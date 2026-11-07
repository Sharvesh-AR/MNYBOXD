package io.github.sharveshar.spendly.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The app's colour themes: a few carefully made ones rather than many near-identical ones.
 * Stored by [name] in preferences (and widget settings), so never rename an entry; themes that
 * were removed are mapped to the closest one by [parse].
 */
enum class AppTheme(val label: String, val tagline: String) {
    MONO("Mono", "Matte black and white. Calm, minimal, Spendly's signature look."),
    MIDNIGHT_EMERALD("Midnight", "Deep charcoal with an emerald accent."),
    LIGHT("Light", "Clean off-white with emerald accents."),
    PURE_AMOLED("AMOLED", "True black for OLED screens and night use."),
    OCEAN_BLUE("Ocean", "Deep navy with soft blue accents."),
    ;

    companion object {
        /** Themes offered before 2.0 beta 2, and the theme each one now opens as. */
        private val retired = mapOf(
            "OBSIDIAN" to PURE_AMOLED,
            "FOREST" to MIDNIGHT_EMERALD,
            "LAVENDER_NIGHT" to MIDNIGHT_EMERALD,
            "WARM_SAND" to LIGHT,
        )

        fun parse(name: String?): AppTheme? = name?.let { n -> entries.firstOrNull { it.name == n } ?: retired[n] }
    }
}

/**
 * What the user picked in Settings → Appearance → Themes. [SYSTEM] follows Android's dark mode,
 * switching between Mono and Light.
 */
enum class ThemeChoice(val theme: AppTheme?) {
    SYSTEM(null),
    MONO(AppTheme.MONO),
    MIDNIGHT_EMERALD(AppTheme.MIDNIGHT_EMERALD),
    LIGHT(AppTheme.LIGHT),
    PURE_AMOLED(AppTheme.PURE_AMOLED),
    OCEAN_BLUE(AppTheme.OCEAN_BLUE);

    fun resolve(systemDark: Boolean): AppTheme =
        theme ?: if (systemDark) AppTheme.MONO else AppTheme.LIGHT

    companion object {
        val DEFAULT = MONO

        fun of(theme: AppTheme): ThemeChoice = entries.first { it.theme == theme }

        /** A stored choice. Retired themes open as their closest current theme. */
        fun parse(name: String?): ThemeChoice? =
            name?.let { n -> entries.firstOrNull { it.name == n } ?: AppTheme.parse(n)?.let(::of) }
    }
}

/**
 * Every colour the UI uses, by role. Screens read these through [Spendly.colors] or
 * MaterialTheme.colorScheme and never hard-code colours, so each theme restyles everything.
 */
@Immutable
data class SpendlyPalette(
    val theme: AppTheme,
    val isDark: Boolean,
    /** Window and page background. */
    val background: Color,
    /** Cards and grouped sections. */
    val surface: Color,
    /** Selected segments, chips, raised controls. */
    val surfaceElevated: Color,
    /** Popups and sheets. */
    val surfaceHighest: Color,
    /** Hairline dividers and quiet outlines. */
    val hairline: Color,
    val text: Color,
    val textMuted: Color,
    /** Primary actions, selection, focus. */
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    /** Secondary highlight (sage, silver, blue-grey...). */
    val secondary: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    /** Empty part of bars and progress tracks. */
    val track: Color,
    /** Ten category colours, chosen to read well on this theme's surfaces. */
    val categories: List<Color>,
    /**
     * Selected segments and tiles are shown inverted (a light fill with dark content) rather
     * than as a slightly raised surface. The monochrome look uses this.
     */
    val invertSelection: Boolean = false,
) {
    /** Fill and content colour for a selected segment or tile. */
    val selectedFill: Color get() = if (invertSelection) text else surfaceElevated
    val onSelectedFill: Color get() = if (invertSelection) background else text

    /** Top highlight for layered surfaces: a faint light edge that gives cards depth. */
    val edgeHighlight: Color get() = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.9f)

    /** Soft shadow tint for raised elements. */
    val shadow: Color get() = if (isDark) Color.Black else lerp(text, background, 0.35f)

    /** Lighter top of the floating button's gradient. */
    val accentHighlight: Color get() = lerp(accent, Color.White, if (isDark) 0.22f else 0.16f)

    val scrim: Color get() = if (isDark) Color.Black.copy(alpha = 0.6f) else Color(0xFF101614).copy(alpha = 0.4f)
}

/** Quiet, nearly neutral tints: categories stay tellable apart without colouring the screen. */
private val MonoCategories = listOf(
    Color(0xFFD9D9DE), Color(0xFFA9B6C2), Color(0xFFBFB2A3), Color(0xFFA3B5A9), Color(0xFFB7A9BC),
    Color(0xFFCEC5AB), Color(0xFF8F99A6), Color(0xFFA6A6AC), Color(0xFFC1CBD4), Color(0xFF80878F),
)

private val PastelCategories = listOf(
    Color(0xFF9BBDA9), Color(0xFF7FB3AE), Color(0xFF8FA9C9), Color(0xFFCDB38B), Color(0xFFC79A88),
    Color(0xFFA9A3CF), Color(0xFFC99AAE), Color(0xFFA6B58A), Color(0xFF9AA3B2), Color(0xFFD0B072),
)

