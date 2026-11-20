package it.casa.pannello.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Angoli molto arrotondati ovunque: li cambi qui. */
object Corners {
    val Card = RoundedCornerShape(32.dp)
    val Inner = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * Aspetto dell'elemento selezionato. [Default] = per le schermate chiare (bordo salvia scuro);
 * [Home] = per la home con scene meteo (bordo salvia chiaro #BFD3B0, alone largo, ingrandimento ~2%).
 */
data class FocusStyle(
    val scale: Float,
    val borderWidth: Dp,
    val borderColor: Color,
    val glowColor: Color,
    val glowElevation: Dp,
) {
    companion object {
        val Default = FocusStyle(1.08f, 5.dp, Color(0xFF4B6343), Color(0xFF4B6343), 38.dp)
        val Home = FocusStyle(1.02f, 6.dp, Color(0xFFBFD3B0), Color(0xFFBFD3B0), 80.dp)
    }
}

val LocalFocusStyle = compositionLocalOf { FocusStyle.Default }

/** Vero quando l'elemento che contiene il testo ha il focus del telecomando. */
val LocalFocused = compositionLocalOf { false }

/**
 * Effetto vetro smerigliato bianco: gradiente bianco semi-trasparente, bordo chiaro sottile
 * e ombra morbida. (Compose non sa sfocare ciò che sta DIETRO una card, quindi la sfocatura
 * è simulata dallo sfondo a sfumature + trasparenza.)
 *
 * [focus] va da 0 (non selezionato) a 1 (selezionato). Con focus alto: bordo spesso verde
 * salvia SCURO (5 dp), alone/ombra salvia e sfondo quasi opaco, ben visibili da 3 metri.
 */
fun Modifier.glass(shape: Shape = Corners.Card, focus: Float = 0f, style: FocusStyle = FocusStyle.Default): Modifier = this
    .shadow(
        elevation = (12f + (style.glowElevation.value - 12f) * focus).dp,
        shape = shape,
        clip = false,
        ambientColor = lerp(Sage.Dark.copy(alpha = 0.18f), style.glowColor, focus),
        spotColor = lerp(Sage.Dark.copy(alpha = 0.30f), style.glowColor, focus),
    )
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.78f + 0.20f * focus),
                Color.White.copy(alpha = 0.48f + 0.40f * focus),
            )
        )
    )
    .border(
        width = (1f + (style.borderWidth.value - 1f) * focus).dp,
        brush = if (focus > 0.02f) SolidColor(lerp(Color.White.copy(alpha = 0.9f), style.borderColor, focus))
        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))),
        shape = shape,
    )

/** Card in vetro NON selezionabile (informazioni). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = Corners.Card, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(shape), content = content)
}

/**
 * Card in vetro selezionabile col telecomando. Quando ha il focus: si ingrandisce dell'8%
 * (transizione morbida), bordo spesso salvia scuro + alone, sfondo più opaco, testo più scuro.
 * Quando NON ce l'ha: è leggermente attenuata da un velo chiaro.
 */
@Composable
fun GlassFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Corners.Card,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val style = LocalFocusStyle.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "focus")
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = 1f + (style.scale - 1f) * f; scaleY = 1f + (style.scale - 1f) * f }
            .onFocusChanged { focused = it.isFocused }
            .glass(shape, f, style)
            .veil(1f - f)
            .clickable(onClick = onClick)
    ) {
        CompositionLocalProvider(LocalFocused provides focused) { content(focused) }
    }
}

/** Velo chiaro che attenua gli elementi non selezionati. */
private fun Modifier.veil(amount: Float): Modifier = drawWithContent {
    drawContent()
    if (amount > 0.01f) drawRect(Color.White.copy(alpha = 0.18f * amount))
}

/**
 * Guardia del focus: se per qualsiasi motivo nessun elemento della schermata ha il focus,
 * lo riporta su [default]. Così col telecomando non ci si "perde" mai.
 */
@Composable
fun FocusGuard(default: FocusRequester, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(hasFocus)
    LaunchedEffect(Unit) {
        delay(120)
        while (true) {
            if (!current) runCatching { default.requestFocus() }
            delay(300)
        }
    }
    Box(modifier.onFocusChanged { hasFocus = it.hasFocus }, content = content)
}

/** Testo con valori di default comodi da 3 metri. Con [ellipsis] = false non mette mai i puntini. */
@Composable
fun Txt(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Sage.Ink,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    ellipsis: Boolean = true,
    wrap: Boolean = true,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val focused = LocalFocused.current
    val c = when {
        focused && color == Sage.Ink -> Sage.InkDeep
        focused && color == Sage.InkSoft -> Sage.Ink
        else -> color
    }
    Text(
        text = text, modifier = modifier, color = c, fontSize = size, fontWeight = weight,
        maxLines = maxLines, softWrap = wrap, fontFamily = Inter,
        letterSpacing = letterSpacing, lineHeight = lineHeight,
        overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Visible, textAlign = align,
    )
}

/** Pulsante a pillola in vetro. [primary] = riempito di salvia. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    padding: PaddingValues = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
) {
    GlassFocusable(onClick, modifier, Corners.Pill, focusRequester) { focused ->
        Box(
            Modifier
                .background(if (primary) Sage.Base.copy(alpha = if (focused) 0.80f else 0.55f) else Color.Transparent)
                .padding(padding)
        ) {
            Txt(text, 22.sp, color = Sage.Ink, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
        }
    }
}

/** Pulsante tondo in vetro (es. ingranaggio). */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassFocusable(onClick, modifier.size(size), CircleShape, focusRequester) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/** Icona a ingranaggio disegnata (nessuna immagine esterna). */
@Composable
fun GearIcon(modifier: Modifier = Modifier, color: Color = Sage.Deeper, size: Dp = 36.dp) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f
        val toothW = r * 0.36f
        repeat(8) { i ->
            rotate(i * 45f, pivot = c) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - toothW / 2f, c.y - r),
                    size = Size(toothW, r * 0.46f),
                    cornerRadius = CornerRadius(toothW * 0.3f),
                )
            }
        }
        drawCircle(color, radius = r * 0.76f, center = c)
        drawCircle(Color.White.copy(alpha = 0.92f), radius = r * 0.30f, center = c)
    }
}

/** Campo di testo per il telecomando: premi OK per far apparire la tastiera. */
@Composable
fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    reveal: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    labelSize: TextUnit = 18.sp,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "focusCampo")
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier) {
        Txt(
            label, labelSize, color = if (focused) Sage.Ink else Sage.InkSoft, weight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = 1f + 0.03f * f; scaleY = 1f + 0.03f * f }
                .glass(Corners.Inner, f)
                .veil(1f - f)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Sage.InkDeep, fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = Inter),
                cursorBrush = SolidColor(Sage.Deeper),
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        if (ev.key == Key.DirectionCenter && ev.type == KeyEventType.KeyUp) {
                            keyboard?.show(); true
                        } else false
                    },
            )
        }
    }
}
