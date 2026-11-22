// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.budgets

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.sharveshar.mnyboxd.Repositories
import io.github.sharveshar.mnyboxd.core.money.AmountInput
import io.github.sharveshar.mnyboxd.core.money.Money
import io.github.sharveshar.mnyboxd.data.repo.MonthBudget
import io.github.sharveshar.mnyboxd.data.repo.WriteResult
import io.github.sharveshar.mnyboxd.domain.insights.BudgetLevel
import io.github.sharveshar.mnyboxd.domain.insights.BudgetMath
import io.github.sharveshar.mnyboxd.domain.insights.BudgetStatus
import io.github.sharveshar.mnyboxd.domain.insights.BudgetView
import io.github.sharveshar.mnyboxd.domain.insights.BudgetWindows
import io.github.sharveshar.mnyboxd.domain.insights.DailySpend
import io.github.sharveshar.mnyboxd.domain.insights.Insights
import io.github.sharveshar.mnyboxd.domain.insights.TimeRange
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import io.github.sharveshar.mnyboxd.domain.model.CategoryKind
import io.github.sharveshar.mnyboxd.ui.common.CategoryUi
import io.github.sharveshar.mnyboxd.ui.common.Formats
import io.github.sharveshar.mnyboxd.ui.common.Today
import io.github.sharveshar.mnyboxd.ui.components.AmountField
import io.github.sharveshar.mnyboxd.ui.components.BudgetBar
import io.github.sharveshar.mnyboxd.ui.components.BudgetStrip
import io.github.sharveshar.mnyboxd.ui.components.CategoryIcons
import io.github.sharveshar.mnyboxd.ui.components.EmptyState
import io.github.sharveshar.mnyboxd.ui.components.IconBadge
import io.github.sharveshar.mnyboxd.ui.components.MoneyText
import io.github.sharveshar.mnyboxd.ui.components.MonthSwitcher
import io.github.sharveshar.mnyboxd.ui.components.OriginAnchor
import io.github.sharveshar.mnyboxd.ui.components.OriginPopup
import io.github.sharveshar.mnyboxd.ui.components.SectionCard
import io.github.sharveshar.mnyboxd.ui.components.SectionHeader
import io.github.sharveshar.mnyboxd.ui.components.SegmentedSelector
import io.github.sharveshar.mnyboxd.ui.components.SelectChip
import io.github.sharveshar.mnyboxd.ui.components.budgetColor
import io.github.sharveshar.mnyboxd.ui.components.originAnchor
import io.github.sharveshar.mnyboxd.ui.components.rememberOriginAnchor
import io.github.sharveshar.mnyboxd.ui.components.rememberStagger
import io.github.sharveshar.mnyboxd.ui.components.staggered
import io.github.sharveshar.mnyboxd.ui.theme.AmountStyle
import io.github.sharveshar.mnyboxd.ui.theme.Space
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A category's budget in the month shown. [custom] means it was set for this month only;
 * [usualPaise] and [usualPeriod] are the recurring budget, if there is one.
 */
@Immutable
data class CategoryBudgetUi(
    val category: CategoryUi,
    val view: BudgetView,
    val custom: Boolean = false,
    val usualPaise: Long? = null,
    val usualPeriod: BudgetPeriod? = null,
)

@Immutable
data class BudgetsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val isCurrentMonth: Boolean = true,
    /** The overall budget as it stands in the month shown: its day, week or month. */
    val overall: BudgetView? = null,
    /** The overall budget was set for this month only. */
    val overallCustom: Boolean = false,
    /** The recurring overall budget, if any (shown when this month has its own). */
    val overallUsual: Long? = null,
    val overallUsualPeriod: BudgetPeriod? = null,
    /** Budgets can be planned up to a year ahead. */
    val canGoNext: Boolean = true,
    val spent: Long = 0,
    val categoryBudgets: List<CategoryBudgetUi> = emptyList(),
    val unbudgeted: List<Pair<CategoryUi, Long>> = emptyList(),
    val availableCategories: List<CategoryUi> = emptyList(),
)