private val CoolCategories = listOf(
    Color(0xFF8FC3B0), Color(0xFF7DB6C9), Color(0xFF8EA6E0), Color(0xFFD1B98C), Color(0xFFD39A95),
    Color(0xFFAFA4E0), Color(0xFFCB9DC2), Color(0xFFA9C08E), Color(0xFF9DA8BF), Color(0xFFD9B676),
)

private val EarthCategories = listOf(
    Color(0xFF4F7D63), Color(0xFF3F7F79), Color(0xFF4D6E96), Color(0xFF8C6E3F), Color(0xFF9A5E48),
    Color(0xFF6A6399), Color(0xFF94586F), Color(0xFF63733F), Color(0xFF5B6475), Color(0xFF8E6B1F),
)

object Palettes {
    val Mono = SpendlyPalette(
        theme = AppTheme.MONO, isDark = true,
        // Matte black (not pure black), charcoal layers, white type and soft Apple-like greys.
        background = Color(0xFF0B0B0C), surface = Color(0xFF151517), surfaceElevated = Color(0xFF1F1F22),
        surfaceHighest = Color(0xFF232326), hairline = Color(0xFF242427),
        text = Color(0xFFF5F5F7), textMuted = Color(0xFF8E8E93),
        accent = Color(0xFFF5F5F7), onAccent = Color(0xFF0B0B0C),
        accentContainer = Color(0xFF2A2A2D), onAccentContainer = Color(0xFFF5F5F7),
        secondary = Color(0xFFA1A1A6),
        // Colour only where it carries meaning, and softly.
        positive = Color(0xFF86D3A3), negative = Color(0xFFEE8E8E), warning = Color(0xFFD8BA7A),
        track = Color(0xFF2A2A2E), categories = MonoCategories,
        invertSelection = true,
    )

    val MidnightEmerald = SpendlyPalette(
        theme = AppTheme.MIDNIGHT_EMERALD, isDark = true,
        background = Color(0xFF0D1117), surface = Color(0xFF151B23), surfaceElevated = Color(0xFF1D2630),
        surfaceHighest = Color(0xFF232D38), hairline = Color(0xFF232C36),
        text = Color(0xFFF5F7F8), textMuted = Color(0xFF8D98A3),
        accent = Color(0xFF67D6A0), onAccent = Color(0xFF07130D),
        accentContainer = Color(0xFF1B3A2C), onAccentContainer = Color(0xFFBDEFD5),
        secondary = Color(0xFF9BBDA9),
        positive = Color(0xFF67D6A0), negative = Color(0xFFE58B8B), warning = Color(0xFFD8B36A),
        track = Color(0xFF2B3540), categories = PastelCategories,
    )

    val Light = SpendlyPalette(
        theme = AppTheme.LIGHT, isDark = false,
        background = Color(0xFFF5F6F4), surface = Color(0xFFFFFFFF), surfaceElevated = Color(0xFFECEFEC),
        surfaceHighest = Color(0xFFFFFFFF), hairline = Color(0xFFE1E5E1),
        text = Color(0xFF14191C), textMuted = Color(0xFF5B656C),
        // Emerald dark enough for small text on white (about 5.3:1).
        accent = Color(0xFF0B7A54), onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFD5F1E3), onAccentContainer = Color(0xFF05301E),
        secondary = Color(0xFF5E7A6B),
        positive = Color(0xFF0B7A54), negative = Color(0xFFB3332B), warning = Color(0xFF875900),
        track = Color(0xFFE3E8E4), categories = EarthCategories,
    )

    val PureAmoled = SpendlyPalette(
        theme = AppTheme.PURE_AMOLED, isDark = true,
        background = Color(0xFF000000), surface = Color(0xFF0C0C0D), surfaceElevated = Color(0xFF161618),
        surfaceHighest = Color(0xFF1C1C1F), hairline = Color(0xFF17171A),
        text = Color(0xFFFFFFFF), textMuted = Color(0xFF8C8C92),
        accent = Color(0xFF5FCFA2), onAccent = Color(0xFF00140A),
        accentContainer = Color(0xFF0E2B1F), onAccentContainer = Color(0xFFB5F3D6),
        secondary = Color(0xFF8FC9AF),
        positive = Color(0xFF5FCFA2), negative = Color(0xFFFF8A8A), warning = Color(0xFFF2C266),
        track = Color(0xFF1F1F23), categories = PastelCategories,
    )

    val OceanBlue = SpendlyPalette(
        theme = AppTheme.OCEAN_BLUE, isDark = true,
        background = Color(0xFF0A1020), surface = Color(0xFF111A2E), surfaceElevated = Color(0xFF18233A),
        surfaceHighest = Color(0xFF1E2A44), hairline = Color(0xFF1C2740),
        text = Color(0xFFF2F6FC), textMuted = Color(0xFF8E9BB4),
        accent = Color(0xFF7FB2F0), onAccent = Color(0xFF04152B),
        accentContainer = Color(0xFF173259), onAccentContainer = Color(0xFFD5E6FF),
        secondary = Color(0xFF9DB6DA),
        positive = Color(0xFF5BD6A6), negative = Color(0xFFF08C96), warning = Color(0xFFE6BE6A),
        track = Color(0xFF22304A), categories = CoolCategories,
    )

    val all: List<SpendlyPalette> = listOf(Mono, MidnightEmerald, Light, PureAmoled, OceanBlue)

    fun of(theme: AppTheme): SpendlyPalette = all.first { it.theme == theme }

    val Default = Mono
}
