package app.biddrive.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.biddrive.android.R

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** One set of semantic colours per theme. Screens read them through [Bd], which resolves against the active palette. */
data class Palette(
    val dark: Boolean,
    val background: Color, val surfaceLowest: Color, val surfaceLow: Color, val surface: Color, val surfaceHigh: Color, val surfaceHighest: Color,
    val onSurface: Color, val onSurfaceVariant: Color, val outline: Color, val outlineVariant: Color,
    val amber: Color, val amberSoft: Color, val onAmber: Color,
    val green: Color, val greenContainer: Color, val red: Color, val redContainer: Color, val live: Color,
)

// Dark palette: taken from the Stitch designs' Tailwind config.
val DarkPalette = Palette(
    dark = true,
    background = Color(0xFF121316), surfaceLowest = Color(0xFF0D0E11), surfaceLow = Color(0xFF1B1B1F), surface = Color(0xFF1F1F23),
    surfaceHigh = Color(0xFF292A2D), surfaceHighest = Color(0xFF343538),
    onSurface = Color(0xFFE3E2E6), onSurfaceVariant = Color(0xFFD7C4AD), outline = Color(0xFF9F8E79), outlineVariant = Color(0xFF524533),
    amber = Color(0xFFFFB020), amberSoft = Color(0xFFFFD59B), onAmber = Color(0xFF2B1A00),
    green = Color(0xFF4EDEA3), greenContainer = Color(0xFF00A572), red = Color(0xFFFFB4AB), redContainer = Color(0xFF93000A), live = Color(0xFFFF5449),
)

// Light ("white") palette: white canvas, warm-grey cards, and a deeper amber so amber text/buttons keep WCAG-level contrast on white.
val LightPalette = Palette(
    dark = false,
    background = Color(0xFFFFFFFF), surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFAF8F5), surface = Color(0xFFF5F2ED),
    surfaceHigh = Color(0xFFECE8E1), surfaceHighest = Color(0xFFE3DED5),
    onSurface = Color(0xFF1B1B1F), onSurfaceVariant = Color(0xFF5C5547), outline = Color(0xFF8A8274), outlineVariant = Color(0xFFD9D2C5),
    amber = Color(0xFFB86E00), amberSoft = Color(0xFFFFE3B8), onAmber = Color(0xFFFFFFFF),
    green = Color(0xFF0B8F5E), greenContainer = Color(0xFFB7F0D8), red = Color(0xFFBA1A1A), redContainer = Color(0xFFFFDAD6), live = Color(0xFFD92D20),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Bd {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val SurfaceLowest: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceLowest
    val SurfaceLow: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceLow
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceHigh
    val SurfaceHighest: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surfaceHighest
    val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onSurface
    val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onSurfaceVariant
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.outline
    val OutlineVariant: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.outlineVariant
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amber
    val AmberSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amberSoft
    val OnAmber: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAmber
    val Green: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.green
    val GreenContainer: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.greenContainer
    val Red: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.red
    val RedContainer: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.redContainer
    val Live: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.live

    @OptIn(ExperimentalTextApi::class)
    private fun family(res: Int, vararg weights: Int) = FontFamily(
        weights.map { w ->
            Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )

    val Head = family(R.font.space_grotesk, 500, 600, 700)
    val Body = family(R.font.geist, 400, 500, 600)
    val Mono = family(R.font.jetbrains_mono, 400, 500, 600, 700)

    // Text styles from the design tokens
    val LabelCaps = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.88.sp)
    val LabelNumeric = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.28.sp)
    val Timer = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.72.sp)
    val BidMetric = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.78).sp)
    val BidMetricSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp)
}

private fun schemeFor(p: Palette) = if (p.dark) darkColorScheme(
    primary = p.amber, onPrimary = p.onAmber,
    primaryContainer = Color(0xFF624000), onPrimaryContainer = p.amberSoft,
    secondary = p.green, onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF005236), onSecondaryContainer = Color(0xFF6FFBBE),
    tertiary = Color(0xFFFFD0CC), error = p.red, onError = Color(0xFF690005),
    errorContainer = p.redContainer, onErrorContainer = Color(0xFFFFDAD6),
    background = p.background, onBackground = p.onSurface,
    surface = p.background, onSurface = p.onSurface,
    surfaceVariant = p.surfaceHighest, onSurfaceVariant = p.onSurfaceVariant,
    outline = p.outline, outlineVariant = p.outlineVariant,
    surfaceContainerLowest = p.surfaceLowest, surfaceContainerLow = p.surfaceLow,
    surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh, surfaceContainerHighest = p.surfaceHighest,
    surfaceTint = p.amber, inverseSurface = Color(0xFFE3E2E6), inverseOnSurface = Color(0xFF2F3034),
) else lightColorScheme(
    primary = p.amber, onPrimary = p.onAmber,
    primaryContainer = Color(0xFFFFDDB0), onPrimaryContainer = Color(0xFF2B1A00),
    secondary = p.green, onSecondary = Color.White,
    secondaryContainer = p.greenContainer, onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF8A4F00), error = p.red, onError = Color.White,
    errorContainer = p.redContainer, onErrorContainer = Color(0xFF410002),
    background = p.background, onBackground = p.onSurface,
    surface = p.background, onSurface = p.onSurface,
    surfaceVariant = p.surfaceHighest, onSurfaceVariant = p.onSurfaceVariant,
    outline = p.outline, outlineVariant = p.outlineVariant,
    surfaceContainerLowest = p.surfaceLowest, surfaceContainerLow = p.surfaceLow,
    surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh, surfaceContainerHighest = p.surfaceHighest,
    surfaceTint = p.amber, inverseSurface = Color(0xFF2F3034), inverseOnSurface = Color(0xFFF1F0F4),
)

private val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = Bd.Head, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.64).sp),
    headlineLarge = TextStyle(fontFamily = Bd.Head, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.24).sp),
    headlineMedium = TextStyle(fontFamily = Bd.Head, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Bd.Head, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Bd.Head, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Bd.Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Bd.Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Bd.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.12.sp),
    labelLarge = TextStyle(fontFamily = Bd.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Bd.Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = Bd.LabelCaps,
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

/** Follows system dark/light mode by default; users can also choose Dark or Light in Profile. */
@Composable
fun BidDriveTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
