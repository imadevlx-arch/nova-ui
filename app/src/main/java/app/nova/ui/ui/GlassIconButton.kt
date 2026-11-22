package app.nova.ui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import app.nova.ui.ui.theme.NovaColors

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp = 32.dp,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val idle = if (active) NovaColors.Jade.copy(alpha = 0.14f) else Color.Transparent
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = idle,
            focusedContainerColor = NovaColors.Jade.copy(alpha = 0.20f),
            pressedContainerColor = NovaColors.Jade.copy(alpha = 0.28f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, NovaColors.Jade), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
