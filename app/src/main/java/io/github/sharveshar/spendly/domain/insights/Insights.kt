// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.domain.insights

import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Light projection used for every spending/income aggregation. Transfers are never included. */
data class SpendRecord(
    val type: TransactionType,
    val amountPaise: Long,
    val categoryId: Long?,
    val channel: PaymentChannel?,
    val occurredAt: Long,
)

/** Half-open epoch-millisecond range [startMillis, endMillis). */
data class TimeRange(val startMillis: Long, val endMillis: Long) {
    operator fun contains(t: Long) = t in startMillis until endMillis

    companion object {
        fun month(month: YearMonth, zone: ZoneId) = TimeRange(
            month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun days(start: LocalDate, count: Int, zone: ZoneId) = TimeRange(
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            start.plusDays(count.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun day(date: LocalDate, zone: ZoneId) = days(date, 1, zone)
    }
}

enum class BudgetLevel { OK, WARNING, EXCEEDED }

/** Budget progress in exact integer arithmetic. [fraction] is for drawing only. */
data class BudgetStatus(val limitPaise: Long, val spentPaise: Long) {
    val remainingPaise: Long get() = limitPaise - spentPaise
    val level: BudgetLevel
        get() = when {
            limitPaise <= 0 -> BudgetLevel.OK
            spentPaise >= limitPaise -> BudgetLevel.EXCEEDED
            spentPaise * 100 >= limitPaise * WARNING_PERCENT -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
    val percentUsed: Int
        get() = if (limitPaise <= 0) 0 else (maxOf(0, spentPaise) * 100 / limitPaise).toInt()
    val fraction: Float
        get() = if (limitPaise <= 0) 0f else (maxOf(0, spentPaise).toDouble() / limitPaise).toFloat().coerceIn(0f, 1f)

    companion object {
        const val WARNING_PERCENT = 80L

        /** The level newly reached when spending moves from [before] to [after], if it got worse. */
        fun crossed(before: BudgetStatus, after: BudgetStatus): BudgetLevel? =
            if (after.level.ordinal > before.level.ordinal) after.level else null
    }
}

/**
 * A look back at one stretch of time (a week or a month) and how it compares. [elapsedDays] of
 * the period's [totalDays] have happened; the comparison uses the same number of days at the
 * start of the previous period, so "this week so far" is never set against a whole week.
 */
data class Recap(
    val start: LocalDate,
    val totalDays: Int,
    val elapsedDays: Int,
    val spentPaise: Long,
    /** Spending over the same number of days at the start of the previous period. */
    val previousPaise: Long,
    val incomePaise: Long,
    /** Expenses recorded in the period. */
    val expenseCount: Int,
    /** Net spend per day, [totalDays] long (days still to come are 0). */
    val daily: List<Long>,
    /** The category with the most net spending, and that amount. */
    val topCategoryId: Long?,
    val topCategoryPaise: Long,
    /** The single largest expense. */
    val biggestPaise: Long,
    val biggestCategoryId: Long?,
    val biggestDay: LocalDate?,
    /** The day with the most net spending. */
    val busiestDay: LocalDate?,
    val busiestPaise: Long,
    /** Elapsed days with no net spending. */
    val noSpendDays: Int,
) {
    val change: Int? get() = Insights.percentChange(spentPaise, previousPaise)
    val hasSpending: Boolean get() = expenseCount > 0
    val topShare: Int get() = if (spentPaise <= 0) 0 else (topCategoryPaise * 100 / spentPaise).toInt().coerceIn(0, 100)

    /** Average net spend per elapsed day, rounded half-up to the paisa. */
    val dailyAverage: Long
        get() {
            if (elapsedDays <= 0) return 0
            val q = spentPaise / elapsedDays
            val r = spentPaise % elapsedDays
            return if (r * 2 >= elapsedDays) q + 1 else q
        }
}

object Insights {
    /**
     * Builds a [Recap] for the [totalDays] from [start], of which [elapsedDays] have happened,
     * compared with the same number of days from [previousStart]. [records] must cover both.
     */
    fun recap(records: List<SpendRecord>, start: LocalDate, totalDays: Int, elapsedDays: Int, previousStart: LocalDate, zone: ZoneId): Recap {
        val elapsed = elapsedDays.coerceIn(0, totalDays)
        val range = TimeRange.days(start, elapsed, zone)
        val inPeriod = records.filter { it.occurredAt in range }
        val previous = records.filter { it.occurredAt in TimeRange.days(previousStart, elapsed, zone) }
        val daily = dailyNetSpend(inPeriod, start, totalDays, zone)
        val top = byCategory(inPeriod).firstOrNull()
        val biggest = inPeriod.filter { it.type == TransactionType.EXPENSE }.maxWithOrNull(compareBy<SpendRecord> { it.amountPaise }.thenBy { -it.occurredAt })
        val busiest = daily.take(elapsed).withIndex().filter { it.value > 0 }.maxByOrNull { it.value }
        return Recap(
            start = start,
            totalDays = totalDays,
            elapsedDays = elapsed,
            spentPaise = netSpend(inPeriod),
            previousPaise = netSpend(previous),
            incomePaise = income(inPeriod),
            expenseCount = inPeriod.count { it.type == TransactionType.EXPENSE },
            daily = daily,
            topCategoryId = top?.first,
            topCategoryPaise = top?.second ?: 0,
            biggestPaise = biggest?.amountPaise ?: 0,
            biggestCategoryId = biggest?.categoryId,
            biggestDay = biggest?.let { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() },
            busiestDay = busiest?.let { start.plusDays(it.index.toLong()) },
            busiestPaise = busiest?.value ?: 0,
            noSpendDays = daily.take(elapsed).count { it <= 0 },
        )
    }

    /** This week (Monday to Sunday) up to [day], against the same days of the week before. */
    fun weekRecap(records: List<SpendRecord>, day: LocalDate, zone: ZoneId): Recap {
        val start = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return recap(records, start, 7, (day.toEpochDay() - start.toEpochDay() + 1).toInt(), start.minusWeeks(1), zone)
    }

    /** [month] up to [today] (or all of it, if it's over), against the same days of the month before. */
    fun monthRecap(records: List<SpendRecord>, month: YearMonth, today: LocalDate, zone: ZoneId): Recap {
        val elapsed = when {
            YearMonth.from(today) == month -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }
        val previous = month.minusMonths(1)
        return recap(records, month.atDay(1), month.lengthOfMonth(), minOf(elapsed, month.lengthOfMonth()), previous.atDay(1), zone)
            .let { r ->
                // A month longer than the one before is compared with the whole of that month.
                if (elapsed > previous.lengthOfMonth()) {
                    r.copy(previousPaise = netSpend(records.filter { it.occurredAt in TimeRange.month(previous, zone) }))
                } else r
            }
    }

    /** Net spending: expenses minus refunds. Transfers and income are ignored. */
    fun netSpend(records: Iterable<SpendRecord>): Long {
        var total = 0L
        for (r in records) when (r.type) {
            TransactionType.EXPENSE -> total = Math.addExact(total, r.amountPaise)
            TransactionType.REFUND -> total = Math.subtractExact(total, r.amountPaise)
            else -> Unit
        }
        return total
    }

    fun income(records: Iterable<SpendRecord>): Long =
        records.filter { it.type == TransactionType.INCOME }.fold(0L) { acc, r -> Math.addExact(acc, r.amountPaise) }

    /** Net spend per category (refunds reduce their category), positive entries only, largest first. */
    fun byCategory(records: Iterable<SpendRecord>): List<Pair<Long?, Long>> =
        records.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.REFUND }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> netSpend(list) }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }

    /** Gross expense amount per payment channel (refunds excluded), largest first. */
    fun byChannel(records: Iterable<SpendRecord>): List<Pair<PaymentChannel?, Long>> =
        records.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.channel }
            .mapValues { (_, list) -> list.fold(0L) { a, r -> Math.addExact(a, r.amountPaise) } }
            .toList()
            .sortedByDescending { it.second }

