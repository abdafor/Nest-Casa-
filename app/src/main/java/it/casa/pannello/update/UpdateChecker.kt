package it.casa.pannello.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import it.casa.pannello.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Una Release più nuova di quella installata. [name] es. "1.1.9". */
data class UpdateInfo(val code: Long, val name: String, val url: String, val sha256: String?, val size: Long)

enum class InstallResult { STARTED, NEEDS_PERMISSION, FAILED }

/**
 * Controllo aggiornamenti. Legge SOLO l'ultima Release pubblica del repository GitHub
 * (nessun login, nessun dato personale: GitHub vede soltanto l'indirizzo IP di casa, come
 * per qualunque pagina web). Al massimo una volta al giorno.
 *
 * Il numero di versione è l'ultimo numero del nome della Release (v1.1.9 → 9) e coincide con
 * il "versionCode" dell'app, che cresce a ogni build.
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/abdafor/nest-casa-/releases/latest"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val TAG = "Aggiornamenti"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("aggiornamenti", Context.MODE_PRIVATE)

    fun currentCode(ctx: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    }.getOrDefault(0L)

    /** Aggiornamento già trovato in precedenza (se ancora più nuovo di quello installato). */
    fun cached(ctx: Context): UpdateInfo? {
        val p = prefs(ctx)
        val code = p.getLong("code", 0L)
        val url = p.getString("url", null) ?: return null
        if (code <= currentCode(ctx)) return null
        return UpdateInfo(code, p.getString("name", "") ?: "", url, p.getString("sha", null), p.getLong("size", 0L))
    }

    /** All'apertura dell'app: contatta GitHub solo se è passato almeno un giorno dall'ultimo controllo. */
    suspend fun checkIfDue(ctx: Context): UpdateInfo? {
        val last = prefs(ctx).getLong("last_check", 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return cached(ctx)
        return checkNow(ctx).getOrElse { cached(ctx) }
    }

    /** Controllo immediato (anche dal pulsante in Info versione). */
    suspend fun checkNow(ctx: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(API).openConnection() as HttpURLConnection
            val body = try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "pannello-aggiornamenti")   // generico: nessun dato personale
                c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }

            val j = JSONObject(body)
            val tag = j.getString("tag_name")
            val code = Regex("(\\d+)$").find(tag)?.value?.toLongOrNull() ?: throw IOException("versione illeggibile")
            val assets = j.getJSONArray("assets")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { asset = a; break }
            }
            val e = prefs(ctx).edit().putLong("last_check", System.currentTimeMillis())
            val info = if (asset != null && code > currentCode(ctx)) {
                UpdateInfo(
                    code = code,
                    name = tag.removePrefix("v"),
                    url = asset.getString("browser_download_url"),
                    sha256 = asset.optString("digest", "").removePrefix("sha256:").ifBlank { null },
                    size = asset.optLong("size", 0L),
                )
            } else null
            if (info != null) {
                e.putLong("code", info.code).putString("name", info.name).putString("url", info.url)
                    .putString("sha", info.sha256).putLong("size", info.size)
            } else e.remove("code").remove("url").remove("name").remove("sha").remove("size")
            e.apply()
            info
        }.onFailure { SafeLog.w(TAG, "Controllo non riuscito: ${it.javaClass.simpleName}") }
    }

    /** Scarica l'APK nella cache privata dell'app e ne verifica l'impronta SHA-256 (se GitHub la fornisce). */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "il-mio-pannello.apk")
        val c = URL(info.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "pannello-aggiornamenti")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            val expected = info.sha256
            if (expected != null && !md.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)) {
                out.delete()
                throw IOException("impronta non valida")
            }
            out
        } finally { c.disconnect() }
    }

    /** Apre l'installazione di Android. Serve il permesso "Installa app sconosciute" per questa app. */
    fun install(ctx: Context, file: File): InstallResult {
        return try {
            if (!ctx.packageManager.canRequestPackageInstalls()) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return InstallResult.NEEDS_PERMISSION
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            InstallResult.STARTED
        } catch (e: Exception) {
            SafeLog.w(TAG, "Installazione non avviata: ${e.javaClass.simpleName}")
            InstallResult.FAILED
        }
    }
}
