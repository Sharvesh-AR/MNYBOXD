package io.github.sharveshar.spendly.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.AmountInput
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.insights.BudgetStatus
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.ui.common.CategoryUi
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.common.Today
import io.github.sharveshar.spendly.ui.components.AmountField
import io.github.sharveshar.spendly.ui.components.BudgetBar
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.MonthSwitcher
import io.github.sharveshar.spendly.ui.components.MoneyText
import io.github.sharveshar.spendly.ui.components.OriginAnchor
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.SelectChip
import io.github.sharveshar.spendly.ui.components.SegmentedSelector
import io.github.sharveshar.spendly.ui.components.budgetColor
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.components.staggered
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

/**
 * A category's budget in the month shown. [custom] means it was set for this month only;
 * [usualPaise] is the recurring amount, if there is one.
 */
@Immutable
data class CategoryBudgetUi(val category: CategoryUi, val status: BudgetStatus, val custom: Boolean = false, val usualPaise: Long? = null)

@Immutable
data class BudgetsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val overall: BudgetStatus? = null,
    /** The overall budget was set for this month only. */
    val overallCustom: Boolean = false,
    /** The recurring overall budget, if any (shown when this month has its own). */
    val overallUsual: Long? = null,
    /** Budgets can be planned up to a year ahead. */
    val canGoNext: Boolean = true,
    val spent: Long = 0,
    val categoryBudgets: List<CategoryBudgetUi> = emptyList(),
    val unbudgeted: List<Pair<CategoryUi, Long>> = emptyList(),
    val availableCategories: List<CategoryUi> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(private val repos: Repositories, private val handle: SavedStateHandle) : ViewModel() {
    private val monthFlow = handle.getStateFlow(KEY_MONTH, YearMonth.now().toString())
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BudgetsUiState> = combine(monthFlow, Today.date) { m, t -> YearMonth.parse(m) to YearMonth.from(t) }
        .flatMapLatest { (month, current) ->
            combine(
                repos.transactions.observeSpendRecords(TimeRange.month(month, ZoneId.systemDefault())),
                repos.budgets.observeBook(),
                repos.categories.observeAll(),
            ) { records, book, cats ->
                val budgets = book.forMonth(month)
                val spent = Insights.netSpend(records)
                val byCat = Insights.byCategory(records).toMap()
                val catById = cats.associateBy { it.id }
                val overall = budgets.firstOrNull { it.categoryId == null }
                val catBudgets = budgets.filter { it.categoryId != null }.mapNotNull { b ->
                    val c = catById[b.categoryId] ?: return@mapNotNull null
                    CategoryBudgetUi(CategoryUi.from(c), BudgetStatus(b.amountPaise, byCat[c.id] ?: 0L), b.custom, b.usualPaise)
                }.sortedByDescending { it.status.percentUsed }
                val budgetedIds = catBudgets.map { it.category.id }.toSet()
                BudgetsUiState(
                    loading = false,
                    month = month,
                    isCurrentMonth = month == current,
                    overall = overall?.let { BudgetStatus(it.amountPaise, spent) },
                    overallCustom = overall?.custom == true,
                    overallUsual = overall?.usualPaise,
                    canGoNext = month.isBefore(current.plusMonths(MONTHS_AHEAD)),
                    spent = spent,
                    categoryBudgets = catBudgets,
                    unbudgeted = byCat.filterKeys { it != null && it !in budgetedIds }.mapNotNull { (id, amt) -> catById[id]?.let { CategoryUi.from(it) to amt } }.sortedByDescending { it.second },
                    availableCategories = cats.filter { it.kind == CategoryKind.EXPENSE && !it.archived && it.id !in budgetedIds }.map(CategoryUi::from),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousMonth() { handle[KEY_MONTH] = YearMonth.parse(monthFlow.value).minusMonths(1).toString() }
    fun nextMonth() { handle[KEY_MONTH] = YearMonth.parse(monthFlow.value).plusMonths(1).toString() }

    private val month: YearMonth get() = YearMonth.parse(monthFlow.value)

    /**
     * Saves a limit: for [onlyThisMonth], just the month on screen (a festival, a trip, a tight
     * month); otherwise the recurring one, which this month then follows too. Returns null on
     * success or an error message.
     */
    suspend fun save(categoryId: Long?, text: String, onlyThisMonth: Boolean): String? {
        val paise = Money.parseRupees(text) ?: return "Enter a valid amount"
        val r = if (onlyThisMonth) repos.budgets.setMonthLimit(month, categoryId, paise) else repos.budgets.setLimitFrom(month, categoryId, paise)
        return (r as? WriteResult.Rejected)?.reason
    }

    /** Drops this month's own amount: it follows the usual budget again (or has none). */
    fun useUsual(categoryId: Long?) { viewModelScope.launch { repos.budgets.clearMonthLimit(month, categoryId) } }

    /** Removes the recurring budget. Months with their own amount keep it. */
    fun removeUsual(categoryId: Long?) { viewModelScope.launch { repos.budgets.removeUsual(categoryId) } }

    private companion object {
        const val KEY_MONTH = "month"
        const val MONTHS_AHEAD = 12L
    }
}

/**
 * Which budget the editor popup is for. [categoryId] null = overall; [isNew] = choose a category.
 * [custom]: the amount shown was set for this month only; [usualPaise]: the recurring amount.
 */
private data class EditTarget(
    val categoryId: Long?,
    val currentPaise: Long?,
    val isNew: Boolean,
    val anchor: OriginAnchor,
    val custom: Boolean = false,
    val usualPaise: Long? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<EditTarget?>(null) }
    var shown by remember { mutableStateOf(false) }
    val overallAnchor = rememberOriginAnchor()
    val addAnchor = rememberOriginAnchor()
    fun open(t: EditTarget) { target = t; shown = true }

    val stagger = io.github.sharveshar.spendly.ui.components.rememberStagger()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.xs, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                MonthSwitcher(state.month.format(Formats.monthYear), viewModel::previousMonth, viewModel::nextMonth, canGoNext = state.canGoNext)
            }
        }
        item(key = "overall") {
            val o = state.overall
            SectionCard(
                Modifier.staggered(stagger, 0).padding(horizontal = Space.gutter, vertical = Space.md).originAnchor(overallAnchor),
                padding = Space.xl,
                onClick = { open(EditTarget(null, o?.limitPaise, false, overallAnchor, state.overallCustom, state.overallUsual)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted, modifier = Modifier.weight(1f))
                    if (o != null) ScopeTag(state.overallCustom, state.month, state.overallUsual)
                }
                Spacer(Modifier.height(Space.xs))
                if (o == null) {
                    Text("Not set", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Space.xs))
                    Text("Tap to set a limit for everything you spend in a month. You'll be warned at 80% and 100%.", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                } else {
                    val over = o.remainingPaise < 0
                    MoneyText(if (over) -o.remainingPaise else o.remainingPaise, MaterialTheme.typography.displaySmall, color = if (over) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
                    Text(if (over) "over budget" else "left to spend", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                    Spacer(Modifier.height(Space.lg))
                    BudgetBar(o.fraction, o.level, height = 8.dp)
                    Spacer(Modifier.height(Space.sm))
                    Row {
                        Text("${Money.format(o.spentPaise)} of ${Money.format(o.limitPaise)}", style = AmountStyle.compact, color = Spendly.colors.muted, modifier = Modifier.weight(1f))
                        LevelLabel(o)
                    }
                }
            }
        }
        item(key = "cat-h") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("By category", Modifier.weight(1f))
                if (state.availableCategories.isNotEmpty()) {
                    TextButton(onClick = { open(EditTarget(null, null, true, addAnchor)) }, modifier = Modifier.originAnchor(addAnchor)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(Space.xs))
                        Text("Add")
                    }
                }
            }
        }
        if (state.categoryBudgets.isEmpty() && !state.loading) {
            item(key = "cat-empty") {
                EmptyState(Icons.Rounded.Savings, "No category budgets", "Set limits for the categories you want to keep an eye on, like Food or Shopping.")
            }
        }
        items(state.categoryBudgets, key = { it.category.id }) { b ->
            val anchor = rememberOriginAnchor()
            CategoryBudgetRow(b, state.month, Modifier.animateItem().originAnchor(anchor)) {
                open(EditTarget(b.category.id, b.status.limitPaise, false, anchor, b.custom, b.usualPaise))
            }
        }
        if (state.unbudgeted.isNotEmpty()) {
            item(key = "unb-h") { SectionHeader("Other spending this month", Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) }
            items(state.unbudgeted, key = { "u" + it.first.id }) { (c, amt) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(amt), style = AmountStyle.compact, color = Spendly.colors.muted)
                }
            }
        }
    }

    target?.let { t ->
        BudgetEditor(
            visible = shown,
            target = t,
            month = state.month,
            categories = state.availableCategories,
            categoryName = state.categoryBudgets.firstOrNull { it.category.id == t.categoryId }?.category?.name,
            onSave = { cat, text, only -> viewModel.save(cat, text, only) },
            onUseUsual = { viewModel.useUsual(t.categoryId); shown = false },
            onRemove = { viewModel.removeUsual(t.categoryId); shown = false },
            onDismiss = { shown = false },
        )
    }
}

