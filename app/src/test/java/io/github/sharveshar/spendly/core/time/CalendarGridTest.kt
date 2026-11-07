package io.github.sharveshar.spendly.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarGridTest {
    private fun grid(year: Int, month: Int, first: DayOfWeek = DayOfWeek.MONDAY) = CalendarMonthGrid.of(YearMonth.of(year, month), first)

    @Test fun `always six rows of seven so the picker never changes height`() {
        for (m in 1..12) assertEquals(42, grid(2026, m).cells.size)
        assertEquals(42, grid(2026, 2, DayOfWeek.SUNDAY).cells.size)
    }

    @Test fun `contains every day of the month exactly once and in order`() {
        for ((y, m) in listOf(2024 to 2, 2025 to 2, 2100 to 2, 2000 to 2, 2026 to 11, 2026 to 12, 2027 to 1)) {
            val g = grid(y, m)
            val days = g.cells.filterNotNull()
            assertEquals(YearMonth.of(y, m).lengthOfMonth(), days.size)
            assertEquals((1..days.size).toList(), days.map { it.dayOfMonth })
            assertTrue(days.all { YearMonth.from(it) == YearMonth.of(y, m) })
        }
    }

    @Test fun `leap years`() {
        assertEquals(29, grid(2024, 2).cells.filterNotNull().size) // divisible by 4
        assertEquals(28, grid(2026, 2).cells.filterNotNull().size)
        assertEquals(28, grid(2100, 2).cells.filterNotNull().size) // century, not divisible by 400
        assertEquals(29, grid(2000, 2).cells.filterNotNull().size) // divisible by 400
    }

    @Test fun `first day lands under the right weekday for Monday and Sunday weeks`() {
        // 1 Nov 2026 is a Sunday.
        assertEquals(LocalDate.of(2026, 11, 1), grid(2026, 11, DayOfWeek.MONDAY).cells[6])
        assertNull(grid(2026, 11, DayOfWeek.MONDAY).cells[5])
        assertEquals(LocalDate.of(2026, 11, 1), grid(2026, 11, DayOfWeek.SUNDAY).cells[0])
        // 1 Jun 2026 is a Monday.
        assertEquals(LocalDate.of(2026, 6, 1), grid(2026, 6, DayOfWeek.MONDAY).cells[0])
        assertEquals(LocalDate.of(2026, 6, 1), grid(2026, 6, DayOfWeek.SUNDAY).cells[1])
    }

    @Test fun `every date sits in the column of its own weekday`() {
        for (first in DayOfWeek.entries) {
            val g = grid(2026, 3, first)
            g.cells.forEachIndexed { i, d -> if (d != null) assertEquals(g.weekdays[i % 7], d.dayOfWeek) }
        }
    }

    @Test fun `weekday headers start on the locale's first day`() {
        assertEquals(DayOfWeek.SUNDAY, grid(2026, 1, DayOfWeek.SUNDAY).weekdays.first())
        assertEquals(DayOfWeek.SATURDAY, grid(2026, 1, DayOfWeek.SUNDAY).weekdays.last())
        assertEquals(DayOfWeek.MONDAY, grid(2026, 1).weekdays.first())
    }

    @Test fun `transactions can be dated from 2000 up to today, never in the future`() {
        val today = LocalDate.of(2026, 11, 8)
        val range = SelectableRange.forTransactions(today)
        assertTrue(today in range)
        assertTrue(LocalDate.of(2000, 1, 1) in range)
        assertFalse(today.plusDays(1) in range)
        assertFalse(LocalDate.of(1999, 12, 31) in range)
        assertTrue(range.canShow(YearMonth.of(2026, 11)))
        assertFalse(range.canShow(YearMonth.of(2026, 12)))
        assertFalse(range.canShow(YearMonth.of(1999, 12)))
    }
}
