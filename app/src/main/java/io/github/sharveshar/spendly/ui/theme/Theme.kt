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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic colours beyond Material's scheme. Colour is used only where it carries meaning. */
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
)

private val DarkCategoryPalette = listOf(
    Color(0xFF9BBDA9), Color(0xFF7FB3AE), Color(0xFF8FA9C9), Color(0xFFCDB38B), Color(0xFFC79A88),
    Color(0xFFA9A3CF), Color(0xFFC99AAE), Color(0xFFA6B58A), Color(0xFF9AA3B2), Color(0xFFD0B072),
)
private val LightCategoryPalette = listOf(
    Color(0xFF4F7D63), Color(0xFF3F7F79), Color(0xFF4D6E96), Color(0xFF8C6E3F), Color(0xFF9A5E48),
    Color(0xFF6A6399), Color(0xFF94586F), Color(0xFF63733F), Color(0xFF5B6475), Color(0xFF8E6B1F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF67D6A0),
    onPrimary = Color(0xFF07130D),
    primaryContainer = Color(0xFF1B3A2C),
    onPrimaryContainer = Color(0xFFBDEFD5),
    secondary = Color(0xFF9BBDA9),
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1D2630),
    onSecondaryContainer = Color(0xFFF5F7F8),
    tertiary = Color(0xFF9BBDA9),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF5F7F8),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = Color(0xFF1D2630),
    onSurfaceVariant = Color(0xFF89949F),
    surfaceContainerLowest = Color(0xFF0A0E13),
    surfaceContainerLow = Color(0xFF12171E),
    surfaceContainer = Color(0xFF151B23),
    surfaceContainerHigh = Color(0xFF1D2630),
    surfaceContainerHighest = Color(0xFF26303B),
    surfaceBright = Color(0xFF26303B),
    outline = Color(0xFF3A4652),
    outlineVariant = Color(0xFF232C36),
    error = Color(0xFFE58B8B),
    onError = Color(0xFF1A0B0B),
    errorContainer = Color(0xFF3A1F22),
    onErrorContainer = Color(0xFFF6CFCF),
    inverseSurface = Color(0xFFF5F7F8),
    inverseOnSurface = Color(0xFF0D1117),
    scrim = Color(0xFF000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E7A52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F1E2),
    onPrimaryContainer = Color(0xFF0B3523),
    secondary = Color(0xFF4F6D5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6ECE8),
    onSecondaryContainer = Color(0xFF0D1117),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF0D1117),
    surface = Color(0xFFF5F6F7),
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFE8EBEE),
    onSurfaceVariant = Color(0xFF5A646E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF0F2),
    surfaceContainerHighest = Color(0xFFE3E7EA),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFFB4BCC4),
    outlineVariant = Color(0xFFE1E5E9),
    error = Color(0xFFB23A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DCDC),
    onErrorContainer = Color(0xFF410E0E),
)

private val DarkExtras = SpendlyColors(
    positive = Color(0xFF67D6A0),
    negative = Color(0xFFE58B8B),
    warning = Color(0xFFD8B36A),
    muted = Color(0xFF89949F),
    card = Color(0xFF151B23),
    cardElevated = Color(0xFF1D2630),
    hairline = Color(0xFF232C36),
    chartMuted = Color(0xFF2B3540),
    categoryPalette = DarkCategoryPalette,
    isDark = true,
)

private val LightExtras = SpendlyColors(
    positive = Color(0xFF1E7A52),
    negative = Color(0xFFB23A3A),
    warning = Color(0xFF9A6B10),
    muted = Color(0xFF5A646E),
    card = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFEDF0F2),
    hairline = Color(0xFFE1E5E9),
    chartMuted = Color(0xFFDCE1E5),
    categoryPalette = LightCategoryPalette,
    isDark = false,
)

val LocalSpendlyColors = staticCompositionLocalOf { DarkExtras }

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

@Composable
fun SpendlyTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalSpendlyColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = scheme, typography = SpendlyTypography, shapes = SpendlyShapes, content = content)
    }
}

object Spendly {
    val colors: SpendlyColors
        @Composable get() = LocalSpendlyColors.current
}
