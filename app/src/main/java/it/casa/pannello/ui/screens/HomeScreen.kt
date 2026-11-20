package it.casa.pannello.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.camera.LiveVideo
import it.casa.pannello.camera.StreamStatus
import it.casa.pannello.data.Camera
import it.casa.pannello.data.Credentials
import it.casa.pannello.data.Settings
import it.casa.pannello.ui.theme.Corners
import it.casa.pannello.ui.theme.FocusGuard
import it.casa.pannello.ui.theme.FocusStyle
import it.casa.pannello.ui.theme.GearIcon
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassIconButton
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.LocalFocusStyle
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import it.casa.pannello.ui.weather.ScenePalette
import it.casa.pannello.ui.weather.WeatherIcon
import it.casa.pannello.ui.weather.WeatherScene
import it.casa.pannello.ui.weather.palette
import it.casa.pannello.ui.weather.sceneFor
import it.casa.pannello.weather.WeatherUi
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROME: ZoneId = ZoneId.of("Europe/Rome")
private val LiveGreen = Color(0xFF5FC878)

/**
 * Home: scena meteo a schermo intero come sfondo; in alto ora enorme a sinistra e meteo "fuso"
 * (senza riquadro) a destra con i prossimi giorni; in basso le camere, che occupano tutta
 * l'altezza rimasta. Le misure sono scritte per uno schermo da 1920 px di larghezza e scalate
 * (fattore k) su qualunque schermo. Ordine del telecomando: camere → meteo → ingranaggio.
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
    val data = (weather as? WeatherUi.Ok)?.data
    val scene = remember(data?.kind, data?.isDay) { sceneFor(data) }
    val pal = palette(scene)
    val cameras = settings.cameras

    FocusGuard(defaultFocus, Modifier.fillMaxSize()) {
        WeatherScene(scene, Modifier.fillMaxSize())

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = maxWidth.value / 1920f
            fun d(x: Float): Dp = (x * k).dp
            val margin = d(96f)

            CompositionLocalProvider(LocalFocusStyle provides FocusStyle.Home) {
                Column(Modifier.fillMaxSize().padding(horizontal = margin, vertical = d(36f))) {
                    // ---- riga in alto: ora | meteo | ingranaggio ----
                    Box(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Clock(k, pal, Modifier.weight(1f))
                            Box(Modifier.padding(top = d(64f))) {
                                WeatherBlock(
                                    weather, pal, k, onOpenWeather,
                                    focusRequester = if (cameras.isEmpty()) defaultFocus else null,
                                )
                            }
                        }
                        // Ingranaggio tondo in vetro, piccolo, nell'angolo in alto a destra.
                        Box(Modifier.align(Alignment.TopEnd).padding(end = d(30f), top = d(4f))) {
                            GlassIconButton(onOpenSettings, size = d(80f)) { GearIcon(size = d(38f)) }
                            if (updateAvailable) {   // pallino: c'è un aggiornamento (si scarica dal menu)
                                Box(Modifier.align(Alignment.TopEnd).size(d(22f)).clip(CircleShape).background(Sage.Alert))
                            }
                        }
                    }
                    Spacer(Modifier.height(d(20f)))

                    // ---- camere: tutto lo spazio rimasto in altezza ----
                    if (cameras.isEmpty()) {
                        GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(d(44f))) {
                            Txt("Nessuna camera. Aggiungila da ⚙ › Telecamere.", (36f * k).sp, Modifier.padding(d(40f)), ellipsis = false)
                        }
                    } else {
                        CameraArea(
                            cameras, settings.credentials, settings.thumbQuality.high, onThumbTrouble,
                            defaultFocus, onOpenCamera, k, Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Righe e colonne di tessere: 1-2 camere in una riga, 3-4 in 2x2, oltre in 3 colonne. */