    /** Net spend per local day for [count] days starting at [start]. */
    fun dailyNetSpend(records: Iterable<SpendRecord>, start: LocalDate, count: Int, zone: ZoneId): List<Long> {
        val totals = LongArray(count)
        for (r in records) {
            if (r.type != TransactionType.EXPENSE && r.type != TransactionType.REFUND) continue
            val day = Instant.ofEpochMilli(r.occurredAt).atZone(zone).toLocalDate()
            val idx = (day.toEpochDay() - start.toEpochDay()).toInt()
            if (idx in 0 until count) {
                totals[idx] = if (r.type == TransactionType.EXPENSE) Math.addExact(totals[idx], r.amountPaise) else Math.subtractExact(totals[idx], r.amountPaise)
            }
        }
        return totals.toList()
    }

    data class MonthTotals(val month: YearMonth, val spentPaise: Long, val incomePaise: Long)

    fun monthlyTotals(records: Iterable<SpendRecord>, months: List<YearMonth>, zone: ZoneId): List<MonthTotals> {
        val byMonth = records.groupBy { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }
        return months.map { m ->
            val list = byMonth[m].orEmpty()
            MonthTotals(m, netSpend(list), income(list))
        }
    }

    /**
     * Average daily net spend for [month]. For the current month only elapsed days count;
     * future months return 0. Rounded half-up to the nearest paisa.
     */
    fun dailyAverage(spentPaise: Long, month: YearMonth, today: LocalDate): Long {
        val days = when {
            YearMonth.from(today) == month -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }
        if (days == 0) return 0
        val q = spentPaise / days
        val r = spentPaise % days
        return if (r * 2 >= days) q + 1 else q
    }

    /** Whole-percent change from [previous] to [current]; null when there is no baseline. */
    fun percentChange(current: Long, previous: Long): Int? {
        if (previous <= 0) return null
        return ((current - previous) * 100 / previous).toInt()
    }
}
