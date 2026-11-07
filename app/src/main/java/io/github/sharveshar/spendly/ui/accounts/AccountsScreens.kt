package io.github.sharveshar.spendly.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.ledger.BalanceCalculator
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.components.BudgetBar
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.LabeledValue
import io.github.sharveshar.spendly.ui.components.MoneyText
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.icon
import io.github.sharveshar.spendly.ui.components.sharedContainer
import io.github.sharveshar.spendly.ui.components.spokenAmount
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.ui.transactions.TransactionsViewModel
import io.github.sharveshar.spendly.ui.transactions.transactionGroups
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AccountsUiState(val loading: Boolean = true, val accounts: List<AccountUi> = emptyList()) {
    private val active get() = accounts.filter { !it.archived }
    val banks get() = active.filter { it.type.isBank }
    val cashAndWallets get() = active.filter { it.type == AccountType.CASH || it.type == AccountType.WALLET }
    val cards get() = active.filter { it.type == AccountType.CREDIT_CARD || it.type == AccountType.DEBIT_CARD }
    val archived get() = accounts.filter { it.archived }
    /** Debit cards share their bank's balance, so they are excluded to avoid double counting. */
    val netWorth get() = BalanceCalculator.netWorth(accounts.filter { it.type.holdsBalance }.map { it.balancePaise })
}

