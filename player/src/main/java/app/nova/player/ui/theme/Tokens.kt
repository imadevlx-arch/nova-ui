package app.nova.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nova.player.R

/** Palette Nova : saphir, océan, pointe de jade néon. Le jade est réservé au focus et aux états actifs. */
object NovaColors {
    val Abyss = Color(0xFF050B1C)
    val Sapphire = Color(0xFF0F52BA)
    val SapphireDeep = Color(0xFF0B2A66)
    val Ocean = Color(0xFF0077BE)
    val OceanDeep = Color(0xFF0A3D62)
    val Jade = Color(0xFF00F5A0)
    val TextPrimary = Color(0xFFEAF2FF)
    val TextMuted = Color(0xFF9FB3D1)
    val GlassFill = Color(0x0FFFFFFF)   // 6 %
    val GlassStroke = Color(0x1FFFFFFF) // 12 %

    /** Teintes de repli pour les apps sans bannière. */
    val TileTints = listOf(
        Sapphire to SapphireDeep,
        Ocean to OceanDeep,
        Color(0xFF123A8C) to Color(0xFF071A44),
        Color(0xFF0B5E86) to Color(0xFF062C4A),
        Color(0xFF1C4FA8) to Color(0xFF0A2350),
        Color(0xFF08708F) to Color(0xFF04324A),
    )
}

object NovaFonts {
    val Display = FontFamily(
        Font(R.font.unbounded_medium, FontWeight.Medium),
        Font(R.font.unbounded_bold, FontWeight.Bold),
    )
    val Body = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
    )
}

/** Échelle typo TV (1080p = 960×540 dp). Titres larges, lisibles à 3 m. */
object NovaType {
    val BigClock = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Bold, fontSize = 110.sp, lineHeight = 100.sp, letterSpacing = (-0.04).em, color = NovaColors.TextPrimary)
    val SubLine = TextStyle(fontFamily = NovaFonts.Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = NovaColors.TextMuted)
    val GridTitle = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = NovaColors.TextPrimary)
    val RailLogo = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NovaColors.Jade)
    val Hero = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Bold, fontSize = 84.sp, letterSpacing = (-0.03).em, color = NovaColors.TextPrimary)
    val Clock = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = NovaColors.TextPrimary)
    val Wordmark = TextStyle(fontFamily = NovaFonts.Display, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.18.em, color = NovaColors.TextPrimary)
    val Date = TextStyle(fontFamily = NovaFonts.Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.04.em, color = NovaColors.TextMuted)
    val Section = TextStyle(fontFamily = NovaFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.08.em, color = NovaColors.TextMuted)
    val TileFallback = TextStyle(fontFamily = NovaFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = NovaColors.TextPrimary)
}

object NovaSpace {
    val SafeX = 48.dp
    val SafeY = 27.dp
    val ContentX = 64.dp
    val TileGap = 18.dp
    val SmallTileGap = 14.dp
}

object NovaMotion {
    const val FocusScale = 1.10f
    const val PressScale = 0.96f
}
