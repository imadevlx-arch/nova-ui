package app.nova.ui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import app.nova.ui.data.TvApp
import app.nova.ui.ui.theme.NovaColors
import app.nova.ui.ui.theme.NovaMotion
import app.nova.ui.ui.theme.NovaType

/**
 * Tuile d'app : visuel seul, nom masqué (lu par l'accessibilité).
 * Focus = agrandissement + contour jade + halo.
 */
@Composable
fun AppTile(
    app: TvApp,
    tintIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val (tintA, tintB) = NovaColors.TileTints[Math.floorMod(tintIndex, NovaColors.TileTints.size)]
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = app.label },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = tintB,
            focusedContainerColor = tintB,
            pressedContainerColor = tintB,
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = NovaMotion.FocusScale,
            pressedScale = NovaMotion.PressScale,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NovaColors.GlassStroke), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NovaColors.Jade), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = NovaColors.Jade.copy(alpha = 0.45f), elevation = 16.dp),
        ),
    ) {
        TileContent(app, tintA, tintB)
    }
}

@Composable
private fun BoxScope.TileContent(app: TvApp, tintA: Color, tintB: Color) {
    val banner = app.banner
    val icon = app.icon
    when {
        banner != null -> Image(
            bitmap = banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        icon != null -> Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tintA, tintB))),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        else -> Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tintA, tintB))).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(app.label, style = NovaType.TileFallback.copy(textAlign = TextAlign.Center))
        }
    }
}
