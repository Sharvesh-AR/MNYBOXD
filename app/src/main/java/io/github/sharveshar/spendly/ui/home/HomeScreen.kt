package io.github.sharveshar.spendly.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.Today
import io.github.sharveshar.spendly.ui.components.AnimatedMoney
import io.github.sharveshar.spendly.ui.components.BarChart
import io.github.sharveshar.spendly.ui.components.BudgetBar
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.MoneyText
import io.github.sharveshar.spendly.ui.components.ProportionRow
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.TransactionListItem
import io.github.sharveshar.spendly.ui.components.compactAxisDescription
import io.github.sharveshar.spendly.ui.components.icon
import io.github.sharveshar.spendly.ui.components.sharedContainer
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onSeeAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onSettings: () -> Unit,
) {
    LifecycleResumeEffect(Unit) { Today.refresh(); onPauseOrDispose { } }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.sm, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Spendly", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Spendly.colors.muted) }
            }
        }
        item(key = "hero") {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.lg)) {
                Text("Spent in ${state.monthLabel}", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted)
                Spacer(Modifier.height(Space.xs))
                MoneyText(state.spentThisMonth, MaterialTheme.typography.displayLarge, countUpOnFirstShow = !state.loading)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                    MiniStat("Today", state.spentToday)
                    MiniStat("Income", state.incomeThisMonth, Spendly.colors.positive)
                }
                Spacer(Modifier.height(Space.lg))
                val budget = state.budget
                if (budget != null) {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBudgets).padding(vertical = Space.xs)) {
                        BudgetBar(budget.fraction, budget.level)
                        Spacer(Modifier.height(Space.sm))
                        val remainingText = if (budget.remainingPaise >= 0) {
                            "${Money.format(budget.remainingPaise)} left of ${Money.format(budget.limitPaise)}"
                        } else {
                            "${Money.format(-budget.remainingPaise)} over the ${Money.format(budget.limitPaise)} budget"
                        }
                        Text(remainingText, style = MaterialTheme.typography.bodySmall, color = if (budget.remainingPaise < 0) Spendly.colors.negative else Spendly.colors.muted)
                    }
                } else if (!state.loading) {
                    TextButton(onClick = onBudgets, contentPadding = PaddingValues(0.dp)) { Text("Set a monthly budget") }
                }
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(Space.sm))
                        Text("Add expense")
                    }
                    FilledTonalButton(onClick = onVoice, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Spacer(Modifier.width(Space.sm))
                        Text("Voice")
                    }
                }
            }
        }

        if (!state.loading && state.transactionCount == 0) {
            item(key = "empty") {
                SectionCard(Modifier.padding(horizontal = Space.gutter)) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "Nothing recorded yet",
                        message = "Add your first expense with the keypad or by voice. Set up your bank accounts and cards in Accounts so balances stay accurate.",
                    )
                }
                Spacer(Modifier.height(Space.lg))
            }
        }

        item(key = "week") {
            SectionCard(Modifier.padding(horizontal = Space.gutter)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(state.week.sum()), style = AmountStyle.compact, color = Spendly.colors.muted)
                }
                Spacer(Modifier.height(Space.lg))
                BarChart(
                    values = state.week,
                    labels = state.weekLabels,
                    highlight = 6,
                    height = 112.dp,
                    description = compactAxisDescription("Spending over the last 7 days", state.weekLabels, state.week),
                )
            }
        }

        if (state.accounts.isNotEmpty()) {
            item(key = "accounts-h") { SectionHeader("Accounts", Modifier.padding(start = Space.gutter, end = Space.sm, top = Space.lg), "See all", onSeeAccounts) }
            item(key = "accounts") {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    items(state.accounts, key = { it.id }) { a -> AccountChipCard(a) { onOpenAccount(a.id) } }
                }
            }
        }

        if (state.topCategories.isNotEmpty()) {
            item(key = "cats-h") { SectionHeader("Where it went", Modifier.padding(start = Space.gutter, end = Space.sm, top = Space.lg)) }
            item(key = "cats") {
                val palette = Spendly.colors.categoryPalette
                val total = state.spentThisMonth.coerceAtLeast(state.topCategories.sumOf { it.amountPaise })
                SectionCard(Modifier.padding(horizontal = Space.gutter), padding = Space.md) {
                    state.topCategories.forEach { c ->
                        val color = palette[(c.category?.colorIndex ?: 9).coerceIn(0, palette.lastIndex)]
                        ProportionRow(
                            label = c.category?.name ?: "Uncategorised",
                            value = c.amountPaise,
                            total = total,
                            color = color,
                            leading = { IconBadge(CategoryIcons.forKey(c.category?.iconKey), color, size = 36.dp) },
                        )
                    }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item(key = "recent-h") { SectionHeader("Recent", Modifier.padding(start = Space.gutter, end = Space.sm, top = Space.lg), "See all", onSeeAllTransactions) }
            items(state.recent, key = { "r" + it.id }) { row -> TransactionListItem(row, onClick = { onOpenTransaction(row.id) }) }
        }
    }
}

@Composable
private fun MiniStat(label: String, paise: Long, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
        AnimatedMoney(paise, AmountStyle.row, color = color)
    }
}

@Composable
fun AccountChipCard(a: AccountUi, onClick: () -> Unit) {
    val palette = Spendly.colors.categoryPalette
    val color = palette[a.colorIndex.coerceIn(0, palette.lastIndex)]
    val isCard = a.type == AccountType.CREDIT_CARD
    SectionCard(Modifier.sharedContainer("account-${a.id}").widthIn(min = 156.dp, max = 220.dp), padding = Space.lg, onClick = onClick) {
        IconBadge(a.type.icon(), color, size = 32.dp)
        Spacer(Modifier.height(Space.md))
        Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnimatedMoney(
            if (isCard) a.outstandingPaise else a.balancePaise,
            style = AmountStyle.summary,
            color = when {
                isCard && a.outstandingPaise > 0 -> Spendly.colors.negative
                !isCard && a.balancePaise < 0 -> Spendly.colors.negative
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
        Text(if (isCard) "Outstanding" else a.type.label, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted, maxLines = 1)
    }
}
