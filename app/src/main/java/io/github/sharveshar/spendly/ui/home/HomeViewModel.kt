package io.github.sharveshar.spendly.ui.home

import io.github.sharveshar.spendly.ui.common.netWorth
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.domain.insights.BudgetStatus
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.common.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId

/** What Home shows: your position first (balance, today, budget), then recent activity. */
@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    /**
     * Everything in your accounts minus what you owe on cards: the same net figure as the
     * Accounts tab. Debit cards share their bank's balance, so they aren't counted twice.
     */
    val balancePaise: Long = 0,
    val hasAccounts: Boolean = false,
    val spentThisMonth: Long = 0,
    val spentToday: Long = 0,
    val budget: BudgetStatus? = null,
    val week: List<Long> = List(7) { 0L },
    val weekLabels: List<String> = List(7) { "" },
    val recent: List<TransactionRow> = emptyList(),
    val transactionCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repos: Repositories, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {

    val state: StateFlow<HomeUiState> = Today.date.flatMapLatest { today ->
        val month = YearMonth.from(today)
        val weekStart = today.minusDays(6)
        val monthRange = TimeRange.month(month, zone())
        val start = minOf(monthRange.startMillis, TimeRange.day(weekStart, zone()).startMillis)
        val end = maxOf(monthRange.endMillis, TimeRange.day(today, zone()).endMillis)
        combine(
            repos.transactions.observeSpendRecords(TimeRange(start, end)),
            repos.accounts.observeWithBalances(),
            repos.budgets.observeAll(),
            repos.transactions.observeRecent(5),
            repos.transactions.observeCount(),
        ) { records, accountRows, budgets, recent, count ->
            val monthRecords = records.filter { it.occurredAt in monthRange }
            val spent = Insights.netSpend(monthRecords)
            val accounts = AccountUi.from(accountRows)
            HomeUiState(
                loading = false,
                monthLabel = today.format(Formats.month),
                balancePaise = accounts.netWorth().netPaise,
                hasAccounts = accounts.any { !it.archived },
                spentThisMonth = spent,
                spentToday = Insights.netSpend(records.filter { it.occurredAt in TimeRange.day(today, zone()) }),
                budget = budgets.firstOrNull { it.categoryId == null }?.let { BudgetStatus(it.amountPaise, spent) },
                week = Insights.dailyNetSpend(records, weekStart, 7, zone()),
                weekLabels = (0..6).map { weekStart.plusDays(it.toLong()).format(Formats.dayShort) },
                recent = recent,
                transactionCount = count,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
