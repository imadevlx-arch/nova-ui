package app.nova.player.ui

import android.graphics.Bitmap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaFonts
import app.nova.player.ui.theme.NovaMotion
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

val BodyStyle = TextStyle(fontFamily = NovaFonts.Body, fontSize = 15.sp, color = NovaColors.TextPrimary)
val MutedStyle = BodyStyle.copy(color = NovaColors.TextMuted, fontSize = 13.sp)
val TitleStyle = TextStyle(fontFamily = NovaFonts.Display, fontSize = 34.sp, color = NovaColors.TextPrimary)
val LabelStyle = TextStyle(fontFamily = NovaFonts.Body, fontSize = 14.sp, color = NovaColors.TextPrimary)

/**
 * Surface cliquable universelle : télécommande (focus + OK) ET tactile (tap).
 * Les Surface de androidx.tv ne gèrent pas le toucher, d'où ce composant maison.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = NovaColors.GlassFill,
    focusedColor: Color = NovaColors.Sapphire.copy(alpha = 0.55f),
    focusedScale: Float = 1.04f,
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when { pressed -> NovaMotion.PressScale; focused -> focusedScale; else -> 1f },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f), label = "scale",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (glow && focused) Modifier.shadow(18.dp, shape, ambientColor = NovaColors.Jade, spotColor = NovaColors.Jade) else Modifier)
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .border(if (focused) 2.dp else 1.dp, if (focused) NovaColors.Jade else NovaColors.GlassStroke, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

/** Bouton / pastille. Focus = contour jade ; sélection = fond jade discret. */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    corner: Dp = 14.dp,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
) {
    val base = when {
        primary -> NovaColors.Jade
        selected -> NovaColors.Jade.copy(alpha = 0.14f)
        else -> NovaColors.GlassFill
    }
    val textColor = if (primary) NovaColors.Abyss else if (selected) NovaColors.Jade else NovaColors.TextPrimary
    Pressable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = base,
        focusedColor = if (primary) NovaColors.Jade else NovaColors.Sapphire.copy(alpha = 0.55f),
    ) {
        BasicText(
            text,
            style = LabelStyle.copy(color = textColor, fontFamily = NovaFonts.Body),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Champ texte pilotable à la télécommande : OK ouvre le clavier, haut/bas changent de champ.
 */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    textStyle: TextStyle = BodyStyle.copy(fontSize = 17.sp),
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    Box(modifier) {
        BasicText(label, style = MutedStyle, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(NovaColors.Jade),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrect = false),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { keyboard?.hide(); onDone() },
                onGo = { keyboard?.hide(); onDone() },
            ),
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            keyboard?.show(); true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> focus.moveFocus(FocusDirection.Down)
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> focus.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                }
                .background(if (focused) NovaColors.Sapphire.copy(alpha = 0.35f) else NovaColors.GlassFill, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) NovaColors.Jade else NovaColors.GlassStroke, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .background(Color(0x33FF5A6E), shape)
            .border(1.dp, Color(0x66FF5A6E), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { BasicText(text, style = BodyStyle.copy(fontSize = 14.sp)) }
}

/** QR code sombre sur fond blanc (meilleure lecture par les téléphones). */
fun qrBitmap(content: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF050B1C.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
