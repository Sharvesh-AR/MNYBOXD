// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.MnyboxdApplication
import io.github.sharveshar.mnyboxd.appContainer
import io.github.sharveshar.mnyboxd.data.db.DatabaseState
import io.github.sharveshar.mnyboxd.data.db.MnyboxdDatabase
import io.github.sharveshar.mnyboxd.data.testDatabase
import io.github.sharveshar.mnyboxd.domain.ledger.TransactionDraft
import io.github.sharveshar.mnyboxd.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MnyboxdApplication::class)
class ExpenseCheckInTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val container get() = context.appContainer
    private lateinit var db: MnyboxdDatabase
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(today, LocalTime.of(h, m), zone)

    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(10_000) { block() } }

    @Before fun setUp() {
        db = testDatabase()
        // The app's own start-up open (no SQLCipher or Keystore here) must finish before the test
        // database takes its place, or it could overwrite it.
        io { container.databaseManager.state.first { it !is DatabaseState.Opening } }
        container.databaseManager.useForTesting(db)
        // DataStore keeps one instance per process, so settings from an earlier test would linger.
        io { container.preferences.clearAll() }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() = db.close()

    private fun record(type: TransactionType, hour: Int) = io {
        val cash = db.accountDao().getAll().first().id
        val category = db.categoryDao().getAll().first { it.key == if (type == TransactionType.INCOME) "salary" else "food" }.id
        container.repositories.transactions.save(
            TransactionDraft(type, 250_00, cash, null, category, null, null, null, "Test", at(hour).toInstant().toEpochMilli()),
            "check-in-$type-$hour", null,
        )
    }

    @Test fun `on a day with no expense, one reminder at 7 PM, and never a second`() {
        io { ExpenseCheckIn.onAlarm(context, at(19, 2)) }
        assertEquals(1, notifications.size)
        val shown = notifications.single()
        assertEquals("Any expenses to add?", shown.extras.getString("android.title"))
        assertEquals(listOf("Add Expense", "No Spending"), shown.actions.map { it.title.toString() })
        assertEquals(today.toString(), io { container.preferences.preferences.first() }.checkInHandledDay)

        // Dismissed, then the alarm fires again (a restart, a reboot, a late duplicate): nothing.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        io { ExpenseCheckIn.onAlarm(context, at(20, 30)) }
        assertEquals(0, notifications.size)
        // And tomorrow's 7 PM is already set.
        assertEquals(today.plusDays(1).atTime(19, 0).atZone(zone).toInstant().toEpochMilli(), alarms.peekNextScheduledAlarm()?.triggerAtTime)
    }

    @Test fun `an expense recorded today means no reminder, and income doesn't count`() {
        record(TransactionType.INCOME, 9)
        record(TransactionType.EXPENSE, 12)
        io { ExpenseCheckIn.onAlarm(context, at(19, 1)) }
        assertEquals(0, notifications.size)
        assertNull(io { container.preferences.preferences.first() }.checkInHandledDay)
    }

    @Test fun `income alone still gets the reminder`() {
        record(TransactionType.INCOME, 9)
        io { ExpenseCheckIn.onAlarm(context, at(19, 1)) }
        assertEquals(1, notifications.size)
    }

    @Test fun `No Spending answers today without recording anything`() {
        val before = io { db.transactionDao().getAll().size }
        io { ExpenseCheckIn.onNoSpending(context, at(18, 0)) }
        io { ExpenseCheckIn.onAlarm(context, at(19, 1)) }
        assertEquals(0, notifications.size)
        assertEquals(before, io { db.transactionDao().getAll().size })
    }

    @Test fun `switching it off cancels the alarm, and on sets the next 7 PM`() {
        io { ExpenseCheckIn.setEnabled(context, false) }
        assertNull(alarms.peekNextScheduledAlarm())
        io { ExpenseCheckIn.onAlarm(context, at(19, 1)) }
        assertEquals(0, notifications.size)

        io { ExpenseCheckIn.setEnabled(context, true) }
        val next = alarms.peekNextScheduledAlarm()!!.triggerAtTime
        assertEquals(LocalTime.of(19, 0), java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalTime())
        assertEquals(1, alarms.scheduledAlarms.size)
        // Scheduling again (every app start does) never adds a second alarm.
        io { ExpenseCheckIn.schedule(context) }
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun `without notification permission it stays quiet and doesn't use up the day`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        io { ExpenseCheckIn.onAlarm(context, at(19, 1)) }
        assertEquals(0, notifications.size)
        assertNull(io { container.preferences.preferences.first() }.checkInHandledDay)
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
    }
}
