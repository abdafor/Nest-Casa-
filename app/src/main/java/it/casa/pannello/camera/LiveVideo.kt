@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package it.casa.pannello.camera

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.casa.pannello.R
import it.casa.pannello.SafeLog
import it.casa.pannello.data.Camera
import it.casa.pannello.data.Credentials
import it.casa.pannello.ui.theme.Corners
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import kotlinx.coroutines.delay

enum class StreamStatus { CONNECTING, LIVE, OFFLINE, NO_CREDENTIALS }

private const val TAG = "Video"

/**
 * Video in diretta di una camera.
 *
 * - Il player vive SOLO mentre la schermata è visibile (app in primo piano e questo pezzo
 *   di schermo presente): appena esci o spegni la TV viene rilasciato → consumi bassi.
 * - Se la camera non risponde: stato "non raggiungibile" e nuovi tentativi sempre più
 *   distanziati (2, 4, 8, 15, 30 secondi). Mai crash.
 * - [high] = true usa /stream1 (alta qualità, con audio); false usa /stream2 (miniatura, muta).
 */
@Composable
fun LiveVideo(
    camera: Camera,
    creds: Credentials,
    high: Boolean,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    audio: Boolean = high,
    onTrouble: (() -> Unit)? = null,
    onStatus: (StreamStatus) -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> started = true
                Lifecycle.Event.ON_STOP -> started = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val hasCredentials = creds.isComplete && camera.ip.isNotBlank()
    var status by remember(camera.id, high, hasCredentials) {
        mutableStateOf(if (hasCredentials) StreamStatus.CONNECTING else StreamStatus.NO_CREDENTIALS)
    }
    val currentOnStatus by rememberUpdatedState(onStatus)
    LaunchedEffect(status) { currentOnStatus(status) }

    Box(modifier) {
        if (hasCredentials && started) {
            // key(high): cambiando qualità il player viene ricreato da zero.
            key(high) { StreamPlayer(camera, creds, high, audio, fit, onTrouble) { status = it } }
        }
        when (status) {
            StreamStatus.LIVE -> Unit
            StreamStatus.CONNECTING -> StatusPill("Connessione…", Sage.InkSoft, Modifier.align(Alignment.Center))
            StreamStatus.OFFLINE -> StatusPill("Non raggiungibile · riprovo…", Sage.Alert, Modifier.align(Alignment.Center))
            StreamStatus.NO_CREDENTIALS -> GlassPanel(Modifier.align(Alignment.TopEnd).padding(14.dp), CircleShape) {
                Txt("🔑", 26.sp, Modifier.padding(12.dp), ellipsis = false, wrap = false)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    GlassPanel(modifier, Corners.Pill) {
        Txt(text, 20.sp, color = color, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp), maxLines = 1)
    }
}

@Composable
private fun StreamPlayer(
    camera: Camera,
    creds: Credentials,
    high: Boolean,
    audio: Boolean,
    fit: Boolean,
    onTrouble: (() -> Unit)?,
    onStatus: (StreamStatus) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context, audio = audio) }
    val currentOnTrouble by rememberUpdatedState(onTrouble)
    var everReady by remember { mutableStateOf(false) }          // il flusso ha già funzionato almeno una volta
    val rebuffers = remember { ArrayDeque<Long>() }              // istanti delle interruzioni recenti
    DisposableEffect(player) { onDispose { player.release() } }

    var generation by remember { mutableIntStateOf(0) }   // aumenta a ogni nuovo tentativo
    var failures by remember { mutableIntStateOf(0) }     // errori consecutivi (per i tempi di attesa)
    var buffering by remember { mutableStateOf(false) }
    val currentOnStatus by rememberUpdatedState(onStatus)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_BUFFERING && everReady) {
                    // Video "a scatti": 3 interruzioni in 30 secondi → segnala il problema.
                    val now = android.os.SystemClock.elapsedRealtime()
                    rebuffers.addLast(now)
                    while (rebuffers.isNotEmpty() && now - rebuffers.first() > 30_000) rebuffers.removeFirst()
                    if (rebuffers.size >= 3) currentOnTrouble?.invoke()
                }
                if (playbackState == Player.STATE_READY) {
                    everReady = true
                    failures = 0
                    currentOnStatus(StreamStatus.LIVE)
                }
            }

            override fun onRenderedFirstFrame() {
                currentOnStatus(StreamStatus.LIVE)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Nei log: solo nome camera e tipo d'errore. MAI l'indirizzo.
                SafeLog.w(TAG, "Errore flusso '${camera.name}': ${error.errorCodeName}")
                if (everReady) currentOnTrouble?.invoke()   // era partito bene e poi è caduto
                failures++
                currentOnStatus(StreamStatus.OFFLINE)
                generation++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Guardiano: se resta "in caricamento" per 15 secondi, si riconnette da capo.
    LaunchedEffect(buffering, generation) {
        if (buffering) {
            if (everReady && currentOnTrouble != null) {
                delay(6_000)                      // fermo da 6 secondi: video bloccato
                currentOnTrouble?.invoke()
            }
            delay(15_000)
            failures++
            currentOnStatus(StreamStatus.OFFLINE)
            generation++
        }
    }

    // (Ri)connessione, con attesa progressiva dopo un errore.
    LaunchedEffect(generation) {
        if (generation > 0) {
            val waits = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000)
            delay(waits[(failures - 1).coerceIn(0, waits.size - 1)])
        }
        val uri = StreamUrls.rtsp(camera, creds, high)
        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)      // TCP: più affidabile del UDP sul Wi-Fi di casa
            .setTimeoutMs(8_000)
            .createMediaSource(MediaItem.fromUri(uri))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null, false) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        },
    )
}

/** ExoPlayer tarato per BASSA LATENZA: buffer cortissimi, audio solo a schermo intero. */
private fun buildPlayer(context: android.content.Context, audio: Boolean): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 500,
            /* maxBufferMs = */ 2_000,
            /* bufferForPlaybackMs = */ 250,
            /* bufferForPlaybackAfterRebufferMs = */ 500,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    return ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
        volume = if (audio) 1f else 0f
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !audio)
            .build()
        playWhenReady = true
    }
}
