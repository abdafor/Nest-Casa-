package it.casa.pannello.ui.weather

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import it.casa.pannello.weather.WeatherData
import it.casa.pannello.weather.WeatherKind
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class SceneBase { NIGHT, SUN, GREY }
enum class Precip { NONE, RAIN, SNOW }

/**
 * Scena meteo della home. Si sceglie dalle condizioni reali (codici Open-Meteo) e da giorno/notte
 * (alba e tramonto): al buio è sempre NOTTE; di giorno sereno = SOLE; coperto, nebbia, pioggia,
 * temporale e neve = grigio-salvia con l'animazione adatta (nuvole, nebbia, pioggia, neve).
 */
data class Scene(val base: SceneBase, val precip: Precip = Precip.NONE, val fog: Boolean = false, val storm: Boolean = false) {
    val dark get() = base == SceneBase.NIGHT
}

fun sceneFor(w: WeatherData?): Scene {
    if (w == null) return Scene(SceneBase.SUN)
    val precip = when (w.kind) {
        WeatherKind.RAIN, WeatherKind.STORM -> Precip.RAIN
        WeatherKind.SNOW -> Precip.SNOW
        else -> Precip.NONE
    }
    val storm = w.kind == WeatherKind.STORM
    val fog = w.kind == WeatherKind.FOG
    return when {
        !w.isDay -> Scene(SceneBase.NIGHT, precip, fog, storm)
        w.kind == WeatherKind.CLEAR -> Scene(SceneBase.SUN)
        else -> Scene(SceneBase.GREY, precip, fog, storm)
    }
}

/** Colori del testo: chiari sulla notte, scuri (#1E2D24) sulle scene chiare. */
class ScenePalette(val text: Color, val soft: Color)

fun palette(scene: Scene) =
    if (scene.dark) ScenePalette(Color(0xFFF2F7F0), Color(0xFFC9D6CD))
    else ScenePalette(Color(0xFF1E2D24), Color(0xFF3F5446))

private class Particle(val x: Float, val y: Float, val p: Float)

/**
 * Sfondo animato a schermo intero, lento e discreto. Tutti i cicli sono interi: nessun "salto".
 *  - "Riduci animazioni" di Android (durata animazioni = 0): scena ferma, senza particelle in moto.
 *  - Se la chiavetta scatta (frame lenti), il numero di particelle si dimezza da solo, più volte.
 */
