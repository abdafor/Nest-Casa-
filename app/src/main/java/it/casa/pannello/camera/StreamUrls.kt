package it.casa.pannello.camera

import android.net.Uri
import it.casa.pannello.data.Camera
import it.casa.pannello.data.Credentials

/**
 * Costruisce l'indirizzo RTSP. ATTENZIONE: contiene la password, quindi non va MAI
 * scritto nei log né mostrato a schermo.
 *  - /stream1 = alta qualità (schermo intero)
 *  - /stream2 = bassa qualità (miniature in home)
 */
object StreamUrls {
    fun rtsp(camera: Camera, creds: Credentials, high: Boolean): Uri =
        Uri.Builder()
            .scheme("rtsp")
            .encodedAuthority("${Uri.encode(creds.user)}:${Uri.encode(creds.password)}@${camera.ip.trim()}:554")
            .path(if (high) "/stream1" else "/stream2")
            .build()
}
