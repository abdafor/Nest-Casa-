package it.casa.pannello

import android.util.Log

/**
 * Log "sicuro": qualunque URL viene oscurato e non si registrano mai password.
 * Regola della casa: nei log si scrive solo il NOME della camera e il tipo di errore,
 * mai l'indirizzo completo (rtsp://utente:password@ip...).
 */
object SafeLog {
    private val urlRegex = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")

    private fun clean(msg: String) = urlRegex.replace(msg, "<url oscurato>")

    fun d(tag: String, msg: String) { Log.d(tag, clean(msg)) }
    fun w(tag: String, msg: String) { Log.w(tag, clean(msg)) }
}
