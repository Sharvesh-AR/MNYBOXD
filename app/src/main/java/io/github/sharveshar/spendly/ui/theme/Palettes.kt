// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

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
    MONO("Mono Dark", "Matte black and white. Calm, minimal, MNYBOXD's signature look."),
    LIGHT("Mono Light", "The same look inverted: paper white and ink black."),
    ;

    companion object {
        /** Themes offered before, and the theme each one now opens as. */
        private val retired = mapOf(
            "MIDNIGHT_EMERALD" to MONO,
            "PURE_AMOLED" to MONO,
            "OCEAN_BLUE" to MONO,
            "OBSIDIAN" to MONO,
            "FOREST" to MONO,
            "LAVENDER_NIGHT" to MONO,
            "WARM_SAND" to LIGHT,
        )

        fun parse(name: String?): AppTheme? = name?.let { n -> entries.firstOrNull { it.name == n } ?: retired[n] }
    }
}

/**
 * What the user picked in Settings → Appearance → Themes. [SYSTEM] follows Android's dark mode,
 * switching between Mono Dark and Mono Light.
 */
enum class ThemeChoice(val theme: AppTheme?) {
    SYSTEM(null),
    MONO(AppTheme.MONO),
    LIGHT(AppTheme.LIGHT);

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
     * Slice colours for breakdown charts, in order. Validated as a set (lightness band, chroma,
     * colour-blind separation between neighbours) against this theme's card surface, so
     * neighbouring slices always read as different. Anything past the last slot is "Everything
     * else" in [track]-grey.
     */
    val chart: List<Color>,
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

/** Darker quiet tints for the light theme, readable as icons on white. */
private val MonoLightCategories = listOf(
    Color(0xFF46464C), Color(0xFF4C6175), Color(0xFF6B5A47), Color(0xFF4A6553), Color(0xFF64536B),
    Color(0xFF6C6340), Color(0xFF3E4A58), Color(0xFF5C5C63), Color(0xFF52626F), Color(0xFF6A7079),
)

/** Validated with the dataviz palette checks against #151517 (Mono Dark cards). */
private val DarkChart = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181), Color(0xFF008300),
)

/**
 * Validated against white (Mono Light cards). Three slots sit under 3:1 contrast on white, so
 * charts using them always carry visible labels (the breakdown list with names and amounts).
 */
private val LightChart = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4), Color(0xFF008300),
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
        track = Color(0xFF2A2A2E), categories = MonoCategories, chart = DarkChart,
        invertSelection = true,
    )

    /** Mono, inverted: off-white page, white cards, ink-black type and actions. */
    val Light = SpendlyPalette(
        theme = AppTheme.LIGHT, isDark = false,
        background = Color(0xFFF4F4F5), surface = Color(0xFFFFFFFF), surfaceElevated = Color(0xFFEAEAEC),
        surfaceHighest = Color(0xFFFFFFFF), hairline = Color(0xFFE1E1E4),
        text = Color(0xFF0B0B0C), textMuted = Color(0xFF6A6A70),
        accent = Color(0xFF0B0B0C), onAccent = Color(0xFFF5F5F7),
        accentContainer = Color(0xFFE4E4E7), onAccentContainer = Color(0xFF0B0B0C),
        secondary = Color(0xFF5C5C62),
        // Dark enough for small text on white.
        positive = Color(0xFF1C7A4B), negative = Color(0xFFC0392F), warning = Color(0xFF8A5A00),
        track = Color(0xFFE5E5E8), categories = MonoLightCategories, chart = LightChart,
        invertSelection = true,
    )

    val all: List<SpendlyPalette> = listOf(Mono, Light)

    fun of(theme: AppTheme): SpendlyPalette = all.first { it.theme == theme }

    val Default = Mono
}
