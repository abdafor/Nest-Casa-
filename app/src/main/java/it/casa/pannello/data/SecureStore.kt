package it.casa.pannello.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Salvataggio CIFRATO sul dispositivo (Android Keystore + EncryptedSharedPreferences).
 * Utente, password, elenco camere e città stanno solo qui: mai nel codice, mai nel repository.
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = open(context.applicationContext)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Modifica le impostazioni partendo sempre dal valore più recente. */
    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        val cams = JSONArray()
        s.cameras.forEach {
            cams.put(JSONObject().put("id", it.id).put("name", it.name).put("ip", it.ip).put("type", it.type.name))
        }
        prefs.edit()
            .putString(K_USER, s.user)
            .putString(K_PASS, s.password)
            .putString(K_CITY, s.city)
            .putString(K_CAMERAS, cams.toString())
            .apply()
        _settings.value = s
    }

    private fun read(): Settings {
        val cameras = if (prefs.contains(K_CAMERAS)) {
            runCatching {
                val arr = JSONArray(prefs.getString(K_CAMERAS, "[]"))
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Camera(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        ip = o.getString("ip"),
                        type = runCatching { CameraType.valueOf(o.getString("type")) }.getOrDefault(CameraType.ALTRA),
                    )
                }
            }.getOrDefault(Defaults.cameras)
        } else Defaults.cameras
        return Settings(
            cameras = cameras,
            user = prefs.getString(K_USER, "") ?: "",
            password = prefs.getString(K_PASS, "") ?: "",
            city = prefs.getString(K_CITY, Defaults.CITY) ?: Defaults.CITY,
        )
    }

    private companion object {
        const val FILE = "pannello_sicuro"
        const val K_USER = "user"
        const val K_PASS = "password"
        const val K_CITY = "city"
        const val K_CAMERAS = "cameras"

        fun create(ctx: Context): SharedPreferences {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                ctx, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Se la chiave cifrata si è rovinata (succede dopo un ripristino), si riparte pulito invece di andare in crash. */
        fun open(ctx: Context): SharedPreferences = try {
            create(ctx)
        } catch (e: Exception) {
            ctx.deleteSharedPreferences(FILE)
            create(ctx)
        }
    }
}
