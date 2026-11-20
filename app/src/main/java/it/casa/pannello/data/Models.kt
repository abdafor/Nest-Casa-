package it.casa.pannello.data

/** Tipo di camera: decide se mostrare le frecce per muoverla. */
enum class CameraType(val label: String, val hasPtz: Boolean) {
    TAPO_PTZ("Tapo con movimento (PTZ)", true),
    TAPO_FISSA("Tapo fissa", false),
    ALTRA("Altra camera RTSP", false);

    fun next(): CameraType = entries[(ordinal + 1) % entries.size]
}

data class Camera(
    val id: String,
    val name: String,
    val ip: String,
    val type: CameraType,
)

/** Account telecamera Tapo (uguale su tutte le camere). */
data class Credentials(val user: String, val password: String) {
    val isComplete get() = user.isNotBlank() && password.isNotBlank()
}

/** Qualità delle miniature in home (uguale per tutte le camere). */
enum class ThumbQuality(val label: String, val description: String, val high: Boolean) {
    ECO("Risparmio", "Flusso leggero (stream2): consumi bassi, consigliato con 2 o più camere", false),
    HIGH("Alta", "Flusso nitido (stream1): più dettaglio ma più Wi-Fi e più lavoro per la TV", true),
}

data class Settings(
    val cameras: List<Camera>,
    val user: String,
    val password: String,
    val city: String,
    val thumbQuality: ThumbQuality = ThumbQuality.ECO,
) {
    val credentials get() = Credentials(user, password)
}

/** Valori iniziali alla prima apertura (utente e password restano VUOTI: li metti tu). */
object Defaults {
    const val CITY = "Roma"
    val cameras = listOf(
        Camera("giardino", "Giardino", "192.168.1.3", CameraType.TAPO_PTZ),
        Camera("bambini", "Camera dei bambini", "192.168.1.8", CameraType.TAPO_PTZ),
    )
}

/** Un indirizzo IP o nome host semplice (solo lettere, numeri, punti e trattini). */
fun isValidHost(s: String) = Regex("^[A-Za-z0-9.-]{1,253}$").matches(s.trim())
