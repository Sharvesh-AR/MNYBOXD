// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.widget

import io.github.sharveshar.mnyboxd.Repositories
import io.github.sharveshar.mnyboxd.domain.insights.BudgetWindows
import io.github.sharveshar.mnyboxd.domain.insights.Insights
import io.github.sharveshar.mnyboxd.domain.insights.SpendRecord
import io.github.sharveshar.mnyboxd.domain.insights.TimeRange
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the home-screen widgets show, computed from the database with the app's own rules
 * (the same net-spend and income calculations as the dashboard). Widgets never store
 * their own copy of financial data: they render a fresh snapshot each time they update.
 */
data class WidgetSnapshot(
    val today: LocalDate,
    val spentMonthPaise: Long,
    val incomeMonthPaise: Long,
    val spentTodayPaise: Long,
    /** The overall budget's limit, or null if none is set. */
    val budgetPaise: Long?,
    /** Net spending for each of the last 7 days, oldest first, ending today. */
    val week: List<Long>,
    val hasAnyTransactions: Boolean,
    /** How often the overall budget starts over. */
    val budgetPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,
    /** Net spending in the overall budget's current day, week or month (never below zero). */
    val budgetSpentPaise: Long = maxOf(0L, spentMonthPaise),
) {
    val monthLabel: String get() = today.format(MONTH)
    val budgetRemainingPaise: Long? get() = budgetPaise?.let { it - budgetSpentPaise }
    val budgetFraction: Float? get() = budgetPaise?.takeIf { it > 0 }?.let { (budgetSpentPaise.toDouble() / it).toFloat().coerceAtLeast(0f) }
    val budgetPercent: Int? get() = budgetFraction?.let { (it * 100).toInt() }
    val weekTotalPaise: Long get() = week.sum()
    val weekDays: List<LocalDate> get() = (6 downTo 0).map { today.minusDays(it.toLong()) }

    /** Index into [week] of the biggest spending day, or null when nothing was spent. */
    val highestDayIndex: Int? get() = week.indices.maxByOrNull { week[it] }?.takeIf { week[it] > 0 }

    fun dayLabel(index: Int): String = weekDays[index].format(DAY)

    companion object {
        private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
        private val DAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

        /** Pure assembly from query results; [build] is what tests exercise. */
        fun build(
            today: LocalDate,
            zone: ZoneId,
            records: List<SpendRecord>,
            overallBudgetPaise: Long?,
            hasAnyTransactions: Boolean,
            budgetPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,
        ): WidgetSnapshot {
            val month = TimeRange.month(YearMonth.from(today), zone)
            val monthRecords = records.filter { it.occurredAt in month }
            val todayRange = TimeRange.day(today, zone)
            // The overall budget runs over today, this week or this month.
            val budgetRange = BudgetWindows.of(budgetPeriod, today).range(zone)
            return WidgetSnapshot(
                today = today,
                spentMonthPaise = Insights.netSpend(monthRecords),
                incomeMonthPaise = Insights.income(monthRecords),
                spentTodayPaise = Insights.netSpend(records.filter { it.occurredAt in todayRange }),
                budgetPaise = overallBudgetPaise,
                week = Insights.dailyNetSpend(records, today.minusDays(6), 7, zone),
                hasAnyTransactions = hasAnyTransactions,
                budgetPeriod = budgetPeriod,
                budgetSpentPaise = maxOf(0L, Insights.netSpend(records.filter { it.occurredAt in budgetRange })),
            )
        }

        /** Reads what the widgets need. Call only when the database is open. */
        suspend fun load(repos: Repositories, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): WidgetSnapshot {
            val month = TimeRange.month(YearMonth.from(today), zone)
            val weekStart = TimeRange.day(today.minusDays(6), zone)
            val from = minOf(month.startMillis, weekStart.startMillis)
            val to = maxOf(month.endMillis, TimeRange.day(today, zone).endMillis)
            // This week's Monday is never before today minus six days, so a weekly budget is covered too.
            val records = repos.transactions.observeSpendRecords(TimeRange(from, to)).first()
            val budget = repos.budgets.book().overall(YearMonth.from(today))
            val count = repos.transactions.observeCount().first()
            return build(today, zone, records, budget?.amountPaise, count > 0, budget?.period ?: BudgetPeriod.MONTHLY)
        }
    }
}
