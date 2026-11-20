package it.casa.pannello.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.camera.LiveVideo
import it.casa.pannello.camera.OnvifPtz
import it.casa.pannello.camera.PtzDirection
import it.casa.pannello.camera.PtzResult
import it.casa.pannello.data.Camera
import it.casa.pannello.data.Credentials
import it.casa.pannello.ui.theme.Corners
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Schermo intero a /stream1.
 * Frecce = muovi la camera (tieni premuto = continua a muoversi, rilascia = si ferma).
 * OK = mostra/nascondi i suggerimenti. Indietro = torna alla home.
 */
@Composable
fun FullscreenScreen(camera: Camera, creds: Credentials, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val ptz = remember(camera.id, camera.ip, creds) { OnvifPtz(camera.ip, creds) }

    var hintTick by remember { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(true) }
    var ptzMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hintTick) { showHint = true; delay(6_000); showHint = false }
    LaunchedEffect(ptzMessage) { if (ptzMessage != null) { delay(4_000); ptzMessage = null } }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // All'uscita: fermiamo sempre la camera, anche se stavi premendo una freccia.
    DisposableEffect(ptz) {
        onDispose {
            if (camera.type.hasPtz) CoroutineScope(Dispatchers.IO).launch { ptz.stop() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val dir = when (ev.key) {
                    Key.DirectionLeft -> PtzDirection.LEFT
                    Key.DirectionRight -> PtzDirection.RIGHT
                    Key.DirectionUp -> PtzDirection.UP
                    Key.DirectionDown -> PtzDirection.DOWN
                    else -> null
                }
                when {
                    dir != null -> {
                        if (camera.type.hasPtz) {
                            if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) {
                                scope.launch { (ptz.move(dir) as? PtzResult.Fail)?.let { ptzMessage = it.message } }
                            } else if (ev.type == KeyEventType.KeyUp) {
                                scope.launch { ptz.stop() }
                            }
                        } else if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) {
                            ptzMessage = "Questa camera non si muove"
                        }
                        true
                    }
                    ev.key == Key.DirectionCenter || ev.key == Key.Enter -> {
                        if (ev.type == KeyEventType.KeyUp) hintTick++
                        true
                    }
                    else -> false
                }
            }
    ) {
        LiveVideo(camera, creds, high = true, fit = true, modifier = Modifier.fillMaxSize())

        // Targhetta in alto a sinistra (dentro i margini di sicurezza).
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            GlassPanel(shape = Corners.Card) {
                Column(Modifier.padding(horizontal = 26.dp, vertical = 14.dp)) {
                    Txt(camera.name, 30.sp, weight = FontWeight.Bold, maxLines = 1)
                    val hint = if (camera.type.hasPtz) "Frecce: muovi la camera  ·  Indietro: torna alla home"
                    else "Indietro: torna alla home"
                    Txt(hint, 20.sp, color = Sage.InkSoft, maxLines = 1)
                }
            }
        }

        AnimatedVisibility(
            visible = ptzMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            GlassPanel(shape = Corners.Pill) {
                Txt(ptzMessage.orEmpty(), 22.sp, color = Sage.Alert, weight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp), maxLines = 2)
            }
        }
    }
}
