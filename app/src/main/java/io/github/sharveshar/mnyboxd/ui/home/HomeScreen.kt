// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.mnyboxd.core.money.Money
import io.github.sharveshar.mnyboxd.domain.insights.BudgetLevel
import io.github.sharveshar.mnyboxd.domain.insights.BudgetView
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import io.github.sharveshar.mnyboxd.ui.budgets.BudgetWords
import io.github.sharveshar.mnyboxd.ui.common.Formats
import io.github.sharveshar.mnyboxd.ui.common.Today
import io.github.sharveshar.mnyboxd.ui.components.AddButtonPlace
import io.github.sharveshar.mnyboxd.ui.components.AmountPill
import io.github.sharveshar.mnyboxd.ui.components.BudgetBar
import io.github.sharveshar.mnyboxd.ui.components.BudgetStrip
import io.github.sharveshar.mnyboxd.ui.components.EmptyState
import io.github.sharveshar.mnyboxd.ui.components.FlowLine
import io.github.sharveshar.mnyboxd.ui.components.OriginPopup
import io.github.sharveshar.mnyboxd.ui.components.RollingAmount
import io.github.sharveshar.mnyboxd.ui.components.SectionCard
import io.github.sharveshar.mnyboxd.ui.components.SectionHeader
import io.github.sharveshar.mnyboxd.ui.insights.WeekGlanceCard
import io.github.sharveshar.mnyboxd.ui.components.TransactionListItem
import io.github.sharveshar.mnyboxd.ui.components.originAnchor
import io.github.sharveshar.mnyboxd.ui.components.pressScale
import io.github.sharveshar.mnyboxd.ui.components.rememberHeld
import io.github.sharveshar.mnyboxd.ui.components.rememberOriginAnchor
import io.github.sharveshar.mnyboxd.ui.components.rememberStagger
import io.github.sharveshar.mnyboxd.ui.components.staggered
import io.github.sharveshar.mnyboxd.ui.entry.LocalSheetMotion
import io.github.sharveshar.mnyboxd.ui.theme.Space
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import kotlinx.coroutines.delay
import java.time.YearMonth