/** How much a budget needs looking at, for ordering: share used, or share of days or weeks over. */
private fun BudgetView.attention(): Int = when (this) {
    is BudgetView.Live -> status.percentUsed
    is BudgetView.Past -> if (track.isEmpty()) 0 else over * 100 / track.size
    is BudgetView.Upcoming -> -1
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val repos: Repositories,
    private val handle: SavedStateHandle,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val monthFlow = handle.getStateFlow(KEY_MONTH, YearMonth.now().toString())
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BudgetsUiState> = combine(monthFlow, Today.date) { m, t -> YearMonth.parse(m) to t }
        .flatMapLatest { (month, today) ->
            val current = YearMonth.from(today)
            // Weekly budgets reach into the weeks either side of the month.
            val around = BudgetWindows.around(month)
            val monthRange = TimeRange.month(month, zone())
            combine(
                repos.transactions.observeSpendRecords(around.range(zone())),
                repos.budgets.observeBook(),
                repos.categories.observeAll(),
            ) { records, book, cats ->
                val inMonth = records.filter { it.occurredAt in monthRange }
                val budgets = book.forMonth(month)
                val spent = Insights.netSpend(inMonth)
                val byCat = Insights.byCategory(inMonth).toMap()
                val catById = cats.associateBy { it.id }
                fun viewOf(b: MonthBudget): BudgetView {
                    val scoped = if (b.categoryId == null) records else records.filter { it.categoryId == b.categoryId }
                    return BudgetMath.view(b.period, b.amountPaise, month, today, DailySpend.of(scoped, around, zone()))
                }
                val overall = budgets.firstOrNull { it.categoryId == null }
                val catBudgets = budgets.filter { it.categoryId != null }.mapNotNull { b ->
                    val c = catById[b.categoryId] ?: return@mapNotNull null
                    CategoryBudgetUi(CategoryUi.from(c), viewOf(b), b.custom, b.usualPaise, b.usualPeriod)
                }.sortedByDescending { it.view.attention() }
                val budgetedIds = catBudgets.map { it.category.id }.toSet()
                BudgetsUiState(
                    loading = false,
                    month = month,
                    today = today,
                    isCurrentMonth = month == current,
                    overall = overall?.let(::viewOf),
                    overallCustom = overall?.custom == true,
                    overallUsual = overall?.usualPaise,
                    overallUsualPeriod = overall?.usualPeriod,
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
     * Saves a limit that starts over every [period]. A monthly one can be [onlyThisMonth]: just the
     * month on screen (a festival, a trip, a tight month). Otherwise it's the recurring budget, which
     * this month then follows too. Returns null on success or an error message.
     */
    suspend fun save(categoryId: Long?, text: String, period: BudgetPeriod, onlyThisMonth: Boolean): String? {
        val paise = Money.parseRupees(text) ?: return "Enter a valid amount"
        val r = if (onlyThisMonth && period == BudgetPeriod.MONTHLY) repos.budgets.setMonthLimit(month, categoryId, paise)
        else repos.budgets.setLimitFrom(month, categoryId, paise, period)
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
 * [period] is the budget's period as it stands; [custom]: the amount shown was set for this month
 * only; [usualPaise] and [usualPeriod]: the recurring budget.
 */
private data class EditTarget(
    val categoryId: Long?,
    val currentPaise: Long?,
    val isNew: Boolean,
    val anchor: OriginAnchor,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val custom: Boolean = false,
    val usualPaise: Long? = null,
    val usualPeriod: BudgetPeriod? = null,
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

    val stagger = rememberStagger()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.xs, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                MonthSwitcher(state.month.format(Formats.monthYear), viewModel::previousMonth, viewModel::nextMonth, canGoNext = state.canGoNext)
            }
        }
        item(key = "overall") {
            val o = state.overall
            OverallCard(
                state,
                Modifier.staggered(stagger, 0).padding(horizontal = Space.gutter, vertical = Space.md).originAnchor(overallAnchor),
            ) {
                open(
                    EditTarget(
                        categoryId = null,
                        currentPaise = o?.limitPaise,
                        isNew = false,
                        anchor = overallAnchor,
                        period = o?.period ?: BudgetPeriod.MONTHLY,
                        custom = state.overallCustom,
                        usualPaise = state.overallUsual,
                        usualPeriod = state.overallUsualPeriod,
                    ),
                )
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
                EmptyState(Icons.Rounded.Savings, "No category budgets", "Set limits for the categories you want to keep an eye on, like Food or Shopping: per day, per week or per month.")
            }
        }
        items(state.categoryBudgets, key = { it.category.id }) { b ->
            val anchor = rememberOriginAnchor()
            CategoryBudgetRow(b, state.month, Modifier.animateItem().originAnchor(anchor)) {
                open(EditTarget(b.category.id, b.view.limitPaise, false, anchor, b.view.period, b.custom, b.usualPaise, b.usualPeriod))
            }
        }
        if (state.unbudgeted.isNotEmpty()) {
            item(key = "unb-h") { SectionHeader("Other spending this month", Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) }
            items(state.unbudgeted, key = { "u" + it.first.id }) { (c, amt) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(amt), style = AmountStyle.compact, color = Mnyboxd.colors.muted)
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
            onSave = { cat, text, period, only -> viewModel.save(cat, text, period, only) },
            onUseUsual = { viewModel.useUsual(t.categoryId); shown = false },
            onRemove = { viewModel.removeUsual(t.categoryId); shown = false },
            onDismiss = { shown = false },
        )
    }
}

/** The overall budget: what's left today, this week or this month, or how a past month went. */
@Composable
private fun OverallCard(state: BudgetsUiState, modifier: Modifier, onClick: () -> Unit) {
    val o = state.overall
    val c = Mnyboxd.colors
    SectionCard(modifier, padding = Space.xl, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o?.let { BudgetWords.title(it.period) } ?: "Overall budget", style = MaterialTheme.typography.labelLarge, color = c.muted, modifier = Modifier.weight(1f))
            if (o != null) ScopeTag(state.overallCustom, o.period, state.month, state.overallUsual, state.overallUsualPeriod)
        }
        Spacer(Modifier.height(Space.xs))
        when (o) {
            null -> {
                Text("Not set", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Tap to set a limit for everything you spend: per day, per week or per month. You'll be warned at 80% and 100%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            is BudgetView.Live -> {
                val s = o.status
                val over = s.remainingPaise < 0
                val now = BudgetWords.nowSuffix(o.period)
                MoneyText(if (over) -s.remainingPaise else s.remainingPaise, MaterialTheme.typography.displaySmall, color = if (over) c.negative else MaterialTheme.colorScheme.onSurface)
                Text(if (over) "over budget$now" else "left to spend$now", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(Space.lg))
                BudgetBar(s.fraction, s.level, height = 8.dp)
                Spacer(Modifier.height(Space.sm))
                Row {
                    Text("${Money.format(s.spentPaise)} of ${Money.format(s.limitPaise)}", style = AmountStyle.compact, color = c.muted, modifier = Modifier.weight(1f))
                    LevelLabel(s)
                }
                // A monthly budget, this month: what's left, spread over the days to go.
                if (o.period == BudgetPeriod.MONTHLY && state.isCurrentMonth && !over) {
                    val daysLeft = state.month.lengthOfMonth() - state.today.dayOfMonth + 1
                    if (daysLeft > 1) {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            // Whole rupees, rounded down so following it never runs over.
                            "About ${Money.format(s.remainingPaise / daysLeft / 100 * 100)} a day for the $daysLeft days left",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    }
                }
                if (o.track.isNotEmpty()) {
                    Spacer(Modifier.height(Space.lg))
                    BudgetStrip(o.track)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "${o.kept} of ${BudgetWords.count(o.elapsed, o.period)} within budget so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }
            is BudgetView.Past -> {
                Text(
                    "${o.kept} of ${BudgetWords.count(o.track.size, o.period)}",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { contentDescription = "Within budget on ${o.kept} of ${BudgetWords.count(o.track.size, o.period)}" },
                )
                Text("within ${BudgetWords.perPeriod(o.limitPaise, o.period)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(Space.lg))
                BudgetStrip(o.track, height = 8.dp)
                Spacer(Modifier.height(Space.sm))
                OverLabel(o)
            }
            is BudgetView.Upcoming -> {
                MoneyText(o.limitPaise, MaterialTheme.typography.displaySmall)
                Text("a ${o.period.unit}. ${BudgetWords.rhythm(o.period)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun LevelLabel(s: BudgetStatus) {
    val text = when (s.level) {
        BudgetLevel.OK -> "${s.percentUsed}%"
        BudgetLevel.WARNING -> "${s.percentUsed}% · nearly there"
        BudgetLevel.EXCEEDED -> "${s.percentUsed}% · over"
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = if (s.level == BudgetLevel.OK) Mnyboxd.colors.muted else budgetColor(s.level))
}

/** "Never over", or "Over on 3 days" in the over-budget colour. */
@Composable
private fun OverLabel(p: BudgetView.Past) {
    Text(
        if (p.over == 0) "Never over" else "Over on ${BudgetWords.count(p.over, p.period)}",
        style = MaterialTheme.typography.labelMedium,
        color = if (p.over == 0) Mnyboxd.colors.muted else Mnyboxd.colors.negative,
    )
}

/** "Only November · usually ₹300 a day" for a one-month budget; "Every day", "Every week" or "Every month" otherwise. */
@Composable
private fun ScopeTag(custom: Boolean, period: BudgetPeriod, month: YearMonth, usualPaise: Long?, usualPeriod: BudgetPeriod?) {
    val text = if (custom) {
        "Only ${month.format(Formats.shortMonth)}" + (usualPaise?.let { " · usually ${BudgetWords.amount(it, usualPeriod ?: BudgetPeriod.MONTHLY, compact = true)}" } ?: "")
    } else BudgetWords.every(period)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (custom) Mnyboxd.colors.onSelectedFill else Mnyboxd.colors.muted,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (custom) Mnyboxd.colors.selectedFill else Mnyboxd.colors.cardElevated)
            .padding(horizontal = Space.sm, vertical = 3.dp),
    )
}

/** What a screen reader says for a category's budget. */
private fun describe(b: CategoryBudgetUi): String {
    val v = b.view
    val name = "${b.category.name} budget"
    return when (v) {
        is BudgetView.Live -> "$name, ${v.status.percentUsed} percent used${BudgetWords.nowSuffix(v.period)}, " +
            "${Money.format(v.status.spentPaise)} of ${Money.format(v.status.limitPaise)}"
        is BudgetView.Past -> "$name, ${BudgetWords.perPeriod(v.limitPaise, v.period)}, within it on ${v.kept} of ${BudgetWords.count(v.track.size, v.period)}"
        is BudgetView.Upcoming -> "$name, ${BudgetWords.perPeriod(v.limitPaise, v.period)}"
    }
}

@Composable
private fun CategoryBudgetRow(b: CategoryBudgetUi, month: YearMonth, modifier: Modifier, onClick: () -> Unit) {
    val color = Mnyboxd.colors.categoryPalette[b.category.colorIndex.coerceIn(0, 9)]
    val v = b.view
    SectionCard(
        modifier.padding(horizontal = Space.gutter, vertical = Space.xs).semantics(mergeDescendants = true) { contentDescription = describe(b) },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.forKey(b.category.iconKey), color, size = 36.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(b.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (v) {
                        is BudgetView.Live -> "${Money.format(v.status.spentPaise)} of ${Money.format(v.status.limitPaise)}${BudgetWords.nowSuffix(v.period)}"
                        else -> BudgetWords.perPeriod(v.limitPaise, v.period)
                    },
                    style = AmountStyle.compact,
                    color = Mnyboxd.colors.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                when (v) {
                    is BudgetView.Live -> LevelLabel(v.status)
                    is BudgetView.Past -> OverLabel(v)
                    is BudgetView.Upcoming -> Unit
                }
                if (b.custom) {
                    Spacer(Modifier.height(Space.xs))
                    ScopeTag(true, BudgetPeriod.MONTHLY, month, null, null)
                }
            }
        }
        when (v) {
            is BudgetView.Live -> {
                Spacer(Modifier.height(Space.md))
                BudgetBar(v.status.fraction, v.status.level)
            }
            is BudgetView.Past -> {
                Spacer(Modifier.height(Space.md))
                BudgetStrip(v.track)
            }
            is BudgetView.Upcoming -> Unit
        }
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
    onSave: suspend (Long?, String, BudgetPeriod, Boolean) -> String?,
    onUseUsual: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(target) { mutableStateOf(target.currentPaise?.let { AmountInput.fromPaise(it).text }.orEmpty()) }
    var chosen by remember(target) { mutableStateOf(target.categoryId) }
    var period by remember(target) { mutableStateOf(target.period) }
    var onlyThisMonth by remember(target) { mutableStateOf(target.custom) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val monthName = month.format(Formats.month)
    val monthly = period == BudgetPeriod.MONTHLY
    OriginPopup(visible = visible, anchor = target.anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.xxl).verticalScroll(rememberScrollState())) {
            Text(
                when {
                    target.isNew -> "New category budget"
                    target.categoryId == null -> "Overall budget"
                    else -> "${categoryName ?: "Category"} budget"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Space.lg))
            if (target.isNew) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = Mnyboxd.colors.muted)
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    categories.forEach { c ->
                        SelectChip(chosen == c.id, c.name, { chosen = c.id }, icon = CategoryIcons.forKey(c.iconKey), accent = Mnyboxd.colors.categoryPalette[c.colorIndex.coerceIn(0, 9)])
                    }
                }
                Spacer(Modifier.height(Space.lg))
            }
            // How often it starts over: whatever suits how you think about money.
            Text("Budget for each", style = MaterialTheme.typography.labelMedium, color = Mnyboxd.colors.muted)
            Spacer(Modifier.height(Space.sm))
            SegmentedSelector(
                BudgetPeriod.entries, period,
                { it.unit.replaceFirstChar(Char::uppercaseChar) },
                { period = it; error = null },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
            AmountField(
                text,
                { text = it; error = null },
                if (monthly && onlyThisMonth) "Limit for $monthName" else "Limit per ${period.unit}",
                error = error,
            )
            Spacer(Modifier.height(Space.sm))
            if (monthly) {
                // Special months (a festival, a trip, a wedding) get their own amount without
                // touching the usual budget.
                Spacer(Modifier.height(Space.xs))
                Text("Applies to", style = MaterialTheme.typography.labelMedium, color = Mnyboxd.colors.muted)
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
                        onlyThisMonth && target.usualPaise != null ->
                            "Other months keep the usual ${BudgetWords.amount(target.usualPaise, target.usualPeriod ?: BudgetPeriod.MONTHLY)}."
                        onlyThisMonth -> "Just for $monthName. Other months stay without this budget."
                        else -> "Repeats every month, including $monthName."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Mnyboxd.colors.muted,
                )
            } else {
                // A quick check that the amount is the one meant: ₹500 a day is ₹15,000 a month.
                val paise = Money.parseRupees(text)?.takeIf(Money::isValidAmount)
                Text(
                    BudgetWords.rhythm(period) +
                        (paise?.let { " About ${Money.format(BudgetMath.monthEquivalent(period, it, month))} in $monthName." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mnyboxd.colors.muted,
                )
            }
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    target.isNew || target.currentPaise == null -> Unit
                    target.custom && target.usualPaise != null -> TextButton(onClick = onUseUsual) { Text("Use usual") }
                    target.custom -> TextButton(onClick = onUseUsual) { Text("Remove", color = Mnyboxd.colors.negative) }
                    else -> TextButton(onClick = onRemove) { Text("Remove", color = Mnyboxd.colors.negative) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Space.sm))
                Button(onClick = {
                    if (target.isNew && chosen == null) { error = "Choose a category"; return@Button }
                    scope.launch {
                        val e = onSave(if (target.isNew) chosen else target.categoryId, text, period, onlyThisMonth && monthly)
                        if (e == null) onDismiss() else error = e
                    }
                }) { Text("Save") }
            }
        }
    }
}
