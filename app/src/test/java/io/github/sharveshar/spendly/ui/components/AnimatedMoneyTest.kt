package io.github.sharveshar.spendly.ui.components

import io.github.sharveshar.spendly.data.prefs.legacyTheme
import io.github.sharveshar.spendly.ui.theme.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedMoneyTest {
    @Test fun `an animation always ends exactly on the saved value`() {
        for ((from, to) in listOf(1_943_400L to 1_898_400L, 0L to 2_892_150L, 12_345L to 12_346L, -50_000L to 75_025L, 99_999_999_999L to 0L)) {
            assertEquals(to, interpolatePaise(from, to, 1f))
            assertEquals(from, interpolatePaise(from, to, 0f))
        }
    }

    @Test fun `intermediate values stay between the endpoints`() {
        val from = 1_943_400L
        val to = 1_898_400L
        var f = 0f
        while (f <= 1f) {
            val v = interpolatePaise(from, to, f)
            assertTrue(v in to..from)
            f += 0.05f
        }
    }

    @Test fun `whole-rupee amounts count in whole rupees`() {
        var f = 0.01f
        while (f < 1f) {
            assertEquals(0L, interpolatePaise(1_943_400L, 1_898_400L, f) % 100)
            f += 0.037f
        }
    }

    @Test fun `old Dark, Light and System settings map to the new themes`() {
        assertEquals(ThemeChoice.MIDNIGHT_EMERALD, legacyTheme("DARK"))
        assertEquals(ThemeChoice.LIGHT, legacyTheme("LIGHT"))
        assertEquals(ThemeChoice.SYSTEM, legacyTheme("SYSTEM"))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme(null))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme("SOMETHING_ELSE"))
    }

    @Test fun `match system resolves to Midnight at night and Light by day`() {
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.MIDNIGHT_EMERALD, ThemeChoice.SYSTEM.resolve(systemDark = true))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.LIGHT, ThemeChoice.SYSTEM.resolve(systemDark = false))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.OCEAN_BLUE, ThemeChoice.OCEAN_BLUE.resolve(systemDark = false))
    }

    @Test fun `themes from earlier betas open as the closest of the four`() {
        assertEquals(ThemeChoice.PURE_AMOLED, ThemeChoice.parse("OBSIDIAN"))
        assertEquals(ThemeChoice.MIDNIGHT_EMERALD, ThemeChoice.parse("FOREST"))
        assertEquals(ThemeChoice.MIDNIGHT_EMERALD, ThemeChoice.parse("LAVENDER_NIGHT"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.parse("WARM_SAND"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.parse("SYSTEM"))
        assertEquals(ThemeChoice.OCEAN_BLUE, ThemeChoice.parse("OCEAN_BLUE"))
        assertEquals(null, ThemeChoice.parse("NOPE"))
        // Widget theme settings are stored as theme names too.
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.LIGHT, io.github.sharveshar.spendly.ui.theme.AppTheme.parse("WARM_SAND"))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.PURE_AMOLED, io.github.sharveshar.spendly.ui.theme.AppTheme.parse("OBSIDIAN"))
        assertEquals(4, io.github.sharveshar.spendly.ui.theme.Palettes.all.size)
    }
}