/**
 * Home: where you stand, in two seconds. The balance, what you spent today and the budget come
 * first, with the app's + resting in the middle (it glides to the corner on other tabs). The month picker changes the budget card and the line behind the
 * balance; the balance itself is always today's.
 *
 * While Quick Add is open over Home, the figures keep showing what they showed. When it closes
 * they update in a short sequence (balance rolls, budget moves, the new transaction slides in),
 * so the change is seen rather than happening behind the sheet.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSeeAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onInsights: () -> Unit,
    onSettings: () -> Unit,
) {
    LifecycleResumeEffect(Unit) { Today.refresh(); onPauseOrDispose { } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Mnyboxd.colors

    // Hold everything while Quick Add is up; release in order once it has gone.
    val sheet = LocalSheetMotion.current
    val sheetOpen by remember(sheet) { derivedStateOf { (sheet?.progress ?: 0f) > 0f } }
    var holdBalance by remember { mutableStateOf(false) }
    var holdBudget by remember { mutableStateOf(false) }
    var holdList by remember { mutableStateOf(false) }
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) {
            holdBalance = true; holdBudget = true; holdList = true
        } else {
            holdBalance = false
            delay(180)
            holdBudget = false
            delay(180)
            holdList = false
        }
    }
    val balance = rememberHeld(state.balancePaise, holdBalance)
    val spentToday = rememberHeld(state.spentToday, holdBalance)
    val line = rememberHeld(state.monthToDate, holdBudget)
    val budget = rememberHeld(state.budget, holdBudget)
    val recent = rememberHeld(state.recent, holdList)
    val week = rememberHeld(state.week, holdBudget)

    // Money coming in gets a brief "↑ ₹2,000" beside the balance (spending already shows as today).
    var lastBalance by remember { mutableStateOf<Long?>(null) }
    var gain by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(balance, state.loading) {
        if (state.loading) return@LaunchedEffect
        val before = lastBalance
        lastBalance = balance
        if (before != null && balance > before) {
            gain = balance - before
            delay(2_400)
            gain = null
        }
    }

    val bell = rememberOriginAnchor()
    var showAlerts by remember { mutableStateOf(false) }
    val stagger = rememberStagger()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "top") {
            HomeHeader(alertCount = state.alerts.size, bell = bell, onAlerts = { showAlerts = true }, onSettings = onSettings)
        }

        item(key = "hero") {
            Column(Modifier.staggered(stagger, 0)) {
                MonthPicker(
                    month = state.month,
                    months = state.months,
                    onPick = viewModel::pickMonth,
                    modifier = Modifier.padding(start = Space.gutter - Space.sm, top = Space.md),
                )
                // The balance opens Accounts, where it's broken down.
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .padding(horizontal = Space.gutter - Space.sm)
                        .pressScale(interaction, 0.98f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open accounts", onClick = onSeeAccounts)
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Oversized, but never wider than the screen: long balances step down in size.
                        val chars = Money.format(balance).length.coerceAtLeast(6)
                        val size = minOf(62f, maxWidth.value / (chars * 0.6f))
                        RollingAmount(
                            paise = state.balancePaise,
                            hold = holdBalance,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, lineHeight = (size * 1.12f).sp, letterSpacing = (-size / 30f).sp),
                            color = if (balance < 0) c.negative else MaterialTheme.colorScheme.onSurface,
                            riseColor = c.positive,
                        )
                    }
                }
                Row(Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    val pill = when {
                        !state.isCurrentMonth -> "↓ ${Money.format(state.spentInMonth)} spent in ${state.month.format(Formats.month)}"
                        spentToday > 0 -> "↓ −${Money.format(spentToday)} today"
                        else -> "Nothing spent today"
                    }
                    AmountPill(pill, color = if (state.isCurrentMonth && spentToday == 0L) c.muted else MaterialTheme.colorScheme.onSurface)
                    AnimatedVisibility(
                        visible = gain != null,
                        enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 2 },
                        exit = fadeOut(tween(300)),
                    ) {
                        // Keeps its figure while fading out.
                        val shown = rememberHeld(gain ?: 0L, hold = gain == null)
                        AmountPill("↗ ${Money.format(shown)}", color = c.positive)
                    }
                }
                // The month's spending as a faint rising line, with the + resting on it.
                Box(Modifier.fillMaxWidth().height(176.dp)) {
                    if (state.transactionCount > 0) {
                        FlowLine(
                            runningTotals = line,
                            description = "Spending so far in ${state.month.format(Formats.month)}: ${Money.format(line.lastOrNull() ?: 0L)}",
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                    }
                    // The app's + rests here (it lives in the shell, so it can glide between tabs).
                    AddButtonPlace(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.sm))
                }
            }
        }

        item(key = "budget") {
            SectionCard(Modifier.staggered(stagger, 1).padding(horizontal = Space.gutter).padding(top = Space.lg), onClick = onBudgets) {
                when (val b = budget) {
                    is BudgetView.Live -> {
                        val s = b.status
                        val title = when {
                            b.period == BudgetPeriod.DAILY -> "Today's budget"
                            b.period == BudgetPeriod.WEEKLY -> "This week's budget"
                            state.isCurrentMonth -> "Budget"
                            else -> "Budget · ${state.month.format(Formats.month)}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${s.percentUsed}%", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = if (s.remainingPaise < 0) c.negative else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(Space.md))
                        BudgetBar(s.fraction, s.level, height = 6.dp)
                        Spacer(Modifier.height(Space.md))
                        Text(
                            if (s.remainingPaise >= 0) "${Money.format(s.remainingPaise)} left of ${Money.format(s.limitPaise)}"
                            else "${Money.format(-s.remainingPaise)} over the ${Money.format(s.limitPaise)} budget",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = if (s.remainingPaise < 0) c.negative else c.muted,
                        )
                    }
                    // A daily or weekly budget in a month that's over: how its days or weeks went.
                    is BudgetView.Past -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${BudgetWords.title(b.period)} · ${state.month.format(Formats.month)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${b.kept}/${b.track.size}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                        }
                        Spacer(Modifier.height(Space.md))
                        BudgetStrip(b.track)
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Within ${BudgetWords.perPeriod(b.limitPaise, b.period)} on ${b.kept} of ${BudgetWords.count(b.track.size, b.period)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = c.muted,
                        )
                    }
                    // Home never shows months to come.
                    is BudgetView.Upcoming -> Unit
                    null -> if (!state.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Set a budget", style = MaterialTheme.typography.titleSmall)
                                Text("For a day, a week or a month: see what's left to spend at a glance.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                        }
                    }
                }
            }
        }

        if (week != null && state.transactionCount > 0) {
            item(key = "week") {
                WeekGlanceCard(week, onOpen = onInsights, modifier = Modifier.staggered(stagger, 2).padding(horizontal = Space.gutter).padding(top = Space.md))
            }
        }

        if (!state.loading && state.transactionCount == 0) {
            item(key = "empty") {
                SectionCard(Modifier.staggered(stagger, 2).padding(horizontal = Space.gutter).padding(top = Space.lg)) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "Nothing recorded yet",
                        message = if (state.hasAccounts) "Tap + to add your first expense, or hold it to speak one." else
                            "Tap + to add your first expense, or hold it to speak one. Set up your bank accounts and cards in Accounts so balances stay accurate.",
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent-h") { SectionHeader("Recent", Modifier.staggered(stagger, 2).padding(start = Space.gutter, end = Space.sm, top = Space.lg), "See all", onSeeAllTransactions) }
            items(recent, key = { "r" + it.id }) { row ->
                TransactionListItem(
                    row,
                    onClick = { onOpenTransaction(row.id) },
                    modifier = Modifier.staggered(stagger, 3).animateItem(
                        fadeInSpec = tween(320),
                        placementSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                        fadeOutSpec = tween(200),
                    ),
                )
            }
        }
    }

    // Alerts: budgets at 80% or more of the day, week or month we're in. Tap one to open Budgets.
    OriginPopup(visible = showAlerts, anchor = bell, onDismissRequest = { showAlerts = false }) {
        Column(Modifier.padding(Space.xl)) {
            Text("Alerts", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            if (state.alerts.isEmpty()) {
                Text(
                    "You're all clear. When a budget passes 80% of its day, week or month, it shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                )
            } else {
                state.alerts.forEach { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClickLabel = "Open budgets") { showAlerts = false; onBudgets() }
                            .heightIn(min = 56.dp)
                            .padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (a.level == BudgetLevel.EXCEEDED) c.negative else c.warning
                        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.bodyLarge)
                            Text(a.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = { showAlerts = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

/** "November ⌄": picks the month the budget card and the line show. */
@Composable
private fun MonthPicker(month: YearMonth, months: List<YearMonth>, onPick: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val now = YearMonth.now()
    fun label(m: YearMonth) = if (m.year == now.year) m.format(Formats.month) else m.format(Formats.monthYear)
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.DropdownList, onClickLabel = "Choose month") { open = true }
                .heightIn(min = 40.dp)
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(month), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = Mnyboxd.colors.cardElevated) {
            months.forEach { m ->
                DropdownMenuItem(
                    text = { Text(label(m), style = MaterialTheme.typography.bodyLarge, color = if (m == month) MaterialTheme.colorScheme.onSurface else Mnyboxd.colors.muted) },
                    onClick = { open = false; onPick(m) },
                )
            }
        }
    }
}
