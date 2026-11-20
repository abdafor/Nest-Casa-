package it.casa.pannello.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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

/** Angoli molto arrotondati ovunque: li cambi qui. */
object Corners {
    val Card = RoundedCornerShape(32.dp)
    val Inner = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * Effetto vetro smerigliato bianco: gradiente bianco semi-trasparente, bordo chiaro sottile
 * e ombra morbida. (Compose non sa sfocare ciò che sta DIETRO una card, quindi la sfocatura
 * è simulata dallo sfondo a sfumature + trasparenza.)
 * Con [focused] = true: bordo e alone salvia ben visibili.
 */
fun Modifier.glass(shape: Shape = Corners.Card, focused: Boolean = false): Modifier = this
    .shadow(
        elevation = if (focused) 30.dp else 14.dp,
        shape = shape,
        clip = false,
        ambientColor = if (focused) Sage.Base else Sage.Dark.copy(alpha = 0.20f),
        spotColor = if (focused) Sage.Base else Sage.Dark.copy(alpha = 0.32f),
    )
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.50f))))
    .border(
        width = if (focused) 4.dp else 1.dp,
        brush = if (focused) SolidColor(Sage.Base)
        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))),
        shape = shape,
    )

/** Card in vetro NON selezionabile (informazioni). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = Corners.Card, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(shape), content = content)
}

/**
 * Card in vetro selezionabile col telecomando: quando ha il focus si ingrandisce un po'
 * e si accende di salvia.
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
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(220), label = "scala")
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .glass(shape, focused)
            .clickable(onClick = onClick)
    ) { content(focused) }
}

/** Testo con valori di default comodi da 3 metri. */
@Composable
fun Txt(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Sage.Ink,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
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
                .background(if (primary) Sage.Base.copy(alpha = if (focused) 0.85f else 0.60f) else Color.Transparent)
                .padding(padding)
        ) {
            Txt(text, 22.sp, color = Sage.Ink, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
        }
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
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ring by animateColorAsState(if (focused) Sage.Base else Color.Transparent, tween(200), label = "anello")
    Column(modifier) {
        Txt(label, labelSize, color = Sage.InkSoft, weight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .glass(Corners.Inner, focused)
                .border(if (focused) 0.dp else 0.dp, ring, Corners.Inner)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Sage.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Sage.Deeper),
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                modifier = Modifier
                    .fillMaxWidth()
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
