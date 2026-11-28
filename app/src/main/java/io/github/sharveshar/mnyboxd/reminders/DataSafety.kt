// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Data Safety reminder: every few days, while there are records with no up-to-date backup,
 * Home asks once to export one. Days are the user's local calendar days.
 *
 * A backup counts as up to date until a transaction is added or edited after it. The clock
 * restarts when the reminder is shown (whatever the answer) and when a backup is saved, so it never
 * appears twice within [EVERY_DAYS] days.
 */
object DataSafety {
    /** The first reminder comes this many days after the reminder first looked at the app. */
    const val FIRST_AFTER_DAYS = 2L

    /** After that, at most one reminder in this many days. */
    const val EVERY_DAYS = 5L

    /**
     * True when the reminder is due on [today]. [since] is when it first looked at this install,
     * [shownAt] when it was last shown, [lastBackupAt] the last successful backup, [lastChangeAt]
     * the last time a transaction was added or edited (all epoch millis).
     */
    fun isDue(
        today: LocalDate,
        zone: ZoneId,
        since: Long,
        shownAt: Long?,
        lastBackupAt: Long?,
        lastChangeAt: Long?,
        transactionCount: Int,
    ): Boolean {
        if (transactionCount == 0) return false
        val backupUpToDate = lastBackupAt != null && (lastChangeAt == null || lastChangeAt <= lastBackupAt)
        if (backupUpToDate) return false
        val anchor = listOfNotNull(shownAt, lastBackupAt).maxOrNull()
        return if (anchor == null) days(since, today, zone) >= FIRST_AFTER_DAYS else days(anchor, today, zone) >= EVERY_DAYS
    }

    private fun days(millis: Long, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), today)

    // --- This app session (one process) ------------------------------------------------------

    private val shown = AtomicBoolean(false)

    /** Claims this session's one chance to show the reminder; false if it was already taken. */
    fun claimSession(): Boolean = shown.compareAndSet(false, true)

    private val exportRequested = AtomicBoolean(false)

    /** "Export Backup": Settings opens its usual export flow when it next appears. */
    fun requestExport() = exportRequested.set(true)

    /** True once after [requestExport]. */
    fun consumeExportRequest(): Boolean = exportRequested.getAndSet(false)
}
