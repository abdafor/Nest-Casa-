package it.casa.pannello.camera

import it.casa.pannello.SafeLog
import it.casa.pannello.data.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

enum class PtzDirection(val pan: Float, val tilt: Float) {
    LEFT(-0.5f, 0f), RIGHT(0.5f, 0f), UP(0f, 0.5f), DOWN(0f, -0.5f)
}

sealed interface PtzResult {
    data object Ok : PtzResult
    /** [message] è già in italiano, pronto da mostrare a schermo (senza dati sensibili). */
    data class Fail(val message: String) : PtzResult
}

private class OnvifHttpError(val code: Int) : IOException("HTTP $code")

/**
 * Movimento camera via ONVIF (SOAP su HTTP, porta 2020 per le Tapo).
 * Analogia: ONVIF è come il "telecomando universale" delle telecamere: noi spediamo
 * una lettera ben formattata ("muoviti a sinistra") e la camera obbedisce.
 *
 * Passi: 1) chiediamo l'ora alla camera (per firmare bene la richiesta),
 *        2) chiediamo il "profilo" video, 3) ContinuousMove (parti) e Stop (fermati).
 *
 * NB: sulle Tapo bisogna aver creato l'"Account telecamera" (Tapo app → Impostazioni
 * avanzate → Account camera). ONVIF usa quelle credenziali.
 */
class OnvifPtz(private val host: String, private val creds: Credentials) {
    private val endpoint = "http://${host.trim()}:$PORT/onvif/service"
    private val lock = Mutex()
    private var timeOffsetMs: Long? = null
    private var profileToken: String? = null

    suspend fun move(dir: PtzDirection): PtzResult = execute {
        val token = prepare()
        val body = """<tptz:ContinuousMove xmlns:tptz="$NS_PTZ" xmlns:tt="$NS_SCHEMA">
<tptz:ProfileToken>$token</tptz:ProfileToken>
<tptz:Velocity><tt:PanTilt x="${dir.pan}" y="${dir.tilt}"/></tptz:Velocity>
<tptz:Timeout>PT5S</tptz:Timeout>
</tptz:ContinuousMove>"""
        post(envelope(body, authenticated = true), "$NS_PTZ/ContinuousMove")
    }

    suspend fun stop(): PtzResult = execute {
        val token = prepare()
        val body = """<tptz:Stop xmlns:tptz="$NS_PTZ">
<tptz:ProfileToken>$token</tptz:ProfileToken>
<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom>
</tptz:Stop>"""
        post(envelope(body, authenticated = true), "$NS_PTZ/Stop")
    }

    // ---- interno --------------------------------------------------------------------

    private suspend fun execute(block: () -> Unit): PtzResult = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                block()
                PtzResult.Ok
            } catch (e: OnvifHttpError) {
                SafeLog.w(TAG, "ONVIF HTTP ${e.code}")
                profileToken = null
                if (e.code == 400 || e.code == 401 || e.code == 403) {
                    PtzResult.Fail("Movimento non autorizzato: controlla utente/password e che ONVIF sia attivo")
                } else PtzResult.Fail("Questa camera non accetta il comando di movimento")
            } catch (e: IOException) {
                SafeLog.w(TAG, "ONVIF rete: ${e.javaClass.simpleName}")
                PtzResult.Fail("Camera non raggiungibile per il movimento")
            } catch (e: Exception) {
                SafeLog.w(TAG, "ONVIF errore: ${e.javaClass.simpleName}")
                PtzResult.Fail("Movimento non riuscito")
            }
        }
    }

    /** Ottiene (una volta sola) differenza d'orologio e profilo video. */
    private fun prepare(): String {
        if (timeOffsetMs == null) timeOffsetMs = fetchTimeOffset()
        profileToken?.let { return it }
        val resp = post(
            envelope("""<trt:GetProfiles xmlns:trt="$NS_MEDIA"/>""", authenticated = true),
            "$NS_MEDIA/GetProfiles",
        )
        val token = Regex("""<(?:\w+:)?Profiles[^>]*\btoken="([^"]+)"""").find(resp)?.groupValues?.get(1)
            ?: throw IOException("nessun profilo")
        profileToken = token
        return token
    }

    private fun fetchTimeOffset(): Long = try {
        val resp = post(
            envelope("""<tds:GetSystemDateAndTime xmlns:tds="$NS_DEVICE"/>""", authenticated = false),
            "$NS_DEVICE/GetSystemDateAndTime",
        )
        val m = Regex(
            """UTCDateTime>.*?Hour>(\d+)<.*?Minute>(\d+)<.*?Second>(\d+)<.*?Year>(\d+)<.*?Month>(\d+)<.*?Day>(\d+)<""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(resp)
        if (m == null) 0L else {
            val g = m.groupValues.drop(1).map { it.toInt() }
            val cam = LocalDateTime.of(g[3], g[4], g[5], g[0], g[1], g[2]).toInstant(ZoneOffset.UTC).toEpochMilli()
            cam - System.currentTimeMillis()
        }
    } catch (e: Exception) {
        0L
    }

    private fun envelope(body: String, authenticated: Boolean): String {
        val header = if (authenticated) securityHeader() else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"><s:Header>$header</s:Header><s:Body>$body</s:Body></s:Envelope>"""
    }

    /** WS-Security "UsernameToken" con digest: password mai inviata in chiaro. */
    private fun securityHeader(): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .format(java.time.Instant.ofEpochMilli(System.currentTimeMillis() + (timeOffsetMs ?: 0L)).atZone(ZoneOffset.UTC))
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(nonce)
        sha.update(created.toByteArray(Charsets.UTF_8))
        sha.update(creds.password.toByteArray(Charsets.UTF_8))
        val digest = Base64.getEncoder().encodeToString(sha.digest())
        return """<Security s:mustUnderstand="1" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
<UsernameToken><Username>${xml(creds.user)}</Username>
<Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digest</Password>
<Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${Base64.getEncoder().encodeToString(nonce)}</Nonce>
<Created xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</Created>
</UsernameToken></Security>"""
    }

    private fun post(xml: String, action: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"$action\"")
            conn.outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw OnvifHttpError(code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val TAG = "Onvif"
        const val PORT = 2020
        const val NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl"
        const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
        const val NS_PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
        const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"
    }
}
