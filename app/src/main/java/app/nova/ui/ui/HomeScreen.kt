package app.nova.ui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.nova.ui.HomeViewModel
import app.nova.ui.ui.theme.NovaColors
import app.nova.ui.ui.theme.NovaSpace
import app.nova.ui.ui.theme.NovaType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Direction B « Rail » : rail glass à gauche, horloge géante, grille d'apps sans noms. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLaunch: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val apps = state.favorites + state.others
    val now by rememberMinuteClock()
    val firstApp = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }

    // Sur l'écran d'accueil, Retour ne fait rien.
    BackHandler(enabled = true) {}

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !initialFocusDone) {
            runCatching { firstApp.requestFocus() }
            initialFocusDone = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .novaRailBackground()
    ) {
        Rail(
            onHome = {},
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .padding(start = NovaSpace.SafeY, top = NovaSpace.SafeY, bottom = NovaSpace.SafeY)
                .width(64.dp)
                .fillMaxHeight(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 140.dp, end = NovaSpace.SafeX)
        ) {
            Spacer(Modifier.height(56.dp))
            BasicText(formatTime(now), style = NovaType.BigClock)
            Spacer(Modifier.height(12.dp))
            BasicText(formatDate(now), style = NovaType.SubLine)
            Spacer(Modifier.height(40.dp))
            BasicText("Mes apps", style = NovaType.GridTitle)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        tintIndex = index,
                        onClick = { onLaunch(app.packageName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .then(if (index == 0) Modifier.focusRequester(firstApp) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun Rail(onHome: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .background(NovaColors.GlassFill.copy(alpha = 0.07f), shape)
            .border(1.dp, NovaColors.GlassStroke, shape)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("N", style = NovaType.RailLogo)
        Spacer(Modifier.weight(1f))
        RailButton(Icons.Filled.Home, "Accueil", active = true, onClick = onHome)
        Spacer(Modifier.weight(1f))
        RailButton(Icons.Filled.Settings, "Réglages", active = false, onClick = onOpenSettings)
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    GlassIconButton(onClick = onClick, contentDescription = label, size = 36.dp, active = active) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (active) NovaColors.Jade else NovaColors.TextMuted),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Fond : dégradé saphir → abysse → océan + halo. Statique, aucun coût par frame. */
private fun Modifier.novaRailBackground(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.linearGradient(
            listOf(NovaColors.SapphireDeep, Color(0xFF04122A), Color(0xFF032A3D)),
            start = Offset(0f, 0f), end = Offset(w, h),
        )
    )
    val c = Offset(w * 0.70f, h * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(listOf(NovaColors.Ocean.copy(alpha = 0.35f), Color.Transparent), center = c, radius = w * 0.38f),
        radius = w * 0.38f, center = c,
    )
}

@Composable
private fun rememberMinuteClock() = produceState(initialValue = Date()) {
    while (true) {
        value = Date()
        delay(60_000L - (System.currentTimeMillis() % 60_000L))
    }
}

private fun formatTime(d: Date): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(d)

private fun formatDate(d: Date): String =
    SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(d).replaceFirstChar { it.titlecase(Locale.FRENCH) }
