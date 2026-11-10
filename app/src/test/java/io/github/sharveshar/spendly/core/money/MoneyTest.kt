// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test fun `parses rupees into exact paise`() {
        assertEquals(12050L, Money.parseRupees("120.50"))
        assertEquals(12050L, Money.parseRupees("120.5"))
        assertEquals(12000L, Money.parseRupees("120"))
        assertEquals(12000L, Money.parseRupees("120."))
        assertEquals(1L, Money.parseRupees("0.01"))
        assertEquals(12000075L, Money.parseRupees("₹1,20,000.75"))
        assertEquals(10L, Money.parseRupees("0.1"))
    }

    @Test fun `classic float trap amounts stay exact`() {
        // 0.1 + 0.2 != 0.3 in floating point; in paise it is exact.
        val sum = Money.parseRupees("0.1")!! + Money.parseRupees("0.2")!!
        assertEquals(Money.parseRupees("0.3"), sum)
        // Summing 1000 × ₹0.01 must give exactly ₹10.
        var total = 0L
        repeat(1000) { total += Money.parseRupees("0.01")!! }
        assertEquals(1000L, total)
    }

    @Test fun `rejects malformed negative and over precise input`() {
        listOf("", " ", "-5", "1.234", "abc", "1..2", ".", "1e5", "12a", "+5", "NaN", "1,2,3.4.5").forEach {
            assertNull("should reject '$it'", Money.parseRupees(it))
        }
    }

    @Test fun `rejects out of range amounts`() {
        assertEquals(Money.MAX_PAISE, Money.parseRupees("999999999.99"))
        assertNull(Money.parseRupees("1000000000"))
        assertNull(Money.parseRupees("99999999999999999999999"))
        assertFalse(Money.isValidAmount(0))
        assertFalse(Money.isValidAmount(-1))
        assertFalse(Money.isValidAmount(Money.MAX_PAISE + 1))
        assertTrue(Money.isValidAmount(1))
    }

    @Test fun `formats with indian grouping`() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹120.50", Money.format(12050))
        assertEquals("₹999", Money.format(99900))
        assertEquals("₹1,000", Money.format(100000))
        assertEquals("₹12,345.67", Money.format(1234567))
        assertEquals("₹1,00,000", Money.format(10000000))
        assertEquals("₹12,34,56,789.01", Money.format(123456789_01))
        assertEquals("−₹500", Money.format(-50000))
        assertEquals("+₹5", Money.format(500, signed = true))
        assertEquals("₹5.00", Money.format(500, alwaysShowPaise = true))
        assertEquals("1,000", Money.format(100000, symbol = false))
    }

    @Test fun `plain export format`() {
        assertEquals("120.50", Money.toPlainString(12050))
        assertEquals("-0.05", Money.toPlainString(-5))
        assertEquals("0.00", Money.toPlainString(0))
    }

    @Test fun `compact labels`() {
        assertEquals("₹950", Money.formatCompact(95000))
        assertEquals("₹1.2K", Money.formatCompact(125000))
        assertEquals("₹12K", Money.formatCompact(1200000))
        assertEquals("₹1.5L", Money.formatCompact(15000000))
        assertEquals("₹2Cr", Money.formatCompact(2000000000))
    }

    @Test fun `keypad input rules`() {
        var a = AmountInput()
        a = a.appendDigit('0').appendDigit('5')
        assertEquals("5", a.text)
        a = a.appendDecimal().appendDigit('2').appendDigit('5').appendDigit('9')
        assertEquals("5.25", a.text)
        assertEquals(525L, a.paise)
        a = a.appendDecimal()
        assertEquals("5.25", a.text)
        assertEquals("0.", AmountInput().appendDecimal().text)
        var big = AmountInput()
        repeat(12) { big = big.appendDigit('9') }
        assertEquals(9, big.text.length)
        assertEquals("99,99,99,999", big.display())
        assertEquals("", AmountInput("5").backspace().text)
        assertFalse(AmountInput("0.").isValid)
        assertTrue(AmountInput("0.01").isValid)
    }

    @Test fun `keypad display and round trip`() {
        assertEquals("1,20,000.5", AmountInput("120000.5").display())
        assertEquals("0", AmountInput().display())
        listOf(1L, 10L, 99L, 100L, 12050L, 12005L, Money.MAX_PAISE).forEach {
            assertEquals(it, AmountInput.fromPaise(it).paise)
        }
    }
}
