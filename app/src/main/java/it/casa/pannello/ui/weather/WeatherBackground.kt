package it.casa.pannello.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import it.casa.pannello.weather.WeatherKind
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sfondo animato della scheda meteo: cambia con il tempo (sereno, nuvoloso, nebbia, pioggia,
 * temporale, neve) e con giorno/notte. Animazione lenta e discreta; i colori sono chiari
 * così il testo scuro resta leggibile. Tutti i cicli sono interi: il loop non ha "salti".
 */
@Composable
fun WeatherBackground(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberInfiniteTransition(label = "meteo")
    val t = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "t")

    // Posizioni casuali ma fisse (stesso seme = stesso disegno).
    val dots = remember { List(46) { Triple(Random(it * 7 + 1).nextFloat(), Random(it * 13 + 5).nextFloat(), Random(it * 3 + 9).nextFloat()) } }

    val night = !isDay
    val (top0, bottom0) = when (kind) {
        WeatherKind.CLEAR -> if (night) Color(0xFFB4C0D4) to Color(0xFFD0DBD6) else Color(0xFFFFF1C4) to Color(0xFFDDEACB)
        WeatherKind.CLOUDY -> Color(0xFFD3DBD3) to Color(0xFFE8EDE5)
        WeatherKind.FOG -> Color(0xFFE2E7E0) to Color(0xFFEEF1EB)
        WeatherKind.RAIN -> Color(0xFFB9C8C0) to Color(0xFFD4DED7)
        WeatherKind.STORM -> Color(0xFFA6B5B0) to Color(0xFFC3CFCA)
        WeatherKind.SNOW -> Color(0xFFE3EDEF) to Color(0xFFF2F7F6)
    }
    // Di notte (tranne il sereno, già scuro) i colori si raffreddano un po'.
    val cool = if (night && kind != WeatherKind.CLEAR) 0.30f else 0f
    val top = lerp(top0, Color(0xFF8E9EB2), cool)
    val bottom = lerp(bottom0, Color(0xFF8E9EB2), cool)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val u = density
        val tt = t.value
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        fun cloud(cx: Float, cy: Float, s: Float, a: Float) {
            val col = Color.White.copy(alpha = a)
            drawCircle(col, 22f * s * u, Offset(cx, cy))
            drawCircle(col, 30f * s * u, Offset(cx + 26f * s * u, cy - 10f * s * u))
            drawCircle(col, 22f * s * u, Offset(cx + 54f * s * u, cy))
            drawCircle(col, 18f * s * u, Offset(cx + 28f * s * u, cy + 8f * s * u))
        }
        fun drifting(count: Int, alpha: Float) {
            for (i in 0 until count) {
                val x = ((i / count.toFloat() + tt) % 1f) * (w + 160f * u) - 100f * u
                cloud(x, h * (0.22f + 0.28f * ((i * 37) % 10) / 10f), 0.9f + 0.4f * (i % 2), alpha)
            }
        }

        when (kind) {
            WeatherKind.CLEAR -> if (night) {
                // Luna con alone e stelle che brillano piano.
                drawCircle(Color.White.copy(alpha = 0.30f), 70f * u, Offset(w * 0.86f, h * 0.28f))
                drawCircle(Color.White.copy(alpha = 0.85f), 20f * u, Offset(w * 0.86f, h * 0.28f))
                dots.forEachIndexed { i, (x, y, p) ->
                    val a = 0.35f + 0.45f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (tt * 3f + p)))
                    drawCircle(Color.White.copy(alpha = a), (1.2f + 1.6f * p) * u, Offset(x * w, y * h * 0.85f))
                }
            } else {
                // Sole con alone che "respira".
                val pulse = 0.5f + 0.5f * sin(2f * PI.toFloat() * tt * 2f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFE08A).copy(alpha = 0.55f + 0.2f * pulse), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.22f), radius = (90f + 14f * pulse) * u,
                    ),
                    radius = (90f + 14f * pulse) * u, center = Offset(w * 0.85f, h * 0.22f),
                )
                drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.9f), 24f * u, Offset(w * 0.85f, h * 0.22f))
            }
            WeatherKind.CLOUDY -> drifting(4, 0.6f)
            WeatherKind.FOG -> for (i in 0 until 4) {
                val x = ((i * 0.27f + tt) % 1f) * (w * 1.6f) - w * 0.5f
                drawRoundRect(
                    Color.White.copy(alpha = 0.40f), Offset(x, h * (0.15f + i * 0.2f)),
                    androidx.compose.ui.geometry.Size(w * 0.7f, 26f * u), androidx.compose.ui.geometry.CornerRadius(30f * u),
                )
            }
            WeatherKind.RAIN, WeatherKind.STORM -> {
                drifting(3, if (kind == WeatherKind.STORM) 0.55f else 0.5f)
                dots.forEachIndexed { i, (x, y, p) ->
                    val speed = 3 + (i % 3)               // numeri interi: ciclo senza salti
                    val yy = ((y + tt * speed) % 1f) * (h + 20f * u) - 10f * u
                    val xx = x * w - (yy / h) * 14f * u
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(xx, yy), Offset(xx - 4f * u, yy + 14f * u), strokeWidth = 1.6f * u)
                }
            }
            WeatherKind.SNOW -> dots.forEachIndexed { i, (x, y, p) ->
                val speed = 1 + (i % 2)
                val yy = ((y + tt * speed) % 1f) * (h + 12f * u) - 6f * u
                val xx = x * w + sin(2f * PI.toFloat() * (tt * 2f + p)) * 8f * u
                drawCircle(Color.White.copy(alpha = 0.85f), (2f + 2.2f * p) * u, Offset(xx, yy))
            }
        }
    }
}
