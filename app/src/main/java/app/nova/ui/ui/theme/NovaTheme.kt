package app.nova.ui.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NovaColors.Jade,
            onPrimary = NovaColors.Abyss,
            secondary = NovaColors.Ocean,
            background = NovaColors.Abyss,
            surface = NovaColors.SapphireDeep,
            onSurface = NovaColors.TextPrimary,
            onBackground = NovaColors.TextPrimary,
        ),
        content = content,
    )
}
