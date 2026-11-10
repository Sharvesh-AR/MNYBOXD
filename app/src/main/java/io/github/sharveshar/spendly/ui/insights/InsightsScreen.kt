// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.Recap
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.ui.common.CategoryUi
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.common.Today
import io.github.sharveshar.spendly.ui.components.BarChart
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.DonutChart
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.MonthSwitcher
import io.github.sharveshar.spendly.ui.components.PairedBarChart
import io.github.sharveshar.spendly.ui.components.ProportionRow
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.Slice
import io.github.sharveshar.spendly.ui.components.compactAxisDescription
import io.github.sharveshar.spendly.ui.components.staggered
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Immutable
data class InsightsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val spent: Long = 0,
    val income: Long = 0,
    val previousSpent: Long = 0,
    val dailyAverage: Long = 0,
    val daily: List<Long> = emptyList(),
    val weeklyLabels: List<String> = emptyList(),
    val weekly: List<Long> = emptyList(),
    val trendLabels: List<String> = emptyList(),
    val trendSpent: List<Long> = emptyList(),
    val trendIncome: List<Long> = emptyList(),
    val categories: List<Pair<CategoryUi?, Long>> = emptyList(),
    val channels: List<Pair<PaymentChannel?, Long>> = emptyList(),
    val hasData: Boolean = false,
    /** The week up to today (or the month's last week) and the month, each against the period before. */
    val week: Recap? = null,
    val monthRecap: Recap? = null,
    val today: LocalDate = LocalDate.now(),
    val names: Map<Long, CategoryUi> = emptyMap(),
) {
    val change: Int? get() = Insights.percentChange(spent, previousSpent)
}

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(repos: Repositories, private val handle: SavedStateHandle) : ViewModel() {
    private val monthFlow = handle.getStateFlow("month", YearMonth.now().toString())

    val state: StateFlow<InsightsUiState> = combine(monthFlow, Today.date) { m, t -> YearMonth.parse(m) to t }
        .flatMapLatest { (month, today) ->
            val zone = ZoneId.systemDefault()
            val firstMonth = month.minusMonths(5)
            val monthEnd = month.atEndOfMonth()
            // 8 weeks ending with the week that contains the month's last relevant day.
            val lastDay = if (YearMonth.from(today) == month) today else monthEnd
            val weekEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val weekStart = weekEnd.minusWeeks(8).plusDays(1)
            val from = minOf(TimeRange.month(firstMonth, zone).startMillis, TimeRange.day(weekStart, zone).startMillis)
            val to = maxOf(TimeRange.month(month, zone).endMillis, TimeRange.day(weekEnd, zone).endMillis)
            combine(repos.transactions.observeSpendRecords(TimeRange(from, to)), repos.categories.observeAll()) { records, cats ->
                val range = TimeRange.month(month, zone)
                val prevRange = TimeRange.month(month.minusMonths(1), zone)
                val monthRecords = records.filter { it.occurredAt in range }
                val spent = Insights.netSpend(monthRecords)
                val months = (0..5).map { firstMonth.plusMonths(it.toLong()) }
                val trend = Insights.monthlyTotals(records, months, zone)
                val days = Insights.dailyNetSpend(records, weekStart, 56, zone)
                val catById = cats.associateBy { it.id }
                InsightsUiState(
                    loading = false,
                    month = month,
                    isCurrentMonth = YearMonth.from(today) == month,
                    spent = spent,
                    income = Insights.income(monthRecords),
                    previousSpent = Insights.netSpend(records.filter { it.occurredAt in prevRange }),
                    dailyAverage = Insights.dailyAverage(spent, month, today),
                    daily = Insights.dailyNetSpend(records, month.atDay(1), month.lengthOfMonth(), zone),
                    weekly = days.chunked(7).map { it.sum() },
                    weeklyLabels = (0 until 8).map { weekStart.plusWeeks(it.toLong()).let { d -> "${d.dayOfMonth}/${d.monthValue}" } },
                    trendLabels = months.map { it.format(Formats.shortMonth) },
                    trendSpent = trend.map { it.spentPaise.coerceAtLeast(0) },
                    trendIncome = trend.map { it.incomePaise },
                    categories = Insights.byCategory(monthRecords).map { (id, v) -> id?.let { catById[it] }?.let(CategoryUi::from) to v },
                    channels = Insights.byChannel(monthRecords),
                    hasData = monthRecords.isNotEmpty(),
                    week = Insights.weekRecap(records, lastDay, zone),
                    monthRecap = Insights.monthRecap(records, month, today, zone),
                    today = today,
                    names = cats.associate { it.id to CategoryUi.from(it) },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun previousMonth() { handle["month"] = YearMonth.parse(monthFlow.value).minusMonths(1).toString() }
    fun nextMonth() { handle["month"] = YearMonth.parse(monthFlow.value).plusMonths(1).toString() }
}

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stagger = io.github.sharveshar.spendly.ui.components.rememberStagger()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.xs, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("Insights", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                MonthSwitcher(state.month.format(Formats.monthYear), viewModel::previousMonth, viewModel::nextMonth, canGoNext = !state.isCurrentMonth)
            }
        }
        val week = state.week
        val monthRecap = state.monthRecap
        if (week != null && monthRecap != null) {
            item(key = "recap") {
                RecapPager(week, monthRecap, state.month, state.today, state.names, Modifier.staggered(stagger, 0).padding(top = Space.md, bottom = Space.sm))
            }
        }
        if (!state.loading && !state.hasData) {
            item(key = "empty") {
                EmptyState(Icons.Rounded.Insights, "No activity in ${state.month.format(Formats.month)}", "Insights are built from what you record. Add expenses and income to see trends here.")
            }
        }
        if (state.hasData) {
            item(key = "cats") {
                SectionCard(Modifier.staggered(stagger, 2).padding(horizontal = Space.gutter, vertical = Space.sm)) {
                    Text("By category", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a slice or a row to focus it", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                    Spacer(Modifier.height(Space.lg))
                    val total = state.categories.sumOf { it.second }
                    if (total > 0) {
                        CategoryBreakdown(state.categories, total, state.month)
                    } else {
                        Text("Refunds cancelled out this month's spending.", color = Spendly.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.channels.isNotEmpty()) {
                item(key = "channels") {
                    SectionCard(Modifier.staggered(stagger, 3).padding(horizontal = Space.gutter, vertical = Space.sm)) {
                        Text("By payment method", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Space.sm))
                        val total = state.channels.sumOf { it.second }
                        state.channels.forEach { (ch, v) -> ProportionRow(ch?.label ?: "Not specified", v, total, MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
        }
        item(key = "weekly") {
            SectionHeader("Weekly spending", Modifier.padding(horizontal = Space.gutter, vertical = Space.xs))
            SectionCard(Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
                BarChart(state.weekly, state.weeklyLabels, highlight = 7, height = 110.dp, description = compactAxisDescription("Weekly spending, weeks starting", state.weeklyLabels, state.weekly))
                Spacer(Modifier.height(Space.sm))
                Text("Weeks run Monday to Sunday; labels show each week's start.", style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
            }
        }
        item(key = "trend") {
            SectionHeader("Income vs spending", Modifier.padding(horizontal = Space.gutter, vertical = Space.xs))
            SectionCard(Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
                PairedBarChart(
                    state.trendSpent, state.trendIncome, state.trendLabels,
                    description = "Last six months. " + state.trendLabels.indices.joinToString { i -> "${state.trendLabels[i]}: spent ${Money.format(state.trendSpent[i])}, income ${Money.format(state.trendIncome[i])}" },
                )
            }
        }
    }
}

/**
 * The month's spending by category: a donut whose slices use the theme's validated chart colours
 * in order (largest first), so neighbouring slices are always easy to tell apart, and below it
 * every category with the same colour, its amount and share. The largest [CHART_SLOTS]
 * categories get their own colour; the rest share a grey "Everything else" slice.
 */
@Composable
private fun CategoryBreakdown(categories: List<Pair<CategoryUi?, Long>>, total: Long, month: YearMonth) {
    val colors = Spendly.colors.chartPalette
    val other = Spendly.colors.muted
    val haptics = LocalHapticFeedback.current
    var focused by rememberSaveable(month.toString()) { mutableStateOf<Int?>(null) }
    val slots = minOf(colors.size, CHART_SLOTS)
    // Only one leftover category: show it by name rather than as "Everything else".
    val own = if (categories.size == slots + 1) slots + 1 else slots
    val top = categories.take(own)
    val rest = categories.drop(own).sumOf { it.second }
    fun colorAt(i: Int) = if (i < slots) colors[i] else other
    val slices = top.mapIndexed { i, (c, v) -> Slice(c?.name ?: "Uncategorised", v, colorAt(i)) } +
        (if (rest > 0) listOf(Slice("Everything else", rest, other)) else emptyList())
    fun focus(i: Int?) {
        if (i != focused) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        focused = i
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        DonutChart(
            slices,
            centerLabel = "Spent",
            centerValue = Money.formatCompact(total),
            selected = focused?.takeIf { it in slices.indices },
            onSelect = ::focus,
        )
    }
    Spacer(Modifier.height(Space.lg))
    categories.forEachIndexed { i, (c, v) ->
        val slice = if (i < top.size) i else top.size
        val color = if (i < top.size) colorAt(i) else other
        ProportionRow(
            c?.name ?: "Uncategorised", v, total, color,
            leading = { IconBadge(CategoryIcons.forKey(c?.iconKey), color, size = 32.dp) },
            emphasised = focused == null || focused == slice,
            onClick = { focus(if (focused == slice) null else slice) },
        )
    }
}

/** Categories that get their own colour in the breakdown; the rest share "Everything else". */
private const val CHART_SLOTS = 6
