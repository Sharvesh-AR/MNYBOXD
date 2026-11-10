// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.widget

import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.AccountWithBalance
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.SpendRecord
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.domain.ledger.BalanceCalculator
import io.github.sharveshar.spendly.domain.model.AccountType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One account as a widget shows it. For credit cards [amountPaise] is the amount owed. */
data class WidgetAccount(val id: Long, val name: String, val type: AccountType, val amountPaise: Long) {
    val isCard get() = type == AccountType.CREDIT_CARD
}

/**
 * Everything the home-screen widgets show, computed from the database with the app's own rules
 * (the same net-spend, income and balance calculations as the dashboard). Widgets never store
 * their own copy of financial data: they render a fresh snapshot each time they update.
 */
data class WidgetSnapshot(
    val today: LocalDate,
    val spentMonthPaise: Long,
    val incomeMonthPaise: Long,
    val spentTodayPaise: Long,
    /** Expenses, income and refunds dated this month (transfers aren't spending). */
    val transactionsThisMonth: Int,
    /** Overall monthly budget, or null if none is set. */
    val budgetPaise: Long?,
    /** Net spending for each of the last 7 days, oldest first, ending today. */
    val week: List<Long>,
    /** Money you have: banks, cash and wallets. Never includes card debt. */
    val availablePaise: Long,
    /** What's owed on credit cards, shown separately. */
    val cardsOwedPaise: Long,
    /** Active accounts in display order (debit cards are excluded; they spend from a bank). */
    val accounts: List<WidgetAccount>,
    val hasAnyTransactions: Boolean,
) {
    val monthLabel: String get() = today.format(MONTH)
    val budgetRemainingPaise: Long? get() = budgetPaise?.let { it - spentMonthPaise }
    val budgetFraction: Float? get() = budgetPaise?.takeIf { it > 0 }?.let { (spentMonthPaise.toDouble() / it).toFloat().coerceAtLeast(0f) }
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
            accounts: List<AccountWithBalance>,
            overallBudgetPaise: Long?,
            hasAnyTransactions: Boolean,
        ): WidgetSnapshot {
            val month = TimeRange.month(YearMonth.from(today), zone)
            val monthRecords = records.filter { it.occurredAt in month }
            val todayRange = TimeRange.day(today, zone)
            val active = accounts.filter { !it.account.archived }
            val shown = active
                .filter { it.account.type != AccountType.DEBIT_CARD }
                .sortedWith(compareBy({ it.account.type == AccountType.CREDIT_CARD }, { it.account.sortOrder }, { it.account.id }))
                .map {
                    val amount = if (it.account.type == AccountType.CREDIT_CARD) BalanceCalculator.outstanding(it.balancePaise) else it.balancePaise
                    WidgetAccount(it.account.id, it.account.name, it.account.type, amount)
                }
            return WidgetSnapshot(
                today = today,
                spentMonthPaise = Insights.netSpend(monthRecords),
                incomeMonthPaise = Insights.income(monthRecords),
                spentTodayPaise = Insights.netSpend(records.filter { it.occurredAt in todayRange }),
                transactionsThisMonth = monthRecords.size,
                budgetPaise = overallBudgetPaise,
                week = Insights.dailyNetSpend(records, today.minusDays(6), 7, zone),
                availablePaise = active.filter { it.account.type.holdsBalance && !it.account.type.isLiability }.sumOf { it.balancePaise },
                cardsOwedPaise = active.filter { it.account.type.isLiability }.sumOf { BalanceCalculator.outstanding(it.balancePaise) },
                accounts = shown,
                hasAnyTransactions = hasAnyTransactions,
            )
        }

        /** Reads what the widgets need. Call only when the database is open. */
        suspend fun load(repos: Repositories, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): WidgetSnapshot {
            val month = TimeRange.month(YearMonth.from(today), zone)
            val weekStart = TimeRange.day(today.minusDays(6), zone)
            val from = minOf(month.startMillis, weekStart.startMillis)
            val to = maxOf(month.endMillis, TimeRange.day(today, zone).endMillis)
            val records = repos.transactions.observeSpendRecords(TimeRange(from, to)).first()
            val accounts = repos.accounts.observeWithBalances().first()
            val budget = repos.budgets.book().overall(YearMonth.from(today))?.amountPaise
            val count = repos.transactions.observeCount().first()
            return build(today, zone, records, accounts, budget, count > 0)
        }
    }
}
