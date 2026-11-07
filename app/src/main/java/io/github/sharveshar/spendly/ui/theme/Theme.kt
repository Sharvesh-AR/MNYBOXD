package io.github.sharveshar.spendly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Semantic colours beyond Material's scheme, derived from the active [SpendlyPalette]. Colour is
 * used only where it carries meaning.
 */
@Immutable
data class SpendlyColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val muted: Color,
    val card: Color,
    val cardElevated: Color,
    val hairline: Color,
    val chartMuted: Color,
    val categoryPalette: List<Color>,
    val isDark: Boolean,
    val background: Color,
    val surfaceHighest: Color,
    val accent: Color,
    val accentHighlight: Color,
    val onAccent: Color,
    val secondaryAccent: Color,
    val edgeHighlight: Color,
    val shadow: Color,
    val scrim: Color,
    val palette: SpendlyPalette,
)

fun SpendlyPalette.toColors() = SpendlyColors(
    positive = positive,
    negative = negative,
    warning = warning,
    muted = textMuted,
    card = surface,
    cardElevated = surfaceElevated,
    hairline = hairline,
    chartMuted = track,
    categoryPalette = categories,
    isDark = isDark,
    background = background,
    surfaceHighest = surfaceHighest,
    accent = accent,
    accentHighlight = accentHighlight,
    onAccent = onAccent,
    secondaryAccent = secondary,
    edgeHighlight = edgeHighlight,
    shadow = shadow,
    scrim = scrim,
    palette = this,
)

fun SpendlyPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = accent,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = text,
        tertiary = secondary,
        onTertiary = background,
        tertiaryContainer = surfaceElevated,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textMuted,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = negative,
        onError = background,
        errorContainer = negative.copy(alpha = 0.18f).compositeOverSafe(background),
        onErrorContainer = text,
        outline = textMuted.copy(alpha = 0.55f).compositeOverSafe(background),
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = surfaceHighest,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceHighest,
    )
}

private fun Color.compositeOverSafe(background: Color): Color = compositeOver(background)

val LocalSpendlyColors = staticCompositionLocalOf { Palettes.Default.toColors() }

/** Tabular figures keep amounts aligned and stop digits jumping while they animate. */
private const val TABULAR = "tnum"

private val Base = Typography()
val SpendlyTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, letterSpacing = 0.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, letterSpacing = 0.1.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

/** Amount styles used in rows and summaries. */
object AmountStyle {
    val row = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFeatureSettings = TABULAR)
    val summary = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = TABULAR)
    val compact = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, fontFeatureSettings = TABULAR)
}

private val SpendlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 4-pt spacing scale. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Horizontal page gutter. */
    val gutter = 20.dp
}

/** Applies [palette] to Material components and to [Spendly.colors]. */
@Composable
fun SpendlyTheme(palette: SpendlyPalette = Palettes.Default, content: @Composable () -> Unit) {
    val colors = remember(palette) { palette.toColors() }
    val scheme = remember(palette) { palette.toColorScheme() }
    CompositionLocalProvider(LocalSpendlyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = SpendlyTypography, shapes = SpendlyShapes, content = content)
    }
}

/** Light/dark shorthand used by tests and previews: Midnight Emerald or Warm Sand. */
@Composable
fun SpendlyTheme(dark: Boolean, content: @Composable () -> Unit) =
    SpendlyTheme(if (dark) Palettes.MidnightEmerald else Palettes.WarmSand, content)

object Spendly {
    val colors: SpendlyColors
        @Composable get() = LocalSpendlyColors.current
}
