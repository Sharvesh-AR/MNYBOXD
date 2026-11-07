package io.github.sharveshar.spendly.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.R
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.ui.common.Today
import io.github.sharveshar.spendly.ui.components.AmountPill
import io.github.sharveshar.spendly.ui.components.BudgetBar
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.RollingAmount
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.Sparkline
import io.github.sharveshar.spendly.ui.components.TransactionListItem
import io.github.sharveshar.spendly.ui.components.compactAxisDescription
import io.github.sharveshar.spendly.ui.components.pressScale
import io.github.sharveshar.spendly.ui.components.rememberHeld
import io.github.sharveshar.spendly.ui.components.rememberStagger
import io.github.sharveshar.spendly.ui.components.staggered
import io.github.sharveshar.spendly.ui.entry.LocalSheetMotion
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.delay

/**
 * Home: where you stand, in two seconds. The balance, what you spent today and what's left in
 * the budget come first; then adding, and the latest transactions.
 *
 * While Quick Add is open over Home, the figures keep showing what they showed. When it closes
 * they update in a short sequence (balance rolls, budget moves, the new transaction slides in),
 * so the change is seen rather than happening behind the sheet.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSeeAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onSettings: () -> Unit,
) {
    LifecycleResumeEffect(Unit) { Today.refresh(); onPauseOrDispose { } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Spendly.colors

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
    val week = rememberHeld(state.week, holdBalance)
    val budget = rememberHeld(state.budget, holdBudget)
    val recent = rememberHeld(state.recent, holdList)

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

    val stagger = rememberStagger()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.sm, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_w_logo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Spendly", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = c.muted) }
            }
        }

        item(key = "balance") {
            Column(Modifier.staggered(stagger, 0).padding(top = Space.xl)) {
                // The balance opens Accounts, where it's broken down.
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .padding(horizontal = Space.gutter - Space.sm)
                        .pressScale(interaction, 0.98f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClickLabel = "Open accounts", onClick = onSeeAccounts)
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Balance", style = MaterialTheme.typography.labelLarge, color = c.muted)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Oversized, but never wider than the screen: long balances step down in size.
                        val chars = Money.format(balance).length.coerceAtLeast(6)
                        val size = minOf(60f, maxWidth.value / (chars * 0.6f))
                        RollingAmount(
                            paise = state.balancePaise,
                            hold = holdBalance,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, lineHeight = (size * 1.12f).sp, letterSpacing = (-size / 30f).sp),
                            color = if (balance < 0) c.negative else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.padding(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    AmountPill(if (spentToday > 0) "↓ ${Money.format(spentToday)} today" else "Nothing spent today", color = if (spentToday > 0) MaterialTheme.colorScheme.onSurface else c.muted)
                    AnimatedVisibility(
                        visible = gain != null,
                        enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 2 },
                        exit = fadeOut(tween(300)),
                    ) {
                        // Keeps its figure while fading out.
                        val shown = rememberHeld(gain ?: 0L, hold = gain == null)
                        AmountPill("↑ ${Money.format(shown)} added", color = c.positive)
                    }
                }
                if (state.transactionCount > 0) {
                    Sparkline(
                        values = week,
                        description = compactAxisDescription("Spending over the last 7 days", state.weekLabels, week),
                        modifier = Modifier.padding(top = Space.lg, start = Space.gutter, end = Space.gutter),
                        height = 64.dp,
                    )
                }
            }
        }

        item(key = "actions") {
            Row(
                Modifier.staggered(stagger, 1).fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                QuickAction("Add expense", Icons.Rounded.Add, onAdd, Modifier.weight(1f))
                QuickAction("Voice", Icons.Rounded.Mic, onVoice, Modifier.weight(1f))
            }
        }

        item(key = "budget") {
            SectionCard(Modifier.staggered(stagger, 2).padding(horizontal = Space.gutter), onClick = onBudgets) {
                val b = budget
                if (b != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Budget", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${b.percentUsed}%", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = if (b.remainingPaise < 0) c.negative else MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(Space.md))
                    BudgetBar(b.fraction, b.level, height = 4.dp)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        if (b.remainingPaise >= 0) "${Money.format(b.remainingPaise)} left of ${Money.format(b.limitPaise)}"
                        else "${Money.format(-b.remainingPaise)} over the ${Money.format(b.limitPaise)} budget",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = if (b.remainingPaise < 0) c.negative else c.muted,
                    )
                } else if (!state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Set a monthly budget", style = MaterialTheme.typography.titleSmall)
                            Text("See what's left to spend at a glance.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                    }
                }
            }
        }

        if (!state.loading && state.transactionCount == 0) {
            item(key = "empty") {
                SectionCard(Modifier.staggered(stagger, 3).padding(horizontal = Space.gutter).padding(top = Space.lg)) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "Nothing recorded yet",
                        message = if (state.hasAccounts) "Add your first expense with the keypad or by voice." else
                            "Add your first expense with the keypad or by voice. Set up your bank accounts and cards in Accounts so balances stay accurate.",
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent-h") { SectionHeader("Recent", Modifier.staggered(stagger, 3).padding(start = Space.gutter, end = Space.sm, top = Space.lg), "See all", onSeeAllTransactions) }
            items(recent, key = { "r" + it.id }) { row ->
                TransactionListItem(
                    row,
                    onClick = { onOpenTransaction(row.id) },
                    modifier = Modifier.staggered(stagger, 4).animateItem(
                        fadeInSpec = tween(320),
                        placementSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                        fadeOutSpec = tween(200),
                    ),
                )
            }
        }
    }
}

/** A labelled quiet action on Home (the white + in the dock stays the main way to add). */
@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, 0.96f)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(50))
            .background(Spendly.colors.card)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(28.dp).background(Spendly.colors.cardElevated, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Space.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
