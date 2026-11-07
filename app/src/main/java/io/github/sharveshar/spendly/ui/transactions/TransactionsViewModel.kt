package io.github.sharveshar.spendly.ui.transactions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.data.repo.TransactionFilter
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.CategoryUi
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.common.Today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DatePreset(val label: String) {
    ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), LAST_3_MONTHS("Last 3 months"), THIS_YEAR("This year")
}

@Immutable
data class DayGroup(val date: LocalDate, val label: String, val netPaise: Long, val rows: List<TransactionRow>)

@Immutable
data class TransactionsUiState(
    val loading: Boolean = true,
    val groups: List<DayGroup> = emptyList(),
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val datePreset: DatePreset = DatePreset.ALL,
    val accounts: List<AccountUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val totalCount: Int = 0,
    val truncated: Boolean = false,
) {
    val hasFilters get() = query.isNotBlank() || type != null || accountId != null || categoryId != null || datePreset != DatePreset.ALL
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionsViewModel(
    repos: Repositories,
    private val handle: SavedStateHandle,
    /** When set, the list is locked to one account (account detail screen). */
    private val fixedAccountId: Long? = null,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val queryFlow = handle.getStateFlow(KEY_QUERY, "")
    private val typeFlow = handle.getStateFlow<String?>(KEY_TYPE, null)
    private val accountFlow = handle.getStateFlow<Long?>(KEY_ACCOUNT, fixedAccountId)
    private val categoryFlow = handle.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val presetFlow = handle.getStateFlow(KEY_PRESET, DatePreset.ALL.name)

    private data class Criteria(val query: String, val type: TransactionType?, val accountId: Long?, val categoryId: Long?, val preset: DatePreset, val today: LocalDate)

    private val criteria = combine(
        // Typed text is debounced; an empty query (first load, cleared search) applies immediately.
        queryFlow.debounce { if (it.isEmpty()) 0L else 180L }.distinctUntilChanged(),
        typeFlow, accountFlow, categoryFlow,
        combine(presetFlow, Today.date) { p, t -> p to t },
    ) { q, t, a, c, (p, today) ->
        Criteria(q, t?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }, a, c, runCatching { DatePreset.valueOf(p) }.getOrDefault(DatePreset.ALL), today)
    }

    val state: StateFlow<TransactionsUiState> = criteria.flatMapLatest { cr ->
        val (from, to) = range(cr.preset, cr.today)
        combine(
            repos.transactions.observeFiltered(TransactionFilter(cr.type, cr.accountId, cr.categoryId, from, to, cr.query), LIMIT),
            repos.accounts.observeWithBalances().map(AccountUi::from),
            repos.categories.observeAll(),
            queryFlow,
        ) { rows, accounts, cats, liveQuery ->
            TransactionsUiState(
                loading = false,
                groups = group(rows, cr.today),
                query = liveQuery,
                type = cr.type,
                accountId = cr.accountId,
                categoryId = cr.categoryId,
                datePreset = cr.preset,
                accounts = accounts,
                categories = cats.map(CategoryUi::from),
                totalCount = rows.size,
                truncated = rows.size >= LIMIT,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    /** Live search text for the field (not debounced). */
    val query: StateFlow<String> = queryFlow

    fun setQuery(q: String) { handle[KEY_QUERY] = q.take(60) }
    fun setType(t: TransactionType?) { handle[KEY_TYPE] = t?.name }
    fun setAccount(id: Long?) { if (fixedAccountId == null) handle[KEY_ACCOUNT] = id }
    fun setCategory(id: Long?) { handle[KEY_CATEGORY] = id }
    fun setPreset(p: DatePreset) { handle[KEY_PRESET] = p.name }
    fun clearFilters() {
        setQuery(""); setType(null); setAccount(null); setCategory(null); setPreset(DatePreset.ALL)
    }

    private fun range(p: DatePreset, today: LocalDate): Pair<Long, Long> {
        val z = zone()
        fun start(d: LocalDate) = d.atStartOfDay(z).toInstant().toEpochMilli()
        val month = YearMonth.from(today)
        return when (p) {
            DatePreset.ALL -> 0L to Long.MAX_VALUE
            DatePreset.THIS_MONTH -> start(month.atDay(1)) to start(month.plusMonths(1).atDay(1))
            DatePreset.LAST_MONTH -> start(month.minusMonths(1).atDay(1)) to start(month.atDay(1))
            DatePreset.LAST_3_MONTHS -> start(month.minusMonths(2).atDay(1)) to start(month.plusMonths(1).atDay(1))
            DatePreset.THIS_YEAR -> start(LocalDate.of(today.year, 1, 1)) to start(LocalDate.of(today.year + 1, 1, 1))
        }
    }

    private fun group(rows: List<TransactionRow>, today: LocalDate): List<DayGroup> {
        val z = zone()
        return rows.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(z).toLocalDate() }
            .map { (date, list) ->
                val net = list.sumOf {
                    when (it.type) {
                        TransactionType.EXPENSE -> -it.amountPaise
                        TransactionType.INCOME, TransactionType.REFUND -> it.amountPaise
                        TransactionType.TRANSFER -> 0L
                    }
                }
                DayGroup(date, Formats.relativeDay(date, today), net, list)
            }
    }

    private companion object {
        const val LIMIT = 2_000
        const val KEY_QUERY = "q"
        const val KEY_TYPE = "type"
        const val KEY_ACCOUNT = "account"
        const val KEY_CATEGORY = "category"
        const val KEY_PRESET = "preset"
    }
}
