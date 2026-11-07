package io.github.sharveshar.spendly.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The days shown for one month in a calendar: always 6 rows of 7 so the picker never changes
 * height between months. Cells before the 1st and after the last day are null.
 *
 * @param firstDayOfWeek the locale's first day of the week (Monday in India, Sunday in the US).
 */
data class CalendarMonthGrid(val month: YearMonth, val firstDayOfWeek: DayOfWeek, val cells: List<LocalDate?>) {
    /** Weekday headers in display order. */
    val weekdays: List<DayOfWeek> get() = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }

    companion object {
        const val ROWS = 6
        const val COLUMNS = 7

        fun of(month: YearMonth, firstDayOfWeek: DayOfWeek): CalendarMonthGrid {
            val first = month.atDay(1)
            // Days between the week's first day and the 1st of the month (0..6).
            val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            val cells = (0 until ROWS * COLUMNS).map { i ->
                val day = i - lead + 1
                if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
            }
            return CalendarMonthGrid(month, firstDayOfWeek, cells)
        }
    }
}

/** Which dates may be picked for a transaction: none before [min], none after [max] (today). */
data class SelectableRange(val min: LocalDate, val max: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(min) && !date.isAfter(max)
    fun canShow(month: YearMonth) = !month.isBefore(YearMonth.from(min)) && !month.isAfter(YearMonth.from(max))

    companion object {
        /** Spendly's rule: from 1 Jan 2000 up to today; future-dated transactions aren't allowed. */
        fun forTransactions(today: LocalDate) = SelectableRange(LocalDate.of(2000, 1, 1), today)
    }
}
