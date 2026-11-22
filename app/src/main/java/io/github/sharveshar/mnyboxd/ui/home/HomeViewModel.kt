// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.mnyboxd.Repositories
import io.github.sharveshar.mnyboxd.core.money.Money
import io.github.sharveshar.mnyboxd.data.db.TransactionRow
import io.github.sharveshar.mnyboxd.domain.insights.BudgetLevel
import io.github.sharveshar.mnyboxd.domain.insights.BudgetMath
import io.github.sharveshar.mnyboxd.domain.insights.BudgetStatus
import io.github.sharveshar.mnyboxd.domain.insights.BudgetView
import io.github.sharveshar.mnyboxd.domain.insights.BudgetWindows
import io.github.sharveshar.mnyboxd.domain.insights.DailySpend
import io.github.sharveshar.mnyboxd.domain.insights.Insights
import io.github.sharveshar.mnyboxd.domain.insights.Recap
import io.github.sharveshar.mnyboxd.domain.insights.TimeRange
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import io.github.sharveshar.mnyboxd.ui.budgets.BudgetWords
import io.github.sharveshar.mnyboxd.ui.common.AccountUi
import io.github.sharveshar.mnyboxd.ui.common.Today
import io.github.sharveshar.mnyboxd.ui.common.netWorth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.ZoneId

/** A budget that needs attention, for the bell on Home. */
@Immutable
data class HomeAlert(val title: String, val detail: String, val level: BudgetLevel)

/** What Home shows: your position first (balance, today, budget), then recent activity. */
@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    /** The month the budget card and the line show (the month picker), and the months on offer. */
    val month: YearMonth = YearMonth.now(),
    val months: List<YearMonth> = emptyList(),
    val isCurrentMonth: Boolean = true,
    /**
     * Everything in your accounts minus what you owe on cards: the same net figure as the
     * Accounts tab. Always today's figure, whatever month is picked.
     */
    val balancePaise: Long = 0,
    val hasAccounts: Boolean = false,
    val spentInMonth: Long = 0,
    val spentToday: Long = 0,
    /** The overall budget in the month picked: today, this week or that month, or how a past month went. */
    val budget: BudgetView? = null,
    /** Spending so far in the month, day by day (running total), for the line behind the balance. */
    val monthToDate: List<Long> = emptyList(),
    /** Budgets at 80% or more right now: today, this week or this month, as each one runs. */
    val alerts: List<HomeAlert> = emptyList(),
    /** This week so far against the same days last week. */
    val week: Recap? = null,
    val recent: List<TransactionRow> = emptyList(),
    val transactionCount: Int = 0,
)

/** How many months the month picker offers, counting this one. */
const val HOME_MONTHS = 12

/** Running total of [daily] amounts, never below zero (refunds can exceed a day's spending). */
fun runningTotal(daily: List<Long>): List<Long> {
    var sum = 0L
    return daily.map { sum = maxOf(0L, Math.addExact(sum, it)); sum }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repos: Repositories, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {
    /** Null means "this month", so the default moves on by itself when the month changes. */
    private val picked = MutableStateFlow<YearMonth?>(null)

    fun pickMonth(month: YearMonth) {
        picked.value = month.takeIf { it != YearMonth.from(Today.date.value) }
    }

    val state: StateFlow<HomeUiState> = combine(Today.date, picked) { today, p -> today to p }.flatMapLatest { (today, p) ->
        val current = YearMonth.from(today)
        val month = p?.takeIf { !it.isAfter(current) } ?: current
        val monthRange = TimeRange.month(month, zone())
        val todayRange = TimeRange.day(today, zone())
        // Weekly budgets reach into the weeks either side of a month.
        val pickedAround = BudgetWindows.around(month)
        val currentAround = BudgetWindows.around(current)
        // From the earliest of those and last week's Monday (for the week card).
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val from = minOf(pickedAround.range(zone()).startMillis, TimeRange.day(lastMonday, zone()).startMillis)
        val to = maxOf(currentAround.range(zone()).endMillis, todayRange.endMillis)
        combine(
            repos.transactions.observeSpendRecords(TimeRange(from, to)),
            repos.accounts.observeWithBalances(),
            combine(repos.budgets.observeBook(), repos.categories.observeAll()) { b, c -> b to c },
            repos.transactions.observeRecent(5),
            repos.transactions.observeCount(),
        ) { records, accountRows, (book, categories), recent, count ->
            val inMonth = records.filter { it.occurredAt in monthRange }
            val spent = Insights.netSpend(inMonth)
            val accounts = AccountUi.from(accountRows)
            val overall = book.overall(month)
            val days = if (month == current) today.dayOfMonth else month.lengthOfMonth()

            // Alerts are about now: each budget over the day, week or month we're in.
            val names = categories.associate { it.id to it.name }
            val alerts = book.forMonth(current).mapNotNull { b ->
                val window = BudgetWindows.of(b.period, today).range(zone())
                val inWindow = records.filter { it.occurredAt in window && (b.categoryId == null || it.categoryId == b.categoryId) }
                val s = BudgetMath.status(b.amountPaise, Insights.netSpend(inWindow))
                if (s.level == BudgetLevel.OK) null
                else alertFor(if (b.categoryId == null) BudgetWords.title(b.period) else "${names[b.categoryId] ?: "Category"} budget", b.period, s)
            }.sortedByDescending { it.level }

            HomeUiState(
                loading = false,
                month = month,
                months = (0 until HOME_MONTHS).map { current.minusMonths(it.toLong()) },
                isCurrentMonth = month == current,
                balancePaise = accounts.netWorth().netPaise,
                hasAccounts = accounts.any { !it.archived },
                spentInMonth = spent,
                spentToday = Insights.netSpend(records.filter { it.occurredAt in todayRange }),
                budget = overall?.let { b -> BudgetMath.view(b.period, b.amountPaise, month, today, DailySpend.of(records, pickedAround, zone())) },
                monthToDate = runningTotal(Insights.dailyNetSpend(inMonth, month.atDay(1), days, zone())),
                alerts = alerts,
                week = Insights.weekRecap(records, today, zone()),
                recent = recent,
                transactionCount = count,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** "Over by ₹40 today (108%)" or "85% used this week · ₹450 left". */
    private fun alertFor(title: String, period: BudgetPeriod, s: BudgetStatus): HomeAlert {
        val now = BudgetWords.nowSuffix(period)
        return HomeAlert(
            title = title,
            detail = if (s.remainingPaise < 0) "Over by ${Money.format(-s.remainingPaise)}$now (${s.percentUsed}%)"
            else "${s.percentUsed}% used$now · ${Money.format(s.remainingPaise)} left",
            level = s.level,
        )
    }

}
