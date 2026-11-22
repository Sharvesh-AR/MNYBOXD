// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.domain.insights

import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One stretch a budget covers: [days] days from [start] (a day, a Monday-to-Sunday week or a month). */
data class BudgetWindow(val start: LocalDate, val days: Int) {
    /** The first day after the window. */
    val end: LocalDate get() = start.plusDays(days.toLong())

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(end)

    fun range(zone: ZoneId): TimeRange = TimeRange.days(start, days, zone)
}

object BudgetWindows {
    /** Weekly budgets run Monday to Sunday, like the week recap. */
    val WEEK_START: DayOfWeek = DayOfWeek.MONDAY

    /** The [period] window that contains [date]: that day, its week, or its month. */
    fun of(period: BudgetPeriod, date: LocalDate): BudgetWindow = when (period) {
        BudgetPeriod.DAILY -> BudgetWindow(date, 1)
        BudgetPeriod.WEEKLY -> BudgetWindow(date.with(TemporalAdjusters.previousOrSame(WEEK_START)), 7)
        BudgetPeriod.MONTHLY -> BudgetWindow(date.withDayOfMonth(1), date.lengthOfMonth())
    }

    /** Every [period] window that touches [month], in order: its days, its weeks (the first and last may spill over), or the month. */
    fun inMonth(period: BudgetPeriod, month: YearMonth): List<BudgetWindow> {
        val last = month.atEndOfMonth()
        return generateSequence(of(period, month.atDay(1))) { of(period, it.end) }
            .takeWhile { !it.start.isAfter(last) }
            .toList()
    }

    /**
     * The days to load to show any budget in [month]: the month itself plus the ends of the weeks
     * at either side of it, Monday before the 1st to Sunday after the last day.
     */
    fun around(month: YearMonth): BudgetWindow {
        val first = of(BudgetPeriod.WEEKLY, month.atDay(1))
        val last = of(BudgetPeriod.WEEKLY, month.atEndOfMonth())
        return BudgetWindow(first.start, ChronoUnit.DAYS.between(first.start, last.end).toInt())
    }
}

/**
 * Net spending (expenses minus refunds) per day, from [start]. Days outside the list count as
 * nothing spent, so the caller loads every day it asks about (see [BudgetWindows.around]).
 */
class DailySpend(val start: LocalDate, private val amounts: List<Long>) {
    /** Net spending over [window]. */
    fun on(window: BudgetWindow): Long {
        val from = (window.start.toEpochDay() - start.toEpochDay()).coerceAtLeast(0)
        val to = (window.end.toEpochDay() - start.toEpochDay()).coerceAtMost(amounts.size.toLong())
        var total = 0L
        for (i in from until to) total = Math.addExact(total, amounts[i.toInt()])
        return total
    }

    companion object {
        /** Sums [records] by day over [window]; pass one category's records for a category budget. */
        fun of(records: Iterable<SpendRecord>, window: BudgetWindow, zone: ZoneId) =
            DailySpend(window.start, Insights.dailyNetSpend(records, window.start, window.days, zone))
    }
}

/** What a budget looks like in one month, seen from one day. */
sealed interface BudgetView {
    val period: BudgetPeriod
    val limitPaise: Long

    /**
     * The stretch we're in: the month shown for a monthly budget; today or this week for a daily
     * or weekly one. For those, [track] also has a level for every day or week of the month so far
     * and null for the ones still to come; it's empty for a monthly budget.
     */
    data class Live(
        override val period: BudgetPeriod,
        override val limitPaise: Long,
        val status: BudgetStatus,
        val track: List<BudgetLevel?> = emptyList(),
    ) : BudgetView {
        /** Days or weeks so far (this one included) that stayed within the limit. */
        val kept: Int get() = track.count { it != null && it != BudgetLevel.EXCEEDED }
        val elapsed: Int get() = track.count { it != null }
    }

    /** A daily or weekly budget in a month that's over: how each of its days or weeks went. */
    data class Past(override val period: BudgetPeriod, override val limitPaise: Long, val track: List<BudgetLevel>) : BudgetView {
        val kept: Int get() = track.count { it != BudgetLevel.EXCEEDED }
        val over: Int get() = track.size - kept
    }

    /** A daily or weekly budget in a month still to come. */
    data class Upcoming(override val period: BudgetPeriod, override val limitPaise: Long) : BudgetView
}

object BudgetMath {
    /**
     * Progress against [limitPaise]. Refunds can push a stretch's net spending below zero; a budget
     * never counts that as extra room, so it's taken as nothing spent.
     */
    fun status(limitPaise: Long, spentPaise: Long) = BudgetStatus(limitPaise, maxOf(0L, spentPaise))

    /** [limitPaise] per [period] in [month], seen on [today], from the spending in [spend]. */
    fun view(period: BudgetPeriod, limitPaise: Long, month: YearMonth, today: LocalDate, spend: DailySpend): BudgetView {
        if (period == BudgetPeriod.MONTHLY) {
            return BudgetView.Live(period, limitPaise, status(limitPaise, spend.on(BudgetWindow(month.atDay(1), month.lengthOfMonth()))))
        }
        val current = YearMonth.from(today)
        val windows = BudgetWindows.inMonth(period, month)
        return when {
            month.isAfter(current) -> BudgetView.Upcoming(period, limitPaise)
            month.isBefore(current) -> BudgetView.Past(period, limitPaise, windows.map { status(limitPaise, spend.on(it)).level })
            else -> BudgetView.Live(
                period,
                limitPaise,
                status(limitPaise, spend.on(BudgetWindows.of(period, today))),
                windows.map { w -> if (w.start.isAfter(today)) null else status(limitPaise, spend.on(w)).level },
            )
        }
    }

    /**
     * Roughly what [limitPaise] per [period] adds up to over [month] (a weekly limit counts the
     * month's days as sevenths of a week), rounded to the rupee. A sanity check while typing.
     */
    fun monthEquivalent(period: BudgetPeriod, limitPaise: Long, month: YearMonth): Long {
        val days = month.lengthOfMonth().toLong()
        val paise = when (period) {
            BudgetPeriod.DAILY -> Math.multiplyExact(limitPaise, days)
            BudgetPeriod.WEEKLY -> Math.multiplyExact(limitPaise, days) / 7
            BudgetPeriod.MONTHLY -> limitPaise
        }
        return (paise + 50) / 100 * 100
    }
}