class AccountsViewModel(repos: Repositories) : ViewModel() {
    val state: StateFlow<AccountsUiState> = repos.accounts.observeWithBalances()
        .map { AccountsUiState(false, AccountUi.from(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}

@Composable
fun AccountsScreen(viewModel: AccountsViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.sm, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add account") }
            }
        }
        item(key = "networth") {
            val nw = state.netWorth
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.lg)) {
                Text("Net worth", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted)
                MoneyText(nw.netPaise, MaterialTheme.typography.displayMedium, color = if (nw.netPaise < 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
                    LabeledValue("Assets", Money.format(nw.assetsPaise))
                    LabeledValue("Owed on cards", Money.format(nw.liabilitiesPaise), valueColor = if (nw.liabilitiesPaise > 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        fun section(title: String, list: List<AccountUi>) {
            if (list.isEmpty()) return
            item(key = "h-$title") { SectionHeader(title, Modifier.padding(horizontal = Space.gutter)) }
            items(list, key = { it.id }) { a -> AccountRow(a, onClick = { onOpen(a.id) }, modifier = Modifier.animateItem()) }
        }
        section("Bank accounts", state.banks)
        section("Cash & wallets", state.cashAndWallets)
        section("Cards", state.cards)
        if (!state.loading && state.banks.isEmpty() && state.cards.isEmpty()) {
            item(key = "hint") {
                EmptyState(
                    Icons.AutoMirrored.Rounded.ReceiptLong,
                    "Add your bank accounts and cards",
                    "Spendly never asks for passwords, PINs or card numbers. Just a name and your current balance.",
                    action = { FilledTonalButton(onClick = onAdd) { Text("Add account") } },
                )
            }
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-toggle") {
                Row(
                    Modifier.fillMaxWidth().clickable { showArchived = !showArchived }.heightIn(min = 48.dp).padding(horizontal = Space.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Archived (${state.archived.size})", style = MaterialTheme.typography.titleSmall, color = Spendly.colors.muted, modifier = Modifier.weight(1f))
                    Icon(if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (showArchived) "Hide archived" else "Show archived", tint = Spendly.colors.muted)
                }
            }
            if (showArchived) items(state.archived, key = { it.id }) { a -> AccountRow(a, onClick = { onOpen(a.id) }, modifier = Modifier.animateItem()) }
        }
        item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun AccountRow(a: AccountUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = Spendly.colors.categoryPalette[a.colorIndex.coerceIn(0, 9)]
    val (value, label, valueColor) = when (a.type) {
        AccountType.CREDIT_CARD -> Triple(Money.format(a.outstandingPaise), if (a.balancePaise > 0) "credit" else "owed", if (a.outstandingPaise > 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
        AccountType.DEBIT_CARD -> Triple("", "", MaterialTheme.colorScheme.onSurface)
        else -> Triple(Money.format(a.balancePaise), "", if (a.balancePaise < 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
    }
    Row(
        modifier
            .sharedContainer("account-${a.id}")
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = "${a.name}, ${a.subtitle}" + if (a.type == AccountType.DEBIT_CARD) "" else ", ${spokenAmount(if (a.type == AccountType.CREDIT_CARD) a.outstandingPaise else a.balancePaise)} $label"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(a.type.icon(), color)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.subtitle, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                Text(value, style = AmountStyle.row, color = valueColor, maxLines = 1)
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = Spendly.colors.muted)
            }
        }
    }
}

@Composable
fun AccountDetailScreen(
    accountId: Long,
    accountsViewModel: AccountsViewModel,
    transactionsViewModel: TransactionsViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddHere: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val accounts by accountsViewModel.state.collectAsStateWithLifecycle()
    val tx by transactionsViewModel.state.collectAsStateWithLifecycle()
    val a = accounts.accounts.firstOrNull { it.id == accountId }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            if (a != null) IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit account") }
        }
        if (a == null) {
            if (!accounts.loading) Text("This account no longer exists.", Modifier.padding(Space.gutter), color = Spendly.colors.muted)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                val color = Spendly.colors.categoryPalette[a.colorIndex.coerceIn(0, 9)]
                Column(
                    Modifier
                        .padding(horizontal = Space.gutter)
                        .sharedContainer("account-${a.id}")
                        .clip(RoundedCornerShape(24.dp))
                        .background(Spendly.colors.card)
                        .padding(Space.xl)
                        .fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(a.type.icon(), color)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(a.subtitle + if (a.archived) " · Archived" else "", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                        }
                    }
                    Spacer(Modifier.height(Space.lg))
                    when (a.type) {
                        AccountType.CREDIT_CARD -> {
                            Text("Outstanding", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted)
                            MoneyText(a.outstandingPaise, MaterialTheme.typography.displaySmall, color = if (a.outstandingPaise > 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
                            a.creditLimitPaise?.takeIf { it > 0 }?.let { limit ->
                                Spacer(Modifier.height(Space.md))
                                val used = a.outstandingPaise
                                val level = when {
                                    used >= limit -> BudgetLevel.EXCEEDED
                                    used * 100 >= limit * 80 -> BudgetLevel.WARNING
                                    else -> BudgetLevel.OK
                                }
                                BudgetBar((used.toDouble() / limit).toFloat(), level)
                                Spacer(Modifier.height(Space.sm))
                                Text("${Money.format((limit - used).coerceAtLeast(0))} available of ${Money.format(limit)}", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
                            }
                            if (a.balancePaise > 0) Text("Credit balance ${Money.format(a.balancePaise)}", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.positive)
                        }
                        AccountType.DEBIT_CARD -> {
                            Text("Payments come from ${a.linkedAccountName ?: "its linked bank"}", style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted)
                            Spacer(Modifier.height(Space.xs))
                            MoneyText(a.balancePaise, MaterialTheme.typography.displaySmall)
                        }
                        else -> {
                            Text("Balance", style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted)
                            MoneyText(a.balancePaise, MaterialTheme.typography.displaySmall, color = if (a.balancePaise < 0) Spendly.colors.negative else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    AnimatedVisibility(!a.archived) {
                        Column {
                            Spacer(Modifier.height(Space.lg))
                            FilledTonalButton(onClick = onAddHere, modifier = Modifier.sharedContainer("entry-account-add", 24)) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(Space.sm))
                                Text("Add transaction")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
            }
            transactionGroups(tx, onOpenTransaction)
            if (!tx.loading && tx.groups.isEmpty()) {
                item(key = "empty") { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No transactions", "Transactions for this account will show up here.") }
            }
            item(key = "bottom") { Spacer(Modifier.height(Space.xxxl)) }
        }
    }
}
