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
        assertEquals(ThemeChoice.WARM_SAND, legacyTheme("LIGHT"))
        assertEquals(ThemeChoice.SYSTEM, legacyTheme("SYSTEM"))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme(null))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme("SOMETHING_ELSE"))
    }

    @Test fun `match system resolves to Midnight Emerald at night and Warm Sand by day`() {
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.MIDNIGHT_EMERALD, ThemeChoice.SYSTEM.resolve(systemDark = true))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.WARM_SAND, ThemeChoice.SYSTEM.resolve(systemDark = false))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.FOREST, ThemeChoice.FOREST.resolve(systemDark = false))
    }
}
