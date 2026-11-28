// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sharveshar.mnyboxd.AppContainer
import io.github.sharveshar.mnyboxd.MainActivity
import io.github.sharveshar.mnyboxd.QuickAddActivity
import io.github.sharveshar.mnyboxd.R
import io.github.sharveshar.mnyboxd.StableNames
import io.github.sharveshar.mnyboxd.appContainer
import io.github.sharveshar.mnyboxd.data.db.DatabaseState
import io.github.sharveshar.mnyboxd.domain.insights.TimeRange
import io.github.sharveshar.mnyboxd.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Expense Check-in: one notification a day, around 7 PM local time, and only on a day with no
 * expense recorded yet: "Any expenses to add?" with Add Expense and No Spending.
 *
 * One inexact alarm (no exact-alarm permission) is kept for the next 7 PM. When it fires, the
 * day is checked against the database itself, just before anything is shown. The day is saved as
 * handled before the notification is posted, so restarts, reboots, a dismissed notification or a
 * second alarm can never bring another one that day. Everything stays on the phone.
 */
object ExpenseCheckIn {
    const val CHANNEL_ID = "expense_checkin"
    private const val NOTIFICATION_ID = 7_001
    private const val REQUEST_ALARM = 7_001
    private const val REQUEST_ADD = 7_002
    private const val REQUEST_NO_SPENDING = 7_003
    const val ACTION_ALARM = "${StableNames.PACKAGE_ID}.action.CHECK_IN"
    const val ACTION_NO_SPENDING = "${StableNames.PACKAGE_ID}.action.CHECK_IN_NO_SPENDING"

    /** When the reminder is due. */
    val TIME: LocalTime = LocalTime.of(19, 0)

    /** After this, "today" is nearly over: a late alarm stays quiet rather than ask at midnight. */
    private val LAST_CALL: LocalTime = LocalTime.of(23, 0)

    /**
     * The next 7 PM worth waking up for: today's if it's still ahead and today isn't handled yet,
     * otherwise tomorrow's. Time zones and daylight saving come from [now]'s zone.
     */
    fun nextTrigger(now: ZonedDateTime, handledDay: LocalDate?): ZonedDateTime {
        val today = now.toLocalDate()
        val todayAt = today.atTime(TIME).atZone(now.zone)
        return if (handledDay != today && now.isBefore(todayAt)) todayAt else today.plusDays(1).atTime(TIME).atZone(now.zone)
    }

    /** Whether to remind now: on, not handled today, between 7 and 11 PM, and no expense recorded today. */
    fun shouldRemind(now: ZonedDateTime, enabled: Boolean, handledDay: LocalDate?, expensesToday: Int): Boolean {
        val time = now.toLocalTime()
        return enabled && handledDay != now.toLocalDate() && !time.isBefore(TIME) && time.isBefore(LAST_CALL) && expensesToday == 0
    }

    /** Android lets MNYBOXD post notifications (Android 13+ permission, and not blocked in Settings). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Switches the reminder on or off; off also cancels the pending alarm and any notification showing. */
    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.appContainer.preferences.setCheckInEnabled(enabled)
        schedule(context)
    }

    /** Sets (or, when switched off, cancels) the one alarm for the next 7 PM. Safe to call any time. */
    suspend fun schedule(context: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val app = context.applicationContext
        val prefs = app.appContainer.preferences.preferences.first()
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val alarm = alarmIntent(app)
        if (!prefs.checkInEnabled) {
            alarms.cancel(alarm)
            clearNotification(app)
            return
        }
        val next = nextTrigger(now, parseDay(prefs.checkInHandledDay))
        // Inexact, but allowed while the phone dozes. The same PendingIntent replaces any earlier
        // alarm, so there is never more than one.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), alarm)
    }

    /** The alarm went off: check today's expenses and remind if there are none, then set tomorrow's. */
    suspend fun onAlarm(context: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val app = context.applicationContext
        val container = app.appContainer
        val prefs = container.preferences.preferences.first()
        val handled = parseDay(prefs.checkInHandledDay)
        // Cheap checks first; the database is only read when a reminder is actually possible.
        if (shouldRemind(now, prefs.checkInEnabled, handled, expensesToday = 0) && canNotify(app)) {
            val expenses = expensesOn(container, now.toLocalDate(), now.zone)
            if (expenses != null && shouldRemind(now, prefs.checkInEnabled, handled, expenses)) {
                // Saved first: whatever happens next, today never gets a second reminder.
                container.preferences.setCheckInHandledDay(now.toLocalDate().toString())
                post(app)
            }
        }
        schedule(app, now)
    }

    /** "No Spending": today is answered; the notification goes and nothing else is recorded. */
    suspend fun onNoSpending(context: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val app = context.applicationContext
        app.appContainer.preferences.setCheckInHandledDay(now.toLocalDate().toString())
        clearNotification(app)
        schedule(app, now)
    }

    /** Expenses recorded on [day] (income, refunds and transfers don't count), or null if the database can't be read. */
    private suspend fun expensesOn(container: AppContainer, day: LocalDate, zone: ZoneId): Int? {
        val state = withTimeoutOrNull(10_000) { container.databaseManager.state.first { it !is DatabaseState.Opening } }
        if (state !is DatabaseState.Ready) return null
        return runCatching {
            container.repositories.transactions.observeSpendRecords(TimeRange.day(day, zone)).first().count { it.type == TransactionType.EXPENSE }
        }.getOrNull()
    }

    /** Removes the reminder if it's showing (Quick Add opened, No Spending, or switched off). */
    fun clearNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission") // canNotify() is checked just before.
    private fun post(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        // Tapping the notification or Add Expense opens the usual Quick Add pop-up.
        val add = PendingIntent.getActivity(
            context, REQUEST_ADD, QuickAddActivity.launchIntent(context, MainActivity.ACTION_QUICK_ADD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val noSpending = PendingIntent.getBroadcast(
            context, REQUEST_NO_SPENDING, Intent(context, ExpenseCheckInReceiver::class.java).setAction(ACTION_NO_SPENDING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "You haven't recorded an expense today. Did you make any purchases?"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_w_logo)
            .setContentTitle("Any expenses to add?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(add)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Add Expense", add)
            .addAction(0, "No Spending", noSpending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Expense Check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder at about 7 PM on days with no expense recorded yet."
            },
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_ALARM, Intent(context, ExpenseCheckInReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun parseDay(day: String?): LocalDate? = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/**
 * Expense Check-in's alarm and its No Spending button (MNYBOXD's own PendingIntents), plus the
 * system broadcasts after which the alarm must be set again: restart, app update, a new time zone
 * or clock change. Not exported; system broadcasts still arrive.
 */
class ExpenseCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                when (intent.action) {
                    ExpenseCheckIn.ACTION_ALARM -> ExpenseCheckIn.onAlarm(context)
                    ExpenseCheckIn.ACTION_NO_SPENDING -> ExpenseCheckIn.onNoSpending(context)
                    else -> ExpenseCheckIn.schedule(context)
                }
            } catch (e: Exception) {
                // A reminder is never worth a crash; the next app start sets the alarm again.
            } finally {
                pending.finish()
            }
        }
    }
}
