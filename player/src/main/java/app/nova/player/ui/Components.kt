package app.nova.player.ui

import android.graphics.Bitmap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
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

/** Bouton / pastille focusable. Focus = contour jade ; sélection = fond jade discret. */
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
    val shape = RoundedCornerShape(corner)
    val base = when {
        primary -> NovaColors.Jade
        selected -> NovaColors.Jade.copy(alpha = 0.14f)
        else -> NovaColors.GlassFill
    }
    val textColor = if (primary) NovaColors.Abyss else if (selected) NovaColors.Jade else NovaColors.TextPrimary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = base,
            focusedContainerColor = if (primary) NovaColors.Jade else NovaColors.Sapphire.copy(alpha = 0.55f),
            pressedContainerColor = base,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f, pressedScale = NovaMotion.PressScale),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NovaColors.GlassStroke), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NovaColors.Jade), shape = shape),
        ),
    ) {
        BasicText(
            text,
            style = LabelStyle.copy(color = textColor, fontFamily = NovaFonts.Body),
            maxLines = 1,
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
