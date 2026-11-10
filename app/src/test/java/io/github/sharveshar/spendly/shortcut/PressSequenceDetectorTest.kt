// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.shortcut

import android.view.KeyEvent
import io.github.sharveshar.spendly.shortcut.PressSequenceDetector.Companion.MAX_GAP_MS
import io.github.sharveshar.spendly.shortcut.PressStep.COMPLETE
import io.github.sharveshar.spendly.shortcut.PressStep.FIRST
import io.github.sharveshar.spendly.shortcut.PressStep.NEXT
import io.github.sharveshar.spendly.shortcut.PressStep.NOT_PART
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PressSequenceDetectorTest {
    private val down = KeyEvent.KEYCODE_VOLUME_DOWN
    private val up = KeyEvent.KEYCODE_VOLUME_UP
    private val triple = ButtonShortcut(ShortcutButton.VOLUME_DOWN, 3)
    private val detector = PressSequenceDetector()

    private fun press(at: Long, key: Int = down, repeat: Int = 0, shortcut: ButtonShortcut = triple) =
        detector.onKeyDown(key, repeat, at, shortcut)

    @Test fun `three quick presses complete the pattern`() {
        assertEquals(FIRST, press(1_000))
        assertEquals(NEXT, press(1_200))
        assertEquals(COMPLETE, press(1_400))
    }

    @Test fun `a pause longer than the limit starts over`() {
        press(1_000)
        assertEquals(NEXT, press(1_000 + MAX_GAP_MS))
        assertEquals(FIRST, press(1_000 + 2 * MAX_GAP_MS + 1))
        assertEquals(NEXT, press(1_100 + 2 * MAX_GAP_MS))
        assertEquals(COMPLETE, press(1_200 + 2 * MAX_GAP_MS))
    }

    @Test fun `holding the button is volume control, not a press`() {
        press(1_000)
        press(1_200)
        assertEquals(NOT_PART, press(1_250, repeat = 1))
        assertEquals(FIRST, press(1_400))
    }

    @Test fun `another key breaks the pattern`() {
        press(1_000)
        press(1_200)
        assertEquals(NOT_PART, press(1_300, key = up))
        assertEquals(FIRST, press(1_400))
    }

    @Test fun `a completed pattern resets so a fourth press starts a new one`() {
        press(1_000); press(1_100); press(1_200)
        assertEquals(FIRST, press(1_300))
    }

    @Test fun `clock going backwards starts over instead of counting`() {
        press(5_000)
        assertEquals(FIRST, press(4_000))
    }

    @Test fun `double press of volume up`() {
        val double = ButtonShortcut(ShortcutButton.VOLUME_UP, 2)
        assertEquals(NOT_PART, press(1_000, key = down, shortcut = double))
        assertEquals(FIRST, press(1_100, key = up, shortcut = double))
        assertEquals(COMPLETE, press(1_300, key = up, shortcut = double))
    }

    @Test fun `only double and triple presses are allowed`() {
        assertThrows(IllegalArgumentException::class.java) { ButtonShortcut(presses = 1) }
        assertThrows(IllegalArgumentException::class.java) { ButtonShortcut(presses = 4) }
    }
}
