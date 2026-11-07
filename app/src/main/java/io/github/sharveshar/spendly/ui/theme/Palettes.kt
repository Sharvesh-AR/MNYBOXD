package io.github.sharveshar.spendly.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The app's colour themes. Stored by [name] in preferences, so never rename an entry. */
enum class AppTheme(val label: String, val tagline: String) {
    MIDNIGHT_EMERALD("Midnight Emerald", "Deep charcoal with an emerald accent. Spendly's signature look."),
    OBSIDIAN("Obsidian", "Near-black graphite layers with silver highlights."),
    PURE_AMOLED("Pure AMOLED", "True black for OLED screens, with emerald highlights."),
    OCEAN_BLUE("Ocean Blue", "Deep navy and blue-grey with an electric blue accent."),
    FOREST("Forest", "Dark forest green, sage surfaces and soft mint."),
    LAVENDER_NIGHT("Lavender Night", "Violet-charcoal with soft lavender highlights."),
    WARM_SAND("Warm Sand", "Warm cream, beige surfaces and muted olive. Light theme."),
}

/**
 * What the user picked in Settings → Appearance → Themes. [SYSTEM] follows Android's dark mode,
 * switching between Midnight Emerald and Warm Sand.
 */
enum class ThemeChoice(val theme: AppTheme?) {
    SYSTEM(null),
    MIDNIGHT_EMERALD(AppTheme.MIDNIGHT_EMERALD),
    OBSIDIAN(AppTheme.OBSIDIAN),
    PURE_AMOLED(AppTheme.PURE_AMOLED),
    OCEAN_BLUE(AppTheme.OCEAN_BLUE),
    FOREST(AppTheme.FOREST),
    LAVENDER_NIGHT(AppTheme.LAVENDER_NIGHT),
    WARM_SAND(AppTheme.WARM_SAND);

    fun resolve(systemDark: Boolean): AppTheme =
        theme ?: if (systemDark) AppTheme.MIDNIGHT_EMERALD else AppTheme.WARM_SAND

    companion object {
        val DEFAULT = MIDNIGHT_EMERALD

        fun parse(name: String?): ThemeChoice? = name?.let { n -> entries.firstOrNull { it.name == n } }
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
) {
    /** Top highlight for layered surfaces: a faint light edge that gives cards depth. */
    val edgeHighlight: Color get() = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.9f)

    /** Soft shadow tint for raised elements. */
    val shadow: Color get() = if (isDark) Color.Black else lerp(text, background, 0.35f)

    /** Lighter top of the floating button's gradient. */
    val accentHighlight: Color get() = lerp(accent, Color.White, if (isDark) 0.22f else 0.16f)

    val scrim: Color get() = if (isDark) Color.Black.copy(alpha = 0.6f) else Color(0xFF1A1610).copy(alpha = 0.42f)
}

private val PastelCategories = listOf(
    Color(0xFF9BBDA9), Color(0xFF7FB3AE), Color(0xFF8FA9C9), Color(0xFFCDB38B), Color(0xFFC79A88),
    Color(0xFFA9A3CF), Color(0xFFC99AAE), Color(0xFFA6B58A), Color(0xFF9AA3B2), Color(0xFFD0B072),
)

private val CoolCategories = listOf(
    Color(0xFF8FC3B0), Color(0xFF7DB6C9), Color(0xFF8EA6E0), Color(0xFFD1B98C), Color(0xFFD39A95),
    Color(0xFFAFA4E0), Color(0xFFCB9DC2), Color(0xFFA9C08E), Color(0xFF9DA8BF), Color(0xFFD9B676),
)

private val VioletCategories = listOf(
    Color(0xFF9FC8B4), Color(0xFF86B9C2), Color(0xFF9DAAE0), Color(0xFFD6BC8E), Color(0xFFDDA19A),
    Color(0xFFC1B0EE), Color(0xFFDDA2C4), Color(0xFFAFC292), Color(0xFFA9A3BE), Color(0xFFDDBA7A),
)

private val EarthCategories = listOf(
    Color(0xFF4F7D63), Color(0xFF3F7F79), Color(0xFF4D6E96), Color(0xFF8C6E3F), Color(0xFF9A5E48),
    Color(0xFF6A6399), Color(0xFF94586F), Color(0xFF63733F), Color(0xFF5B6475), Color(0xFF8E6B1F),
)

object Palettes {
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

    val Obsidian = SpendlyPalette(
        theme = AppTheme.OBSIDIAN, isDark = true,
        background = Color(0xFF09090A), surface = Color(0xFF141416), surfaceElevated = Color(0xFF1D1D20),
        surfaceHighest = Color(0xFF242428), hairline = Color(0xFF242427),
        text = Color(0xFFFAFAFA), textMuted = Color(0xFF93939A),
        accent = Color(0xFFD6D7DC), onAccent = Color(0xFF0B0B0C),
        accentContainer = Color(0xFF2A2A2F), onAccentContainer = Color(0xFFF1F1F4),
        secondary = Color(0xFFA9ABB3),
        positive = Color(0xFF5FCB95), negative = Color(0xFFEB8F8F), warning = Color(0xFFDDB96F),
        track = Color(0xFF29292D), categories = PastelCategories,
    )

