// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.reminders

import io.github.sharveshar.mnyboxd.data.prefs.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The timing rules of Expense Check-in and the Data Safety reminder (no Android needed). */
class ReminderRulesTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 28)
    private fun at(h: Int, m: Int = 0, zone: ZoneId = india, date: LocalDate = day) = ZonedDateTime.of(date, LocalTime.of(h, m), zone)

    // --- Expense Check-in ---------------------------------------------------------------------

    @Test fun `Expense Check-in is on by default`() {
        assertTrue(UserPreferences().checkInEnabled)
    }

    @Test fun `the next reminder is today's 7 PM while it's ahead, otherwise tomorrow's`() {
        assertEquals(at(19), ExpenseCheckIn.nextTrigger(at(9), handledDay = null))
        assertEquals(at(19, date = day.plusDays(1)), ExpenseCheckIn.nextTrigger(at(19), handledDay = null))
        assertEquals(at(19, date = day.plusDays(1)), ExpenseCheckIn.nextTrigger(at(21), handledDay = null))
        // Today already answered (No Spending) or reminded: tomorrow, even in the morning.
        assertEquals(at(19, date = day.plusDays(1)), ExpenseCheckIn.nextTrigger(at(9), handledDay = day))
    }

    @Test fun `7 PM is local time, across time zones and daylight saving`() {
        val london = ZoneId.of("Europe/London")
        // The night the clocks go back (25 October 2026): still 7 PM on the wall clock the next day.
        val beforeChange = ZonedDateTime.of(LocalDate.of(2026, 10, 24), LocalTime.of(20, 0), london)
        val next = ExpenseCheckIn.nextTrigger(beforeChange, handledDay = null)
        assertEquals(LocalTime.of(19, 0), next.toLocalTime())
        assertEquals(LocalDate.of(2026, 10, 25), next.toLocalDate())
        // After moving to New York, it's New York's 7 PM.
        val ny = ZoneId.of("America/New_York")
        assertEquals(LocalTime.of(19, 0), ExpenseCheckIn.nextTrigger(ZonedDateTime.of(day, LocalTime.of(8, 0), ny), null).toLocalTime())
    }

    @Test fun `it reminds only between 7 and 11 PM, once a day, with no expense recorded`() {
        assertTrue(ExpenseCheckIn.shouldRemind(at(19, 5), enabled = true, handledDay = null, expensesToday = 0))
        assertTrue(ExpenseCheckIn.shouldRemind(at(22, 30), enabled = true, handledDay = day.minusDays(1), expensesToday = 0))
        assertFalse(ExpenseCheckIn.shouldRemind(at(19, 5), enabled = true, handledDay = null, expensesToday = 1))
        assertFalse(ExpenseCheckIn.shouldRemind(at(19, 5), enabled = true, handledDay = day, expensesToday = 0))
        assertFalse(ExpenseCheckIn.shouldRemind(at(19, 5), enabled = false, handledDay = null, expensesToday = 0))
        assertFalse(ExpenseCheckIn.shouldRemind(at(18, 59), enabled = true, handledDay = null, expensesToday = 0))
        assertFalse(ExpenseCheckIn.shouldRemind(at(23, 10), enabled = true, handledDay = null, expensesToday = 0))
        assertFalse(ExpenseCheckIn.shouldRemind(at(0, 30), enabled = true, handledDay = null, expensesToday = 0))
    }

    // --- Data Safety reminder -----------------------------------------------------------------

    private fun millis(daysAgo: Long, hour: Int = 10) = ZonedDateTime.of(day.minusDays(daysAgo), LocalTime.of(hour, 0), india).toInstant().toEpochMilli()

    private fun due(since: Long = millis(30), shownAt: Long? = null, backupAt: Long? = null, changeAt: Long? = millis(0), count: Int = 12) =
        DataSafety.isDue(day, india, since, shownAt, backupAt, changeAt, count)

    @Test fun `nothing to lose, no reminder`() {
        assertFalse(due(count = 0, changeAt = null))
    }

    @Test fun `the first reminder comes two days in`() {
        assertFalse(due(since = millis(0)))
        assertFalse(due(since = millis(1)))
        assertTrue(due(since = millis(2)))
    }

    @Test fun `after it's shown (or Maybe Later), it waits five days`() {
        assertFalse(due(shownAt = millis(0)))
        assertFalse(due(shownAt = millis(4, hour = 1)))
        assertTrue(due(shownAt = millis(5, hour = 23)))
    }

    @Test fun `an up-to-date backup stops it, and new records after the backup start it again`() {
        // Backed up after the last change: no reminder, however long ago.
        assertFalse(due(backupAt = millis(20), changeAt = millis(21)))
        // A transaction added after the backup makes it outdated: five days from the backup.
        assertFalse(due(backupAt = millis(3), changeAt = millis(1)))
        assertTrue(due(backupAt = millis(6), changeAt = millis(1)))
        // A newer showing restarts the wait even when the backup is older.
        assertFalse(due(backupAt = millis(9), changeAt = millis(1), shownAt = millis(2)))
    }
}
