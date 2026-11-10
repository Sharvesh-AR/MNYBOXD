// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.insights

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.insights.Recap
import io.github.sharveshar.spendly.ui.common.CategoryUi
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.components.AnimatedMoney
import io.github.sharveshar.spendly.ui.components.RecapBars
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val weekdayShort = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
private val weekLabels = listOf("M", "T", "W", "T", "F", "S", "S")

/** "↓ 12% less than last week" and its colour: spending less is good news. */
@Composable
private fun changeLine(change: Int?, against: String): Pair<String, Color> {
    val c = Spendly.colors
    return when {
        change == null -> "Nothing to compare with $against yet" to c.muted
        change > 0 -> "↑ $change% more than $against" to c.negative
        change < 0 -> "↓ ${-change}% less than $against" to c.positive
        else -> "Same as $against" to c.muted
    }
}

/** Rounded to the nearest rupee: an average doesn't need paise. */
private fun wholeRupees(paise: Long): Long = Math.floorDiv(paise + 50, 100) * 100

/** One labelled figure in a recap. */
@Composable
private fun Fact(label: String, value: String, detail: String?, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Facts in two columns. */
@Composable
private fun FactGrid(facts: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                row.forEach { it(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecapHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
}

@Composable
private fun ColumnScope.RecapAmount(recap: Recap, against: String) {
    Spacer(Modifier.height(Space.md))
    AnimatedMoney(recap.spentPaise.coerceAtLeast(0), MaterialTheme.typography.displaySmall, countUpOnFirstShow = true)
    val (line, color) = changeLine(recap.change, against)
    Text(line, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun topCategoryFact(recap: Recap, names: Map<Long, CategoryUi>): Triple<String, String, String?> =
    Triple("Top category", recap.topCategoryId?.let { names[it]?.name } ?: "Uncategorised", "${Money.format(recap.topCategoryPaise)} · ${recap.topShare}%")

private fun biggestFact(recap: Recap, names: Map<Long, CategoryUi>, dayFormat: DateTimeFormatter): Triple<String, String, String?> =
    Triple(
        "Biggest spend",
        Money.format(recap.biggestPaise),
        listOfNotNull(recap.biggestCategoryId?.let { names[it]?.name }, recap.biggestDay?.format(dayFormat)).joinToString(" · ").ifEmpty { null },
    )

/** This week (or the last week of a past month), day by day, with what stood out. */
@Composable
fun WeekRecapCard(recap: Recap, today: LocalDate, names: Map<Long, CategoryUi>, modifier: Modifier = Modifier) {
    val current = today in recap.start..recap.start.plusDays(6)
    val end = recap.start.plusDays(6)
    SectionCard(modifier, padding = Space.xl) {
        RecapHeader(
            if (current) "This week" else "Week of ${recap.start.format(dayMonth)}",
            "${recap.start.format(dayMonth)} – ${end.format(dayMonth)}",
        )
        RecapAmount(recap, if (current) "last week, same days" else "the week before")
        Spacer(Modifier.height(Space.lg))
        RecapBars(
            recap.daily,
            elapsed = recap.elapsedDays,
            highlight = if (current) recap.elapsedDays - 1 else null,
            labels = weekLabels,
            height = 64.dp,
        )
        Spacer(Modifier.height(Space.lg))
        if (!recap.hasSpending) {
            Text(
                if (current) "Nothing spent this week so far. Keep it going." else "No spending recorded that week.",
                style = MaterialTheme.typography.bodyMedium,
                color = Spendly.colors.muted,
            )
        } else {
            val top = topCategoryFact(recap, names)
            val big = biggestFact(recap, names, weekdayShort)
            FactGrid(listOf(
                { m -> Fact(top.first, top.second, top.third, m) },
                { m -> Fact(big.first, big.second, big.third, m) },
                { m -> Fact("Busiest day", recap.busiestDay?.format(weekday) ?: "—", recap.busiestPaise.takeIf { it > 0 }?.let(Money::format), m) },
                { m -> Fact("No-spend days", "${recap.noSpendDays} of ${recap.elapsedDays}", if (recap.noSpendDays > 0) "days with nothing spent" else "spent every day", m) },
            ))
        }
    }
}

/** A month at a glance: what went out, what came in, and what stood out. */
@Composable
fun MonthRecapCard(recap: Recap, month: YearMonth, today: LocalDate, names: Map<Long, CategoryUi>, modifier: Modifier = Modifier) {
    val current = YearMonth.from(today) == month
    val previous = month.minusMonths(1).format(Formats.month)
    SectionCard(modifier, padding = Space.xl) {
        RecapHeader(
            if (current) "${month.format(Formats.month)} so far" else "${month.format(Formats.month)} recap",
            if (current) "${recap.elapsedDays} of ${recap.totalDays} days" else month.format(Formats.monthYear),
        )
        RecapAmount(recap, if (current) "the same days of $previous" else previous)
        Spacer(Modifier.height(Space.lg))
        val todayIndex = if (current) recap.elapsedDays - 1 else null
        RecapBars(
            recap.daily,
            elapsed = recap.elapsedDays,
            highlight = todayIndex,
            // Every week's start, and today, so a bar is easy to place.
            labels = recap.daily.indices.map { i -> if (i == todayIndex || (i % 7 == 0 && (todayIndex == null || kotlin.math.abs(i - todayIndex) > 2))) "${i + 1}" else "" },
            height = 64.dp,
        )
        Spacer(Modifier.height(Space.lg))
        val saved = recap.incomePaise - recap.spentPaise
        val facts = buildList<@Composable (Modifier) -> Unit> {
            if (recap.hasSpending) {
                val top = topCategoryFact(recap, names)
                val big = biggestFact(recap, names, dayMonth)
                add { m -> Fact(top.first, top.second, top.third, m) }
                add { m -> Fact(big.first, big.second, big.third, m) }
            }
            add { m -> Fact("Daily average", Money.format(wholeRupees(recap.dailyAverage)), "over ${recap.elapsedDays} days", m) }
            add { m -> Fact("No-spend days", "${recap.noSpendDays}", "of ${recap.elapsedDays} days", m) }
            add { m -> Fact("Income", Money.format(recap.incomePaise), null, m, valueColor = if (recap.incomePaise > 0) Spendly.colors.positive else MaterialTheme.colorScheme.onSurface) }
            add { m -> Fact(if (saved < 0) "Overspent" else "Saved", Money.format(kotlin.math.abs(saved)), if (recap.incomePaise > 0) "income minus spending" else null, m, valueColor = if (saved < 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface) }
        }
        FactGrid(facts)
    }
}

/**
 * The week and month recaps side by side: swipe between them. Pages lean back slightly as they
 * slide away, and dots show where you are.
 */
@Composable
fun RecapPager(week: Recap, month: Recap, monthOf: YearMonth, today: LocalDate, names: Map<Long, CategoryUi>, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { 2 }
    Column(modifier) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Space.gutter),
            pageSpacing = Space.md,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val m = Modifier.graphicsLayer {
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val s = lerp(1f, 0.94f, off)
                scaleX = s
                scaleY = s
                alpha = lerp(1f, 0.55f, off)
            }
            if (page == 0) WeekRecapCard(week, today, names, m) else MonthRecapCard(month, monthOf, today, names, m)
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Recap ${pager.currentPage + 1} of 2" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 18.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
                Box(
                    Modifier
                        .height(6.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) MaterialTheme.colorScheme.onSurface else Spendly.colors.chartMuted),
                )
            }
        }
    }
}

/** Home's small "This week" card: the total, how it compares, and the days so far. Opens Insights. */
@Composable
fun WeekGlanceCard(recap: Recap, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                AnimatedMoney(recap.spentPaise.coerceAtLeast(0), MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                val (line, color) = changeLine(recap.change, "last week")
                Text(line, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.md))
            RecapBars(recap.daily, elapsed = recap.elapsedDays, highlight = recap.elapsedDays - 1, modifier = Modifier.width(112.dp), height = 44.dp)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Spendly.colors.muted, modifier = Modifier.padding(start = Space.xs).size(20.dp))
        }
    }
}