    val PureAmoled = SpendlyPalette(
        theme = AppTheme.PURE_AMOLED, isDark = true,
        background = Color(0xFF000000), surface = Color(0xFF0C0C0D), surfaceElevated = Color(0xFF161618),
        surfaceHighest = Color(0xFF1C1C1F), hairline = Color(0xFF17171A),
        text = Color(0xFFFFFFFF), textMuted = Color(0xFF8C8C92),
        accent = Color(0xFF3DDC97), onAccent = Color(0xFF00140A),
        accentContainer = Color(0xFF0E2B1F), onAccentContainer = Color(0xFFB5F3D6),
        secondary = Color(0xFF8FD6B4),
        positive = Color(0xFF3DDC97), negative = Color(0xFFFF8A8A), warning = Color(0xFFF2C266),
        track = Color(0xFF1F1F23), categories = PastelCategories,
    )

    val OceanBlue = SpendlyPalette(
        theme = AppTheme.OCEAN_BLUE, isDark = true,
        background = Color(0xFF0A1020), surface = Color(0xFF111A2E), surfaceElevated = Color(0xFF18233A),
        surfaceHighest = Color(0xFF1E2A44), hairline = Color(0xFF1C2740),
        text = Color(0xFFF2F6FC), textMuted = Color(0xFF8E9BB4),
        accent = Color(0xFF4C9EFF), onAccent = Color(0xFF020E1F),
        accentContainer = Color(0xFF12305A), onAccentContainer = Color(0xFFCFE3FF),
        secondary = Color(0xFF8FB3E8),
        positive = Color(0xFF5BD6A6), negative = Color(0xFFF08C96), warning = Color(0xFFE6BE6A),
        track = Color(0xFF22304A), categories = CoolCategories,
    )

    val Forest = SpendlyPalette(
        theme = AppTheme.FOREST, isDark = true,
        background = Color(0xFF0B1410), surface = Color(0xFF122019), surfaceElevated = Color(0xFF1A2B22),
        surfaceHighest = Color(0xFF203429), hairline = Color(0xFF1D2E25),
        text = Color(0xFFEEF5EF), textMuted = Color(0xFF92A898),
        accent = Color(0xFF8EE3B5), onAccent = Color(0xFF06140C),
        accentContainer = Color(0xFF1E3A2B), onAccentContainer = Color(0xFFC9F2DB),
        secondary = Color(0xFFA3BBA8),
        positive = Color(0xFF8EE3B5), negative = Color(0xFFE89A8E), warning = Color(0xFFD9BC72),
        track = Color(0xFF243A2E), categories = PastelCategories,
    )

    val LavenderNight = SpendlyPalette(
        theme = AppTheme.LAVENDER_NIGHT, isDark = true,
        background = Color(0xFF120E1A), surface = Color(0xFF1A1524), surfaceElevated = Color(0xFF241D31),
        surfaceHighest = Color(0xFF2A2239), hairline = Color(0xFF282035),
        text = Color(0xFFF5F1FB), textMuted = Color(0xFF9E95B0),
        accent = Color(0xFFC4B1F5), onAccent = Color(0xFF1A1129),
        accentContainer = Color(0xFF342851), onAccentContainer = Color(0xFFE9E0FF),
        secondary = Color(0xFFB8A9D9),
        positive = Color(0xFF7FD8B0), negative = Color(0xFFF09AAE), warning = Color(0xFFE8C27A),
        track = Color(0xFF30283F), categories = VioletCategories,
    )

    val WarmSand = SpendlyPalette(
        theme = AppTheme.WARM_SAND, isDark = false,
        background = Color(0xFFF6F1E8), surface = Color(0xFFFFFCF6), surfaceElevated = Color(0xFFEFE7DA),
        surfaceHighest = Color(0xFFFFFDF9), hairline = Color(0xFFE6DDCD),
        text = Color(0xFF2A2621), textMuted = Color(0xFF6E655A),
        accent = Color(0xFF5F6F32), onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFE4E9CF), onAccentContainer = Color(0xFF2B3312),
        secondary = Color(0xFF7C6F57),
        positive = Color(0xFF3F7A3C), negative = Color(0xFFB0473A), warning = Color(0xFF946510),
        track = Color(0xFFE7DECF), categories = EarthCategories,
    )

    val all: List<SpendlyPalette> = listOf(MidnightEmerald, Obsidian, PureAmoled, OceanBlue, Forest, LavenderNight, WarmSand)

    fun of(theme: AppTheme): SpendlyPalette = all.first { it.theme == theme }

    val Default = MidnightEmerald
}
