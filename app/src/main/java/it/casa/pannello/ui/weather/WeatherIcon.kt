package it.casa.pannello.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import it.casa.pannello.weather.IconKind

private val SunColor = Color(0xFFFFC94D)
private val MoonColor = Color(0xFFF3E2A9)
private val DropColor = Color(0xFF86B6D2)
private val Shade = Color(0xFF1E2D24).copy(alpha = 0.16f)

/** Icone meteo disegnate (sole, luna, nuvole, gocce...). */
@Composable
fun WeatherIcon(kind: IconKind, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (kind) {
            IconKind.SUN -> sun(Offset(s * 0.5f, s * 0.5f), s * 0.27f)
            IconKind.MOON -> moon(Offset(s * 0.5f, s * 0.5f), s * 0.34f)
            IconKind.MOON_CLOUD -> {
                moon(Offset(s * 0.44f, s * 0.36f), s * 0.27f)
                cloud(s * 0.20f, s * 0.38f, s * 0.68f)
            }
            IconKind.SUN_CLOUD -> {
                sun(Offset(s * 0.38f, s * 0.36f), s * 0.17f)
                cloud(s * 0.20f, s * 0.38f, s * 0.68f)
            }
            IconKind.CLOUD -> cloud(s * 0.12f, s * 0.26f, s * 0.76f)
            IconKind.RAIN -> {
                cloud(s * 0.12f, s * 0.14f, s * 0.76f)
                drops(s * 0.12f, s * 0.14f, s * 0.76f)
            }
            IconKind.STORM -> {
                cloud(s * 0.12f, s * 0.12f, s * 0.76f)
                val p = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f); lineTo(s * 0.40f, s * 0.80f); lineTo(s * 0.50f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.97f); lineTo(s * 0.64f, s * 0.72f); lineTo(s * 0.53f, s * 0.72f); close()
                }
                drawPath(p, SunColor)
            }
            IconKind.SNOW -> {
                cloud(s * 0.12f, s * 0.14f, s * 0.76f)
                for (i in 0..2) {
                    val c = Offset(s * (0.30f + 0.20f * i), s * (0.74f + 0.06f * (i % 2)))
                    drawCircle(Shade, s * 0.04f, c + Offset(0f, s * 0.008f))
                    drawCircle(Color.White, s * 0.04f, c)
                }
            }
            IconKind.FOG -> {
                cloud(s * 0.12f, s * 0.10f, s * 0.76f)
                for (i in 0..2) {
                    drawLine(
                        Color.White.copy(alpha = 0.9f), Offset(s * (0.20f + 0.06f * i), s * (0.70f + 0.09f * i)),
                        Offset(s * (0.80f - 0.06f * i), s * (0.70f + 0.09f * i)), strokeWidth = s * 0.05f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(SunColor.copy(alpha = 0.28f), r * 1.55f, c)
    repeat(8) { i ->
        rotate(i * 45f, c) {
            drawLine(SunColor, Offset(c.x, c.y - r * 1.28f), Offset(c.x, c.y - r * 1.68f), strokeWidth = r * 0.17f, cap = StrokeCap.Round)
        }
    }
    drawCircle(SunColor, r, c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val a = Path().apply { addOval(Rect(c, r)) }
    val b = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.25f), r * 0.86f)) }
    drawPath(Path.combine(PathOperation.Difference, a, b), MoonColor)
}

/** Nuvola bianca con una leggera ombra (si legge anche su sfondi chiari). */
private fun DrawScope.cloud(left: Float, top: Float, w: Float) {
    val h = w * 0.62f
    fun body(col: Color, dy: Float) {
        drawRoundRect(col, Offset(left, top + h * 0.45f + dy), Size(w, h * 0.55f), CornerRadius(h * 0.27f))
        drawCircle(col, h * 0.34f, Offset(left + w * 0.30f, top + h * 0.52f + dy))
        drawCircle(col, h * 0.46f, Offset(left + w * 0.56f, top + h * 0.40f + dy))
        drawCircle(col, h * 0.30f, Offset(left + w * 0.79f, top + h * 0.60f + dy))
    }
    body(Shade, h * 0.06f)
    body(Color.White.copy(alpha = 0.98f), 0f)
}

private fun DrawScope.drops(left: Float, top: Float, w: Float) {
    val h = w * 0.62f
    for (i in 0..2) {
        val x = left + w * (0.28f + 0.22f * i)
        val y = top + h * 1.12f + (if (i == 1) h * 0.10f else 0f)
        drawLine(DropColor, Offset(x, y), Offset(x - w * 0.05f, y + h * 0.30f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
    }
}