@Composable
private fun LevelLabel(s: BudgetStatus) {
    val text = when (s.level) {
        BudgetLevel.OK -> "${s.percentUsed}%"
        BudgetLevel.WARNING -> "${s.percentUsed}% · nearly there"
        BudgetLevel.EXCEEDED -> "${s.percentUsed}% · over"
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = if (s.level == BudgetLevel.OK) Spendly.colors.muted else budgetColor(s.level))
}

/** "Only November" for a one-month budget, "Every month" otherwise. */
@Composable
private fun ScopeTag(custom: Boolean, month: YearMonth, usualPaise: Long?) {
    val text = if (custom) "Only ${month.format(Formats.shortMonth)}" + (usualPaise?.let { " · usually ${Money.formatCompact(it)}" } ?: "") else "Every month"
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (custom) Spendly.colors.onSelectedFill else Spendly.colors.muted,
        maxLines = 1,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (custom) Spendly.colors.selectedFill else Spendly.colors.cardElevated)
            .padding(horizontal = Space.sm, vertical = 3.dp),
    )
}

@Composable
private fun CategoryBudgetRow(b: CategoryBudgetUi, month: YearMonth, modifier: Modifier, onClick: () -> Unit) {
    val color = Spendly.colors.categoryPalette[b.category.colorIndex.coerceIn(0, 9)]
    SectionCard(
        modifier.padding(horizontal = Space.gutter, vertical = Space.xs).semantics(mergeDescendants = true) {
            contentDescription = "${b.category.name} budget, ${b.status.percentUsed} percent used, ${Money.format(b.status.spentPaise)} of ${Money.format(b.status.limitPaise)}"
        },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.forKey(b.category.iconKey), color, size = 36.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(b.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Money.format(b.status.spentPaise)} of ${Money.format(b.status.limitPaise)}", style = AmountStyle.compact, color = Spendly.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                LevelLabel(b.status)
                if (b.custom) {
                    Spacer(Modifier.height(Space.xs))
                    ScopeTag(true, month, null)
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        BudgetBar(b.status.fraction, b.status.level)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditor(
    visible: Boolean,
    target: EditTarget,
    month: YearMonth,
    categories: List<CategoryUi>,
    categoryName: String?,
    onSave: suspend (Long?, String, Boolean) -> String?,
    onUseUsual: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(target) { mutableStateOf(target.currentPaise?.let { AmountInput.fromPaise(it).text }.orEmpty()) }
    var chosen by remember(target) { mutableStateOf(target.categoryId) }
    var onlyThisMonth by remember(target) { mutableStateOf(target.custom) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val monthName = month.format(Formats.month)
    OriginPopup(visible = visible, anchor = target.anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.xxl).verticalScroll(rememberScrollState())) {
            Text(
                when {
                    target.isNew -> "New category budget"
                    target.categoryId == null -> "Monthly budget"
                    else -> "${categoryName ?: "Category"} budget"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Space.lg))
            if (target.isNew) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    categories.forEach { c ->
                        SelectChip(chosen == c.id, c.name, { chosen = c.id }, icon = CategoryIcons.forKey(c.iconKey), accent = Spendly.colors.categoryPalette[c.colorIndex.coerceIn(0, 9)])
                    }
                }
                Spacer(Modifier.height(Space.lg))
            }
            AmountField(text, { text = it; error = null }, if (onlyThisMonth) "Limit for $monthName" else "Limit per month", error = error)
            Spacer(Modifier.height(Space.md))
            // Special months (a festival, a trip, a wedding) get their own amount without
            // touching the usual budget.
            Text("Applies to", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
            Spacer(Modifier.height(Space.sm))
            SegmentedSelector(
                listOf(false, true), onlyThisMonth,
                { only -> if (only) "Only $monthName" else "Every month" },
                { onlyThisMonth = it },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                when {
                    onlyThisMonth && target.usualPaise != null -> "Other months keep the usual ${Money.format(target.usualPaise)}."
                    onlyThisMonth -> "Just for $monthName. Other months stay without this budget."
                    else -> "Repeats every month, including $monthName."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Spendly.colors.muted,
            )
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    target.isNew || target.currentPaise == null -> Unit
                    target.custom && target.usualPaise != null -> TextButton(onClick = onUseUsual) { Text("Use usual") }
                    target.custom -> TextButton(onClick = onUseUsual) { Text("Remove", color = Spendly.colors.negative) }
                    else -> TextButton(onClick = onRemove) { Text("Remove", color = Spendly.colors.negative) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    if (target.isNew && chosen == null) { error = "Choose a category"; return@Button }
                    scope.launch {
                        val e = onSave(if (target.isNew) chosen else target.categoryId, text, onlyThisMonth)
                        if (e == null) onDismiss() else error = e
                    }
                }) { Text("Save") }
            }
        }
    }
}
