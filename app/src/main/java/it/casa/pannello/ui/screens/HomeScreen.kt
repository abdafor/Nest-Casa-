package it.casa.pannello.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
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
import it.casa.pannello.ui.theme.Corners
import it.casa.pannello.ui.theme.GlassButton
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import it.casa.pannello.weather.WeatherUi
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROME: ZoneId = ZoneId.of("Europe/Rome")

/** Home: orologio, meteo, impostazioni e griglia delle camere in diretta. */
@Composable
fun HomeScreen(
    settings: Settings,
    weather: WeatherUi,
    onOpenCamera: (Camera) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val creds = settings.credentials
    val cameras = settings.cameras

    // Margini di sicurezza (overscan): circa 5% ai lati.
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClockBlock(Modifier.weight(1f))
            WeatherCard(weather)
            Spacer(Modifier.width(20.dp))
            GlassButton("⚙  Impostazioni", onOpenSettings)
        }
        Spacer(Modifier.height(18.dp))

        if (!creds.isComplete) {
            GlassFocusable(onClick = onOpenSettings, Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("🔑", 30.sp)
                    Spacer(Modifier.width(16.dp))
                    Txt("Per vedere le camere imposta utente e password (premi OK)", 24.sp, weight = FontWeight.SemiBold)
                }
            }
        }

        if (cameras.isEmpty()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Txt("Nessuna camera. Aggiungila da Impostazioni.", 26.sp, Modifier.padding(32.dp))
            }
        } else {
            // 1 camera = 1 colonna, 2 e 4 = 2 colonne, oltre = 3. Più di 2 righe scorrono col telecomando.
            val columns = when {
                cameras.size <= 1 -> 1
                cameras.size <= 4 -> 2
                else -> 3
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(cameras, key = { _, c -> c.id }) { index, cam ->
                    CameraTile(
                        camera = cam,
                        creds = creds,
                        focusRequester = if (index == 0) firstFocus else null,
                        onClick = { onOpenCamera(cam) },
                    )
                }
            }
        }
    }

    // Parte col focus sulla prima camera (se c'è).
    LaunchedEffect(cameras.isNotEmpty()) {
        delay(250)
        if (cameras.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
}

/** Una miniatura: video a bassa qualità dentro una cornice di vetro. */
@Composable
private fun CameraTile(camera: Camera, creds: Credentials, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var status by remember { mutableStateOf(StreamStatus.CONNECTING) }
    GlassFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        focusRequester = focusRequester,
    ) {
        Box(Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF1E2A22))) {
            LiveVideo(camera, creds, high = false, modifier = Modifier.fillMaxSize(), onStatus = { status = it })
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

/** Ora grande e data in italiano, fuso Europe/Rome. */
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
    Column(modifier) {
        Txt(time, 76.sp, weight = FontWeight.Bold, maxLines = 1)
        Txt(date, 26.sp, color = Sage.InkSoft, weight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun WeatherCard(weather: WeatherUi) {
    GlassPanel(Modifier.height(112.dp), Corners.Card) {
        Row(Modifier.padding(horizontal = 26.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (weather) {
                WeatherUi.Loading -> Txt("Meteo…", 24.sp, color = Sage.InkSoft)
                WeatherUi.Error -> Txt("Meteo non disponibile", 22.sp, color = Sage.InkSoft)
                is WeatherUi.Ok -> {
                    val w = weather.data
                    Txt(w.emoji, 50.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt("${w.temp}°", 44.sp, weight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.width(12.dp))
                            Txt(w.place, 22.sp, color = Sage.InkSoft, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        val rain = w.rainPercent?.let { " · pioggia $it%" } ?: ""
                        Txt("${w.description} · ${w.min}°/${w.max}°$rain", 20.sp, color = Sage.InkSoft, maxLines = 1)
                    }
                }
            }
        }
    }
}
