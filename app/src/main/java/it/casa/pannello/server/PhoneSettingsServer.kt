package it.casa.pannello.server

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import it.casa.pannello.SafeLog
import it.casa.pannello.data.Camera
import it.casa.pannello.data.CameraType
import it.casa.pannello.data.Settings
import it.casa.pannello.data.isValidHost
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Mini server TEMPORANEO per scrivere le impostazioni dal telefono.
 *
 * - Parte solo quando apri la schermata "Impostazioni dal telefono".
 * - Risponde solo a dispositivi della rete di casa (indirizzi privati) e solo a chi conosce
 *   l'indirizzo segreto (contiene un codice casuale: è anche nel QR).
 * - Si spegne da solo dopo il salvataggio, alla chiusura della schermata o dopo 10 minuti.
 * - Nessuna porta da aprire sul router: resta tutto dentro casa.
 * - Limite noto: dentro casa il collegamento è http (non cifrato), come aprire una pagina
 *   del router. La password viaggia solo sul tuo Wi-Fi e solo per pochi secondi.
 */
class PhoneSettingsServer(
    private val current: () -> Settings,
    private val onSaved: (Settings) -> Unit,
) {
    private val executor = Executors.newCachedThreadPool()
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var serverSocket: ServerSocket? = null
    private var token = ""
    @Volatile private var running = false

    /** Avvia il server e restituisce l'indirizzo da aprire dal telefono (o null se non c'è rete). */
    @Synchronized
    fun start(): String? {
        val ip = localIpv4() ?: return null
        token = ByteArray(9).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val ss = ServerSocket(0, 8)
        serverSocket = ss
        running = true
        executor.execute { acceptLoop(ss) }
        timer.schedule({ stop() }, 10, TimeUnit.MINUTES)
        return "http://$ip:${ss.localPort}/$token"
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor.shutdownNow()
        timer.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val socket = try { ss.accept() } catch (e: Exception) { break }
            runCatching { executor.execute { handle(socket) } }.onFailure { runCatching { socket.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 5000
                val addr = s.inetAddress
                if (!(addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress)) return
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                var length = 0
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        length = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val out = s.getOutputStream()
                if (parts[1].trimEnd('/') != "/$token") {
                    respond(out, 404, page("Non trovato", "<p>Indirizzo non valido.</p>")); return
                }
                when (parts[0]) {
                    "GET" -> respond(out, 200, form())
                    "POST" -> {
                        if (length !in 0..MAX_BODY) { respond(out, 413, page("Troppo grande", "<p>Dati troppo grandi.</p>")); return }
                        val buf = CharArray(length)
                        var read = 0
                        while (read < length) {
                            val n = reader.read(buf, read, length - read)
                            if (n < 0) break
                            read += n
                        }
                        onSaved(merge(parseForm(String(buf, 0, read))))
                        respond(out, 200, page("Salvato ✓", "<p>Fatto! Le impostazioni sono sulla TV. Puoi chiudere questa pagina.</p>"))
                        stop()
                    }
                    else -> respond(out, 405, page("Errore", "<p>Metodo non consentito.</p>"))
                }
            } catch (e: Exception) {
                SafeLog.w("Server", "Richiesta scartata: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    /** Trasforma il modulo in nuove impostazioni (password vuota = si tiene quella attuale). */
    private fun merge(f: Map<String, String>): Settings {
        val old = current()
        val cams = (0 until ROWS).mapNotNull { i ->
            val name = f["n$i"]?.trim().orEmpty().take(40)
            val ip = f["i$i"]?.trim().orEmpty()
            if (name.isBlank() || !isValidHost(ip)) null
            else Camera(UUID.randomUUID().toString(), name, ip, runCatching { CameraType.valueOf(f["t$i"].orEmpty()) }.getOrDefault(CameraType.TAPO_PTZ))
        }
        val pass = f["password"].orEmpty()
        return old.copy(
            cameras = cams,
            user = f["user"]?.trim().orEmpty().ifBlank { old.user },
            password = pass.ifEmpty { old.password },
            city = f["city"]?.trim().orEmpty().ifBlank { old.city },
        )
    }

    private fun form(): String {
        val s = current()
        val rows = (0 until ROWS).joinToString("") { i ->
            val c = s.cameras.getOrNull(i)
            val opts = CameraType.entries.joinToString("") {
                "<option value=\"${it.name}\"${if (c?.type == it) " selected" else ""}>${esc(it.label)}</option>"
            }
            """<div class="card"><label>Nome</label><input name="n$i" value="${esc(c?.name.orEmpty())}">
<label>Indirizzo IP</label><input name="i$i" value="${esc(c?.ip.orEmpty())}" inputmode="decimal">
<label>Tipo</label><select name="t$i">$opts</select></div>"""
        }
        return page(
            "Il mio pannello",
            """<form method="post">
<div class="card"><h2>Account telecamera</h2>
<label>Utente</label><input name="user" value="${esc(s.user)}" autocapitalize="off" autocomplete="off">
<label>Password (lascia vuoto per non cambiarla)</label><input name="password" type="password" autocomplete="new-password">
<h2>Meteo</h2><label>Città</label><input name="city" value="${esc(s.city)}"></div>
<h2>Telecamere</h2>$rows
<button type="submit">Salva sulla TV</button></form>"""
        )
    }

    private fun page(title: String, body: String) = """<!doctype html><html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title>
<style>
body{font-family:system-ui,sans-serif;margin:0;padding:18px;background:linear-gradient(#f5f9f1,#c8d5ba);color:#243329;min-height:100vh}
h1{font-size:24px}h2{font-size:18px;margin:14px 0 6px}
.card{background:rgba(255,255,255,.7);border:1px solid #fff;border-radius:22px;padding:14px 16px;margin:12px 0;box-shadow:0 6px 20px rgba(110,134,96,.25)}
label{display:block;font-size:14px;color:#475c4d;margin:10px 0 4px}
input,select{width:100%;box-sizing:border-box;font-size:18px;padding:12px 14px;border-radius:16px;border:1px solid #9caf88;background:#fff}
button{width:100%;font-size:20px;font-weight:600;padding:16px;border:0;border-radius:999px;background:#9caf88;color:#243329;margin:12px 0 30px}
</style></head><body><h1>Il mio pannello</h1>$body</body></html>"""

    private fun respond(out: java.io.OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val MAX_BODY = 20_000
        const val ROWS = 6
    }
}

/** Indirizzo IP della TV nella rete di casa (es. 192.168.1.20). */
fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/** Disegna il QR code (scuro su bianco, per essere letto bene da ogni telefono). */
fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (m[x, y]) Color.rgb(0x24, 0x33, 0x29) else Color.WHITE)
    }
    return bmp
}
