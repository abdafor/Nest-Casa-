package it.casa.pannello.weather

import it.casa.pannello.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherData(
    val place: String,
    val temp: Int,
    val min: Int,
    val max: Int,
    val rainPercent: Int?,
    val emoji: String,
    val description: String,
)

sealed interface WeatherUi {
    data object Loading : WeatherUi
    data object Error : WeatherUi
    data class Ok(val data: WeatherData) : WeatherUi
}

/** Meteo gratuito da Open-Meteo (nessuna chiave API). Si contatta solo per città e previsione. */
object WeatherClient {
    private val places = HashMap<String, Triple<Double, Double, String>>()

    suspend fun fetch(city: String): Result<WeatherData> = withContext(Dispatchers.IO) {
        runCatching {
            val key = city.trim().lowercase()
            val (lat, lon, name) = places.getOrPut(key) {
                val geo = JSONObject(
                    get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city.trim(), "UTF-8")}&count=1&language=it&format=json")
                )
                val r = geo.getJSONArray("results").getJSONObject(0)
                Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
            }
            val j = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&timezone=Europe%2FRome&forecast_days=1"
                )
            )
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val (emoji, text) = describe(cur.getInt("weather_code"))
            WeatherData(
                place = name,
                temp = cur.getDouble("temperature_2m").roundToInt(),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                rainPercent = daily.optJSONArray("precipitation_probability_max")?.let { if (it.isNull(0)) null else it.getInt(0) },
                emoji = emoji,
                description = text,
            )
        }.onFailure { SafeLog.w("Meteo", "Meteo non disponibile: ${it.javaClass.simpleName}") }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Codici meteo WMO → icona + descrizione in italiano. */
    private fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "Sereno"
        1 -> "🌤️" to "Poco nuvoloso"
        2 -> "⛅" to "Parzialmente nuvoloso"
        3 -> "☁️" to "Coperto"
        45, 48 -> "🌫️" to "Nebbia"
        51, 53, 55 -> "🌦️" to "Pioggerella"
        56, 57 -> "🌧️" to "Pioggerella gelata"
        61, 63 -> "🌧️" to "Pioggia"
        65 -> "🌧️" to "Pioggia forte"
        66, 67 -> "🌧️" to "Pioggia gelata"
        71, 73, 75, 77 -> "❄️" to "Neve"
        80, 81 -> "🌦️" to "Rovesci"
        82 -> "⛈️" to "Rovesci forti"
        85, 86 -> "🌨️" to "Rovesci di neve"
        95 -> "⛈️" to "Temporale"
        96, 99 -> "⛈️" to "Temporale con grandine"
        else -> "🌡️" to "Meteo"
    }
}
