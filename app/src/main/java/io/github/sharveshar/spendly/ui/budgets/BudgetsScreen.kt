package io.github.sharveshar.spendly.ui.budgets

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

@Immutable
data class CategoryBudgetUi(val budgetId: Long, val category: CategoryUi, val status: BudgetStatus)

@Immutable
data class BudgetsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val overall: BudgetStatus? = null,
    val overallId: Long? = null,
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
                repos.budgets.observeAll(),
                repos.categories.observeAll(),
            ) { records, budgets, cats ->
                val spent = Insights.netSpend(records)
                val byCat = Insights.byCategory(records).toMap()
                val catById = cats.associateBy { it.id }
                val overall = budgets.firstOrNull { it.categoryId == null }
                val catBudgets = budgets.filter { it.categoryId != null }.mapNotNull { b ->
                    val c = catById[b.categoryId] ?: return@mapNotNull null
                    CategoryBudgetUi(b.id, CategoryUi.from(c), BudgetStatus(b.amountPaise, byCat[c.id] ?: 0L))
                }.sortedByDescending { it.status.percentUsed }
                val budgetedIds = catBudgets.map { it.category.id }.toSet()
                BudgetsUiState(
                    loading = false,
                    month = month,
                    isCurrentMonth = month == current,
                    overall = overall?.let { BudgetStatus(it.amountPaise, spent) },
                    overallId = overall?.id,
                    spent = spent,
                    categoryBudgets = catBudgets,
                    unbudgeted = byCat.filterKeys { it != null && it !in budgetedIds }.mapNotNull { (id, amt) -> catById[id]?.let { CategoryUi.from(it) to amt } }.sortedByDescending { it.second },
                    availableCategories = cats.filter { it.kind == CategoryKind.EXPENSE && !it.archived && it.id !in budgetedIds }.map(CategoryUi::from),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousMonth() { handle[KEY_MONTH] = YearMonth.parse(monthFlow.value).minusMonths(1).toString() }
    fun nextMonth() { handle[KEY_MONTH] = YearMonth.parse(monthFlow.value).plusMonths(1).toString() }

    /** Returns null on success or an error message. */
    suspend fun save(categoryId: Long?, text: String): String? {
        val paise = Money.parseRupees(text) ?: return "Enter a valid amount"
        return when (val r = repos.budgets.setLimit(categoryId, paise)) {
            is WriteResult.Ok -> null
            is WriteResult.Rejected -> r.reason
        }
    }

    fun remove(id: Long) { viewModelScope.launch { repos.budgets.remove(id) } }

    private companion object { const val KEY_MONTH = "month" }
}

/** Which budget the editor popup is for. [categoryId] null = overall; [isNew] = choose a category. */
private data class EditTarget(val categoryId: Long?, val budgetId: Long?, val currentPaise: Long?, val isNew: Boolean, val anchor: OriginAnchor)

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
                MonthSwitcher(state.month.format(Formats.monthYear), viewModel::previousMonth, viewModel::nextMonth, canGoNext = !state.isCurrentMonth)
            }
        }
        item(key = "overall") {
            val o = state.overall
            SectionCard(
                Modifier.staggered(stagger, 0).padding(horizontal = Space.gutter, vertical = Space.md).originAnchor(overallAnchor),
                padding = Space.xl,
                onClick = { open(EditTarget(null, state.overallId, o?.limitPaise, false, overallAnchor)) },
            ) {
                Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted)
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
                    TextButton(onClick = { open(EditTarget(null, null, null, true, addAnchor)) }, modifier = Modifier.originAnchor(addAnchor)) {
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
        items(state.categoryBudgets, key = { it.budgetId }) { b ->
            val anchor = rememberOriginAnchor()
            CategoryBudgetRow(b, Modifier.animateItem().originAnchor(anchor)) {
                open(EditTarget(b.category.id, b.budgetId, b.status.limitPaise, false, anchor))
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
            categories = state.availableCategories,
            categoryName = state.categoryBudgets.firstOrNull { it.category.id == t.categoryId }?.category?.name,
            onSave = { cat, text -> viewModel.save(cat, text) },
            onRemove = { id -> viewModel.remove(id); shown = false },
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

@Composable
private fun CategoryBudgetRow(b: CategoryBudgetUi, modifier: Modifier, onClick: () -> Unit) {
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
            LevelLabel(b.status)
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
    categories: List<CategoryUi>,
    categoryName: String?,
    onSave: suspend (Long?, String) -> String?,
    onRemove: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(target) { mutableStateOf(target.currentPaise?.let { AmountInput.fromPaise(it).text }.orEmpty()) }
    var chosen by remember(target) { mutableStateOf(target.categoryId) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
            AmountField(text, { text = it; error = null }, "Limit per month", error = error)
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (target.budgetId != null) {
                    TextButton(onClick = { onRemove(target.budgetId) }) { Text("Remove", color = Spendly.colors.negative) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    if (target.isNew && chosen == null) { error = "Choose a category"; return@Button }
                    scope.launch {
                        val e = onSave(if (target.isNew) chosen else target.categoryId, text)
                        if (e == null) onDismiss() else error = e
                    }
                }) { Text("Save") }
            }
        }
    }
}
