package it.casa.pannello.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.casa.pannello.R
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.lightColorScheme

/**
 * Tavolozza "vetro bianco e verde salvia".
 * Per cambiare l'accento di tutta l'app basta ritoccare questi colori.
 */
object Sage {
    val Base = Color(0xFF9CAF88)      // accento principale
    val Light = Color(0xFFC8D5BA)     // variante chiara
    val Pale = Color(0xFFE6EEDD)      // quasi bianco verdino
    val Mist = Color(0xFFF5F9F1)      // sfondo chiarissimo
    val Dark = Color(0xFF6E8660)      // variante scura
    val Deeper = Color(0xFF4B6343)    // verde profondo (icone, cursori)
    val Ink = Color(0xFF243329)       // testo principale (grigio-verde scuro)
    val InkSoft = Color(0xFF475C4D)   // testo secondario, ancora ben leggibile
    val InkDeep = Color(0xFF0F1A13)   // testo sugli elementi selezionati (più scuro)
    val Alert = Color(0xFFB3541E)     // avvisi (camera non raggiungibile)
}

/** Font Inter (licenza OFL, vedi licenze/Inter-OFL.txt), file in res/font. */
val Inter = FontFamily(
    Font(R.font.inter_extralight, FontWeight.W200),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Composable
fun PanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Sage.Base,
            onPrimary = Sage.Ink,
            background = Sage.Mist,
            onBackground = Sage.Ink,
            surface = Color.White,
            onSurface = Sage.Ink,
        ),
        content = content,
    )
}

/** Sfondo chiaro con morbide sfumature salvia: dà "profondità" al vetro che ci sta sopra. */
@Composable
fun PanelBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Sage.Mist, Sage.Pale, Sage.Light)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Sage.Base.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.05f),
                        radius = size.width * 0.45f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Sage.Dark.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.width * 0.5f,
                    )
                )
            }
    ) { content() }
}