@Composable
private fun CameraArea(
    cameras: List<Camera>,
    creds: Credentials,
    high: Boolean,
    onTrouble: () -> Unit,
    defaultFocus: FocusRequester,
    onOpen: (Camera) -> Unit,
    k: Float,
    modifier: Modifier,
) {
    val cols = when {
        cameras.size <= 2 -> cameras.size.coerceAtLeast(1)
        cameras.size <= 4 -> 2
        else -> 3
    }
    val rows = (cameras.size + cols - 1) / cols
    val gap = (40f * k).dp
    // Margine interno: lascia spazio all'alone e all'ingrandimento dell'elemento selezionato.
    Column(modifier.padding((18f * k).dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    if (i < cameras.size) {
                        CameraTile(
                            cameras[i], creds, high, onTrouble, k,
                            if (i == 0) defaultFocus else null,
                            { onOpen(cameras[i]) },
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Tessera di vetro con il video; il nome sta in una pillola di vetro col pallino "in diretta". */
@Composable
private fun CameraTile(
    camera: Camera,
    creds: Credentials,
    high: Boolean,
    onTrouble: () -> Unit,
    k: Float,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var status by remember { mutableStateOf(StreamStatus.CONNECTING) }
    GlassFocusable(onClick, modifier, RoundedCornerShape((48f * k).dp), focusRequester) {
        Box(Modifier.fillMaxSize().padding((10f * k).dp).clip(RoundedCornerShape((40f * k).dp)).background(Color(0xFF1E2A22))) {
            LiveVideo(
                camera, creds, high = high, modifier = Modifier.fillMaxSize(), audio = false,   // miniatura: mai audio
                onTrouble = if (high) onTrouble else null, onStatus = { status = it },
            )
            GlassPanel(Modifier.align(Alignment.BottomStart).padding((24f * k).dp), Corners.Pill) {
                Row(Modifier.padding(horizontal = (28f * k).dp, vertical = (14f * k).dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size((20f * k).dp).clip(CircleShape).background(
                            when (status) {
                                StreamStatus.LIVE -> LiveGreen
                                StreamStatus.OFFLINE, StreamStatus.NO_CREDENTIALS -> Sage.Alert
                                StreamStatus.CONNECTING -> Sage.Light
                            }
                        )
                    )
                    Spacer(Modifier.width((16f * k).dp))
                    Txt(camera.name, (38f * k).sp, weight = FontWeight.SemiBold, maxLines = 1)
                    val note = when (status) {
                        StreamStatus.LIVE -> "in diretta"
                        StreamStatus.OFFLINE -> "non raggiungibile"
                        StreamStatus.CONNECTING -> "connessione…"
                        StreamStatus.NO_CREDENTIALS -> null
                    }
                    if (note != null) {
                        Spacer(Modifier.width((14f * k).dp))
                        Txt(note, (28f * k).sp, color = Sage.InkSoft, maxLines = 1, wrap = false)
                    }
                }
            }
        }
    }
}

/** Ora enorme (peso 200, spaziatura stretta) e data completa sotto. Mai troncata. */
@Composable
private fun Clock(k: Float, pal: ScenePalette, modifier: Modifier) {
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
        Txt(
            time, (236f * k).sp, color = pal.text, weight = FontWeight.W200, maxLines = 1,
            ellipsis = false, wrap = false, letterSpacing = (-8f * k).sp, lineHeight = (250f * k).sp,
        )
        Txt(date, (44f * k).sp, Modifier.padding(start = (10f * k).dp), color = pal.soft, weight = FontWeight.Medium, ellipsis = false)
    }
}

/** Meteo "fuso" con lo sfondo: nessun riquadro (si vede solo l'anello quando è selezionato). */
@Composable
private fun WeatherBlock(
    weather: WeatherUi,
    pal: ScenePalette,
    k: Float,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    PlainFocusable(onClick, focusRequester, k) {
        Column(Modifier.padding(horizontal = (28f * k).dp, vertical = (6f * k).dp), horizontalAlignment = Alignment.End) {
            when (weather) {
                is WeatherUi.Ok -> {
                    val w = weather.data
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(w.icon, (200f * k).dp)
                        Spacer(Modifier.width((20f * k).dp))
                        Txt(
                            "${w.temp}°", (196f * k).sp, color = pal.text, weight = FontWeight.W200, maxLines = 1,
                            ellipsis = false, wrap = false, letterSpacing = (-6f * k).sp, lineHeight = (210f * k).sp,
                        )
                    }
                    Txt("${w.description} · ${w.place}", (40f * k).sp, color = pal.soft, weight = FontWeight.Medium, ellipsis = false)
                    Spacer(Modifier.height((14f * k).dp))
                    // Solo "Prossimi giorni": compatto, 5 colonne (giorno, icona, min/max).
                    Txt("PROSSIMI GIORNI", (22f * k).sp, color = pal.soft, weight = FontWeight.Medium, ellipsis = false, wrap = false, letterSpacing = (3f * k).sp)
                    Spacer(Modifier.height((6f * k).dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((30f * k).dp)) {
                        w.days.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Txt(day.label.substringBefore(' '), (28f * k).sp, color = pal.text, weight = FontWeight.SemiBold, maxLines = 1, ellipsis = false)
                                WeatherIcon(day.icon, (60f * k).dp)
                                Txt("${day.min}° / ${day.max}°", (26f * k).sp, color = pal.soft, maxLines = 1, ellipsis = false, wrap = false)
                            }
                        }
                    }
                }
                WeatherUi.Loading -> Txt("Meteo…", (44f * k).sp, color = pal.soft, ellipsis = false)
                WeatherUi.Error -> Txt("Meteo non disponibile", (40f * k).sp, color = pal.soft, ellipsis = false)
            }
        }
    }
}

/**
 * Elemento selezionabile SENZA riquadro: da fermo è trasparente (leggermente attenuato);
 * quando ha il focus compare l'anello salvia chiaro con alone largo, come le altre tessere.
 */
@Composable
private fun PlainFocusable(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    k: Float,
    content: @Composable () -> Unit,
) {
    val st = FocusStyle.Home
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "focusMeteo")
    val shape = RoundedCornerShape((44f * k).dp)
    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = 1f + (st.scale - 1f) * f; scaleY = 1f + (st.scale - 1f) * f }
            .onFocusChanged { focused = it.isFocused }
            .alpha(0.92f + 0.08f * f)
            .shadow(
                (st.glowElevation.value * f).dp, shape, clip = false,
                ambientColor = st.glowColor.copy(alpha = f), spotColor = st.glowColor.copy(alpha = f),
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f * f))
            .border((st.borderWidth.value * f).dp, st.borderColor.copy(alpha = f), shape)
            .clickable(onClick = onClick)
    ) { content() }
}