@Composable
fun WeatherScene(scene: Scene, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    var quality by remember { mutableFloatStateOf(1f) }
    val t: State<Float> = if (reduceMotion) remember { mutableFloatStateOf(0.3f) }
    else rememberInfiniteTransition(label = "scena").animateFloat(
        0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )

    if (!reduceMotion) {
        // Controllo della fluidità: ogni ~120 fotogrammi misura il tempo medio.
        LaunchedEffect(Unit) {
            var last = 0L; var acc = 0L; var n = 0; var bad = 0
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (last != 0L) { acc += now - last; n++ }
                    last = now
                }
                if (n >= 120) {
                    val avgMs = acc / n / 1_000_000.0
                    bad = if (avgMs > 30.0) bad + 1 else 0
                    if (bad >= 2 && quality > 0.2f) { quality *= 0.5f; bad = 0 }
                    acc = 0; n = 0
                }
            }
        }
    }

    val dots = remember { List(60) { Particle(Random(it * 7 + 1).nextFloat(), Random(it * 13 + 5).nextFloat(), Random(it * 3 + 9).nextFloat()) } }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val u = w / 1920f
        val tt = t.value
        val q = quality
        val twoPi = 2f * PI.toFloat()
        val (top, bottom) = when (scene.base) {
            SceneBase.NIGHT -> Color(0xFF18221C) to Color(0xFF34493C)
            SceneBase.SUN -> Color(0xFFF7FAEF) to Color(0xFFC9DCB7)
            SceneBase.GREY -> Color(0xFFC9D4CC) to Color(0xFF93A79A)
        }
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        // Nuvola sfocata (cerchi con gradiente radiale morbido).
        fun softCloud(cx: Float, cy: Float, sc: Float, col: Color) {
            listOf(Triple(-120f, 20f, 150f), Triple(-40f, -30f, 190f), Triple(60f, -10f, 170f), Triple(140f, 25f, 140f)).forEach { (dx, dy, r) ->
                val c = Offset(cx + dx * sc * u, cy + dy * sc * u)
                drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center = c, radius = r * sc * u), radius = r * sc * u, center = c)
            }
        }
        fun drifting(count: Int, y: List<Float>, sc: Float, col: Color) {
            for (i in 0 until count) {
                val x = ((i / count.toFloat() + tt) % 1f) * (w + 900f * u) - 450f * u
                softCloud(x, h * y[i % y.size], sc, col)
            }
        }

        val src = Offset(w * 0.80f, h * 0.17f)               // dove stanno sole/luna
        val pulse = 0.5f + 0.5f * sin(twoPi * tt * 2f)       // "respiro" lento

        when (scene.base) {
            SceneBase.NIGHT -> {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF2D58A).copy(alpha = 0.30f + 0.12f * pulse), Color.Transparent), center = src, radius = (400f + 40f * pulse) * u),
                    radius = (400f + 40f * pulse) * u, center = src,
                )
                for (i in 0 until (28 * q).toInt()) {
                    val d = dots[i]
                    val a = 0.25f + 0.70f * (0.5f + 0.5f * sin(twoPi * (tt * 3f + d.p)))
                    drawCircle(Color.White.copy(alpha = a), (2f + 2.6f * d.p) * u, Offset(d.x * w, d.y * h * 0.8f))
                }
                drifting(2, listOf(0.22f, 0.46f), 1.2f, Color(0xFFB8C9BD).copy(alpha = 0.16f))
            }
            SceneBase.SUN -> {
                val r = (440f + 50f * pulse) * u
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f + 0.15f * pulse), Color.Transparent), center = src, radius = r), radius = r, center = src)
                val rays = max(6, (12 * q).toInt())
                for (i in 0 until rays) {
                    rotate(i * (360f / rays) + tt * 360f, src) {
                        drawLine(
                            Brush.linearGradient(listOf(Color(0xFFFFE9A8).copy(alpha = 0.34f), Color.Transparent), start = src, end = Offset(src.x, src.y - 760f * u)),
                            src, Offset(src.x, src.y - 760f * u), strokeWidth = 30f * u, cap = StrokeCap.Round,
                        )
                    }
                }
                for (i in 0 until (30 * q).toInt()) {   // pulviscolo luminoso che fluttua
                    val d = dots[i]
                    val x = d.x * w + sin(twoPi * (tt * 2f + d.p)) * 34f * u
                    val y = (((d.y - tt * (1 + i % 2)) % 1f) + 1f) % 1f * h
                    drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.35f + 0.4f * d.p), (2f + 3.4f * d.p) * u, Offset(x, y))
                }
            }
            SceneBase.GREY -> {
                val dense = scene.precip != Precip.NONE
                drifting(3, listOf(0.10f, 0.30f, 0.52f), 1.5f, Color(0xFF4F6258).copy(alpha = if (dense) 0.38f else 0.28f))
            }
        }

        if (scene.fog) {
            for (i in 0 until 4) {
                val x = ((i * 0.27f + tt) % 1f) * (w * 1.6f) - w * 0.5f
                drawRoundRect(Color.White.copy(alpha = 0.20f), Offset(x, h * (0.15f + i * 0.2f)), Size(w * 0.7f, 70f * u), CornerRadius(60f * u))
            }
        }
        when (scene.precip) {
            Precip.RAIN -> for (i in 0 until (46 * q).toInt()) {
                val d = dots[i]
                val speed = 3 + (i % 4)                      // velocità diverse, ma intere: ciclo senza salti
                val yy = ((d.y + tt * speed) % 1f) * (h + 80f * u) - 40f * u
                val xx = d.x * w - (yy / h) * 40f * u
                val len = (40f + 50f * d.p) * u
                drawLine(Color.White.copy(alpha = 0.45f + 0.25f * d.p), Offset(xx, yy), Offset(xx - len * 0.18f, yy + len), strokeWidth = (2f + 1.5f * d.p) * u, cap = StrokeCap.Round)
            }
            Precip.SNOW -> for (i in 0 until (46 * q).toInt()) {
                val d = dots[i]
                val yy = ((d.y + tt * (1 + i % 2)) % 1f) * (h + 30f * u) - 15f * u
                val xx = d.x * w + sin(twoPi * (tt * 2f + d.p)) * 24f * u
                drawCircle(Color.White.copy(alpha = 0.85f), (3f + 4f * d.p) * u, Offset(xx, yy))
            }
            Precip.NONE -> Unit
        }
        if (scene.storm) {   // lampo appena percettibile, raro
            val flash = max(0f, sin(twoPi * tt * 5f) - 0.985f) * 40f
            if (flash > 0f) drawRect(Color.White.copy(alpha = flash.coerceAtMost(0.25f)))
        }
    }
}
