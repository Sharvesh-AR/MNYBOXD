package io.github.sharveshar.spendly.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.insights.BudgetStatus
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.Today
import io.github.sharveshar.spendly.ui.common.netWorth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
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
    val budget: BudgetStatus? = null,
    /** Spending so far in the month, day by day (running total), for the line behind the balance. */
    val monthToDate: List<Long> = emptyList(),
    /** Budgets at 80% or more this month. */
    val alerts: List<HomeAlert> = emptyList(),
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
        val currentRange = TimeRange.month(current, zone())
        val todayRange = TimeRange.day(today, zone())
        combine(
            repos.transactions.observeSpendRecords(TimeRange(monthRange.startMillis, maxOf(currentRange.endMillis, todayRange.endMillis))),
            repos.accounts.observeWithBalances(),
            combine(repos.budgets.observeAll(), repos.categories.observeAll()) { b, c -> b to c },
            repos.transactions.observeRecent(5),
            repos.transactions.observeCount(),
        ) { records, accountRows, (budgets, categories), recent, count ->
            val inMonth = records.filter { it.occurredAt in monthRange }
            val inCurrent = records.filter { it.occurredAt in currentRange }
            val spent = Insights.netSpend(inMonth)
            val accounts = AccountUi.from(accountRows)
            val overall = budgets.firstOrNull { it.categoryId == null }
            val days = if (month == current) today.dayOfMonth else month.lengthOfMonth()

            // Alerts are about now: this month's budgets at 80% or more.
            val names = categories.associate { it.id to it.name }
            val byCategory = Insights.byCategory(inCurrent).toMap()
            val alerts = buildList {
                overall?.let { b ->
                    val s = BudgetStatus(b.amountPaise, Insights.netSpend(inCurrent))
                    if (s.level != BudgetLevel.OK) add(alertFor("Monthly budget", s))
                }
                budgets.filter { it.categoryId != null }.forEach { b ->
                    val s = BudgetStatus(b.amountPaise, byCategory[b.categoryId] ?: 0L)
                    if (s.level != BudgetLevel.OK) add(alertFor("${names[b.categoryId] ?: "Category"} budget", s))
                }
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
                budget = overall?.let { BudgetStatus(it.amountPaise, spent) },
                monthToDate = runningTotal(Insights.dailyNetSpend(inMonth, month.atDay(1), days, zone())),
                alerts = alerts,
                recent = recent,
                transactionCount = count,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun alertFor(title: String, s: BudgetStatus) = HomeAlert(
        title = title,
        detail = if (s.remainingPaise < 0) "Over by ${io.github.sharveshar.spendly.core.money.Money.format(-s.remainingPaise)} (${s.percentUsed}%)"
        else "${s.percentUsed}% used · ${io.github.sharveshar.spendly.core.money.Money.format(s.remainingPaise)} left",
        level = s.level,
    )

}
