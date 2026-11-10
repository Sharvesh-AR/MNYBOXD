// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

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
        assertEquals(ThemeChoice.DEFAULT, legacyTheme("DARK"))
        assertEquals(ThemeChoice.LIGHT, legacyTheme("LIGHT"))
        assertEquals(ThemeChoice.SYSTEM, legacyTheme("SYSTEM"))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme(null))
        assertEquals(ThemeChoice.DEFAULT, legacyTheme("SOMETHING_ELSE"))
    }

    @Test fun `match system resolves to Mono Dark at night and Mono Light by day`() {
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.MONO, ThemeChoice.SYSTEM.resolve(systemDark = true))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.LIGHT, ThemeChoice.SYSTEM.resolve(systemDark = false))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.MONO, ThemeChoice.MONO.resolve(systemDark = false))
    }

    @Test fun `retired themes open as Mono Dark or Mono Light`() {
        // Midnight, AMOLED and Ocean (2.0–2.3) and the earlier beta themes.
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("MIDNIGHT_EMERALD"))
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("PURE_AMOLED"))
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("OCEAN_BLUE"))
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("OBSIDIAN"))
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("FOREST"))
        assertEquals(ThemeChoice.MONO, ThemeChoice.parse("LAVENDER_NIGHT"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.parse("WARM_SAND"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.parse("LIGHT"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.parse("SYSTEM"))
        assertEquals(null, ThemeChoice.parse("NOPE"))
        // Widget theme settings are stored as theme names too.
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.LIGHT, io.github.sharveshar.spendly.ui.theme.AppTheme.parse("WARM_SAND"))
        assertEquals(io.github.sharveshar.spendly.ui.theme.AppTheme.MONO, io.github.sharveshar.spendly.ui.theme.AppTheme.parse("OCEAN_BLUE"))
        assertEquals(listOf(io.github.sharveshar.spendly.ui.theme.AppTheme.MONO, io.github.sharveshar.spendly.ui.theme.AppTheme.LIGHT), io.github.sharveshar.spendly.ui.theme.Palettes.all.map { it.theme })
        // Monochrome is the default look.
        assertEquals(ThemeChoice.MONO, ThemeChoice.DEFAULT)
    }

    @Test fun `each theme has a chart palette and invert-style selection`() {
        for (p in io.github.sharveshar.spendly.ui.theme.Palettes.all) {
            assertEquals(6, p.chart.size)
            assertEquals(6, p.chart.toSet().size)
            assertEquals(10, p.categories.size)
            org.junit.Assert.assertTrue(p.invertSelection)
        }
    }
}
