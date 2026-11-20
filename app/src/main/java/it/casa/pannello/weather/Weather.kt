package it.casa.pannello.weather

import it.casa.pannello.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Tipo di tempo: decide lo sfondo animato della scheda meteo. */
enum class WeatherKind { CLEAR, CLOUDY, FOG, RAIN, STORM, SNOW }

/** Icone meteo disegnate dall'app (nessuna immagine esterna, stesso stile ovunque). */
enum class IconKind { SUN, MOON, MOON_CLOUD, SUN_CLOUD, CLOUD, RAIN, STORM, SNOW, FOG }

data class HourPoint(val time: String, val temp: Int, val emoji: String, val rain: Int?, val icon: IconKind = IconKind.CLOUD)
data class DayPoint(val label: String, val emoji: String, val min: Int, val max: Int, val rain: Int?, val icon: IconKind = IconKind.CLOUD)

data class WeatherData(
    val place: String,
    val temp: Int,
    val feels: Int,
    val min: Int,
    val max: Int,
    val rainPercent: Int?,
    val windKmh: Int,
    val sunrise: String,
    val sunset: String,
    val emoji: String,
    val icon: IconKind,
    val description: String,
    val kind: WeatherKind,
    val isDay: Boolean,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
)

sealed interface WeatherUi {
    data object Loading : WeatherUi
    data object Error : WeatherUi
    data class Ok(val data: WeatherData) : WeatherUi
}

/** Meteo gratuito da Open-Meteo (nessuna chiave API). Si contatta solo per città e previsione. */
object WeatherClient {
    private val places = HashMap<String, Triple<Double, Double, String>>()
    private val rome = ZoneId.of("Europe/Rome")

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
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
                        "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                        "&timezone=Europe%2FRome&forecast_days=6"
                )
            )
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val hourly = j.getJSONObject("hourly")
            val code = cur.getInt("weather_code")
            val isDay = cur.optInt("is_day", 1) == 1

            // Prossime 24 ore, a partire dall'ora corrente (fuso Europe/Rome).
            val times = hourly.getJSONArray("time")
            val now = LocalDateTime.now(rome).truncatedTo(ChronoUnit.HOURS)
            var start = 0
            while (start < times.length() && LocalDateTime.parse(times.getString(start)).isBefore(now)) start++
            val hours = (start until minOf(start + 24, times.length())).map { i ->
                HourPoint(
                    time = if (i == start) "Ora" else times.getString(i).substring(11, 16),
                    temp = hourly.getJSONArray("temperature_2m").getDouble(i).roundToInt(),
                    emoji = icon(hourly.getJSONArray("weather_code").getInt(i), hourly.getJSONArray("is_day").optInt(i, 1) == 1),
                    icon = iconKind(hourly.getJSONArray("weather_code").getInt(i), hourly.getJSONArray("is_day").optInt(i, 1) == 1),
                    rain = hourly.getJSONArray("precipitation_probability").let { if (it.isNull(i)) null else it.getInt(i) },
                )
            }

            // Oggi + prossimi 5 giorni.
            val dTimes = daily.getJSONArray("time")
            val days = (1 until minOf(6, dTimes.length())).map { i ->
                val date = LocalDate.parse(dTimes.getString(i))
                val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)
                    .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                DayPoint(
                    label = "$dow ${date.dayOfMonth}",
                    emoji = icon(daily.getJSONArray("weather_code").getInt(i), true),
                    icon = iconKind(daily.getJSONArray("weather_code").getInt(i), true),
                    min = daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                    max = daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(),
                    rain = daily.getJSONArray("precipitation_probability_max").let { if (it.isNull(i)) null else it.getInt(i) },
                )
            }

            WeatherData(
                place = name,
                temp = cur.getDouble("temperature_2m").roundToInt(),
                feels = cur.getDouble("apparent_temperature").roundToInt(),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                rainPercent = daily.getJSONArray("precipitation_probability_max").let { if (it.isNull(0)) null else it.getInt(0) },
                windKmh = cur.getDouble("wind_speed_10m").roundToInt(),
                sunrise = daily.getJSONArray("sunrise").getString(0).substring(11, 16),
                sunset = daily.getJSONArray("sunset").getString(0).substring(11, 16),
                emoji = icon(code, isDay),
                icon = iconKind(code, isDay),
                description = describe(code),
                kind = kindOf(code),
                isDay = isDay,
                hours = hours,
                days = days,
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

    private fun iconKind(code: Int, isDay: Boolean): IconKind = when (code) {
        0 -> if (isDay) IconKind.SUN else IconKind.MOON
        1 -> if (isDay) IconKind.SUN else IconKind.MOON_CLOUD
        2 -> if (isDay) IconKind.SUN_CLOUD else IconKind.MOON_CLOUD
        3 -> IconKind.CLOUD
        45, 48 -> IconKind.FOG
        in 51..67, 80, 81 -> IconKind.RAIN
        82, 95, 96, 99 -> IconKind.STORM
        in 71..77, 85, 86 -> IconKind.SNOW
        else -> IconKind.CLOUD
    }

    private fun kindOf(code: Int) = when (code) {
        0, 1, 2 -> WeatherKind.CLEAR
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..67, 80, 81, 82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

    /** Icona (emoji) per codice WMO; di notte luna al posto del sole. */
    private fun icon(code: Int, isDay: Boolean): String = when {
        !isDay && (code == 0 || code == 1) -> "🌙"
        !isDay && code == 2 -> "☁️"
        else -> when (code) {
            0 -> "☀️"
            1 -> "🌤️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55, 80, 81 -> "🌦️"
            56, 57, 61, 63, 65, 66, 67 -> "🌧️"
            71, 73, 75, 77 -> "❄️"
            85, 86 -> "🌨️"
            82, 95, 96, 99 -> "⛈️"
            else -> "🌡️"
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Sereno"
        1 -> "Poco nuvoloso"
        2 -> "Parz. nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioggerella"
        56, 57 -> "Pioggia gelata"
        61, 63 -> "Pioggia"
        65 -> "Pioggia forte"
        66, 67 -> "Pioggia gelata"
        71, 73, 75, 77 -> "Neve"
        80, 81 -> "Rovesci"
        82 -> "Rovesci forti"
        85, 86 -> "Rovesci di neve"
        95 -> "Temporale"
        96, 99 -> "Temporale e grandine"
        else -> "Meteo"
    }
}
