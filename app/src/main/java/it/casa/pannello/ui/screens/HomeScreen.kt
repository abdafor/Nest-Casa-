package it.casa.pannello.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.camera.LiveVideo
import it.casa.pannello.camera.StreamStatus
import it.casa.pannello.data.Camera
import it.casa.pannello.data.Credentials
import it.casa.pannello.data.Settings
import it.casa.pannello.ui.theme.FocusGuard
import it.casa.pannello.ui.theme.GearIcon
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassIconButton
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import it.casa.pannello.ui.weather.WeatherCard
import it.casa.pannello.weather.WeatherUi
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROME: ZoneId = ZoneId.of("Europe/Rome")

/**
 * Home: orologio, scheda meteo (OK = schermo intero), ingranaggio in alto a destra
 * e griglia delle camere in diretta. Ordine del telecomando: camere → meteo → ingranaggio.
 */
@Composable
fun HomeScreen(
    settings: Settings,
    weather: WeatherUi,
    onOpenCamera: (Camera) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeather: () -> Unit,
    onThumbTrouble: () -> Unit,
    updateAvailable: Boolean = false,
) {
    val defaultFocus = remember { FocusRequester() }
    val creds = settings.credentials
    val cameras = settings.cameras

    FocusGuard(defaultFocus, Modifier.fillMaxSize()) {
        // Margini di sicurezza (overscan): circa 5% ai lati.
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ClockBlock(Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                WeatherCard(
                    weather, onOpenWeather, Modifier.weight(1.5f),
                    focusRequester = if (cameras.isEmpty()) defaultFocus else null,
                )
                Spacer(Modifier.width(20.dp))
                Box {
                    GlassIconButton(onOpenSettings) { GearIcon() }
                    // Pallino: c'è un aggiornamento (si scarica dal menu).
                    if (updateAvailable) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Sage.Alert))
                }
            }
            Spacer(Modifier.height(14.dp))

            if (cameras.isEmpty()) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Txt("Nessuna camera. Aggiungila da ⚙ › Telecamere.", 26.sp, Modifier.padding(32.dp), ellipsis = false)
                }
            } else {
                // 1 camera = 1 colonna, 2-4 = 2 colonne, oltre = 3. Oltre 2 righe si scorre col telecomando.
                val columns = when {
                    cameras.size <= 1 -> 1
                    cameras.size <= 4 -> 2
                    else -> 3
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(22.dp),   // spazio per l'ingrandimento del focus
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(cameras, key = { _, c -> c.id }) { index, cam ->
                        CameraTile(
                            camera = cam,
                            creds = creds,
                            high = settings.thumbQuality.high,
                            onTrouble = onThumbTrouble,
                            focusRequester = if (index == 0) defaultFocus else null,
                            onClick = { onOpenCamera(cam) },
                        )
                    }
                }
            }
        }
    }
}

/** Una miniatura: video a bassa qualità dentro una cornice di vetro. Senza credenziali: icona a chiave. */
@Composable
private fun CameraTile(
    camera: Camera,
    creds: Credentials,
    high: Boolean,
    onTrouble: () -> Unit,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var status by remember { mutableStateOf(StreamStatus.CONNECTING) }
    GlassFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        focusRequester = focusRequester,
    ) {
        Box(Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF1E2A22))) {
            // Miniatura: mai audio. Con qualità Alta, se il video scatta si torna a Risparmio (vedi MainActivity).
            LiveVideo(
                camera, creds, high = high, modifier = Modifier.fillMaxSize(), audio = false,
                onTrouble = if (high) onTrouble else null, onStatus = { status = it },
            )
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC1E2A22))))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            when (status) {
                                StreamStatus.LIVE -> Sage.Base
                                StreamStatus.OFFLINE, StreamStatus.NO_CREDENTIALS -> Sage.Alert
                                StreamStatus.CONNECTING -> Color.White.copy(alpha = 0.6f)
                            }
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Txt(camera.name, 26.sp, color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Ora grande e data completa in italiano, fuso Europe/Rome.
 * Le dimensioni si adattano alla larghezza disponibile e il testo non viene mai tagliato.
 */
@Composable
private fun ClockBlock(modifier: Modifier = Modifier) {
    val now by produceState(ZonedDateTime.now(ROME)) {
        while (true) {
            value = ZonedDateTime.now(ROME)
            delay(1000L - (System.currentTimeMillis() % 1000L))   // si aggiorna allo scoccare del secondo
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN))
        .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    BoxWithConstraints(modifier) {
        val w = maxWidth.value
        val timeSize = (w / 3.4f).coerceIn(48f, 90f).sp
        val dateSize = (w / 15f).coerceIn(17f, 30f).sp
        Column {
            Txt(time, timeSize, weight = FontWeight.Bold, maxLines = 1, ellipsis = false, wrap = false)
            Txt(date, dateSize, color = Sage.InkSoft, weight = FontWeight.Medium, ellipsis = false)
        }
    }
}
