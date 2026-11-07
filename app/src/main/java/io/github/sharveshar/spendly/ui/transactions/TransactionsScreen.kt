package io.github.sharveshar.spendly.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.data.repo.DeleteResult
import io.github.sharveshar.spendly.appContainer
import io.github.sharveshar.spendly.ui.components.LocalAppMessages
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.launch
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.components.EmptyState
import io.github.sharveshar.spendly.ui.components.OriginAnchor
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SelectChip
import io.github.sharveshar.spendly.ui.components.TransactionListItem
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.components.sharedContainer
import io.github.sharveshar.spendly.ui.theme.AmountStyle
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly

private enum class FilterPopup { NONE, TYPE, CATEGORY, ACCOUNT, DATE }

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteWithUndo = rememberDeleteWithUndo()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var popup by rememberSaveable { mutableStateOf(FilterPopup.NONE) }
    val typeAnchor = rememberOriginAnchor()
    val categoryAnchor = rememberOriginAnchor()
    val accountAnchor = rememberOriginAnchor()
    val dateAnchor = rememberOriginAnchor()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Text(
                "Transactions",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.md).semantics { heading() },
            )
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search notes, places or amounts", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }) else null,
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Spendly.colors.card,
                    focusedContainerColor = Spendly.colors.card,
                    unfocusedBorderColor = Spendly.colors.card,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
            )
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item {
                    SelectChip(state.type != null, state.type?.label ?: "Type", { popup = FilterPopup.TYPE }, Modifier.originAnchor(typeAnchor))
                }
                item {
                    SelectChip(state.categoryId != null, state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "Category", { popup = FilterPopup.CATEGORY }, Modifier.originAnchor(categoryAnchor))
                }
                item {
                    SelectChip(state.accountId != null, state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "Account", { popup = FilterPopup.ACCOUNT }, Modifier.originAnchor(accountAnchor))
                }
                item {
                    SelectChip(state.datePreset != DatePreset.ALL, state.datePreset.takeIf { it != DatePreset.ALL }?.label ?: "Date", { popup = FilterPopup.DATE }, Modifier.originAnchor(dateAnchor))
                }
                if (state.hasFilters) {
                    item { SelectChip(false, "Clear", viewModel::clearFilters, icon = Icons.Rounded.FilterAltOff) }
                }
            }
        }
        transactionGroups(state, onOpen, onDelete = deleteWithUndo)
        if (!state.loading && state.groups.isEmpty()) {
            item(key = "empty") {
                if (state.hasFilters) {
                    EmptyState(Icons.Rounded.FilterAltOff, "No matches", "Nothing matches these filters.", action = { TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") } })
                } else {
                    EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No transactions yet", "Everything you record appears here, grouped by day.")
                }
            }
        }
        if (state.truncated) {
            item(key = "truncated") {
                Text(
                    "Showing the latest ${state.totalCount}. Use filters to narrow down older records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Spendly.colors.muted,
                    modifier = Modifier.padding(Space.gutter),
                )
            }
        }
    }

    OptionPopup(popup == FilterPopup.TYPE, typeAnchor, "Type", { popup = FilterPopup.NONE },
        listOf<Pair<String, Boolean>>("All types" to (state.type == null)) + TransactionType.entries.map { it.label to (state.type == it) },
    ) { i -> viewModel.setType(if (i == 0) null else TransactionType.entries[i - 1]) }
    OptionPopup(popup == FilterPopup.CATEGORY, categoryAnchor, "Category", { popup = FilterPopup.NONE },
        listOf("All categories" to (state.categoryId == null)) + state.categories.map { it.name to (state.categoryId == it.id) },
    ) { i -> viewModel.setCategory(if (i == 0) null else state.categories[i - 1].id) }
    OptionPopup(popup == FilterPopup.ACCOUNT, accountAnchor, "Account", { popup = FilterPopup.NONE },
        listOf("All accounts" to (state.accountId == null)) + state.accounts.map { it.name to (state.accountId == it.id) },
    ) { i -> viewModel.setAccount(if (i == 0) null else state.accounts[i - 1].id) }
    OptionPopup(popup == FilterPopup.DATE, dateAnchor, "Date", { popup = FilterPopup.NONE },
        DatePreset.entries.map { it.label to (state.datePreset == it) },
    ) { i -> viewModel.setPreset(DatePreset.entries[i]) }
}

/** Grouped transaction rows with sticky day headers. Shared by history and account detail. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.transactionGroups(
    state: TransactionsUiState,
    onOpen: (Long) -> Unit,
    /** Swipe left to delete (with Undo). Returns false if the row couldn't be deleted. */
    onDelete: (suspend (TransactionRow) -> Boolean)? = null,
) {
    state.groups.forEach { g ->
        stickyHeader(key = "h" + g.date) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g.label, style = MaterialTheme.typography.labelLarge, color = Spendly.colors.muted, modifier = Modifier.weight(1f).semantics { heading() })
                if (g.netPaise != 0L) {
                    Text(Money.format(g.netPaise, signed = true), style = AmountStyle.compact, color = Spendly.colors.muted)
                }
            }
        }
        items(g.rows, key = { it.id }) { row ->
            if (onDelete == null) {
                TransactionListItem(row, onClick = { onOpen(row.id) }, modifier = Modifier.animateItem().sharedContainer("tx-${row.id}", 0))
            } else {
                SwipeToDelete(onDelete = { onDelete(row) }, modifier = Modifier.animateItem()) {
                    TransactionListItem(row, onClick = { onOpen(row.id) }, modifier = Modifier.sharedContainer("tx-${row.id}", 0))
                }
            }
        }
    }
}

/**
 * Deletes a transaction and shows "Transaction deleted · Undo". Returns false (with a message)
 * for an expense that still has refunds linked to it.
 */
@Composable
fun rememberDeleteWithUndo(): suspend (TransactionRow) -> Boolean {
    val messages = LocalAppMessages.current
    val container = LocalAppContainer.current ?: LocalContext.current.appContainer
    return remember(messages, container) {
        { row ->
            val repos = container.repositories
            val (result, removed) = repos.transactions.deleteForUndo(row.id)
            if (result is DeleteResult.HasRefunds) {
                messages?.show("Delete the refunds linked to this expense first.")
                false
            } else {
                if (removed != null) {
                    messages?.showUndo("Transaction deleted") {
                        if (!repos.transactions.restoreDeleted(removed)) messages.show("Couldn't restore it. An account it used no longer exists.")
                    }
                }
                true
            }
        }
    }
}

/**
 * Swipe a row left to delete it. The red area grows with the swipe; if deleting isn't possible
 * (an expense with refunds) the row springs back. Screen readers get a "Delete" action instead.
 */
@Composable
private fun SwipeToDelete(onDelete: suspend () -> Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Delete") { scope.launch { onDelete() }; true })
        },
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { if (!onDelete()) state.reset() }
            }
        },
        backgroundContent = {
            val active = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (active) Spendly.colors.negative.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(end = Space.xxl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (active) Icon(Icons.Rounded.Delete, contentDescription = null, tint = Spendly.colors.negative)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Single-choice list that expands from the chip that opened it. */
@Composable
fun OptionPopup(
    visible: Boolean,
    anchor: OriginAnchor,
    title: String,
    onDismiss: () -> Unit,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    OriginPopup(visible = visible, anchor = anchor, onDismissRequest = onDismiss) {
        Column(Modifier.padding(vertical = Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.sm))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, (label, selected) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(i); onDismiss() }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = Space.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (selected) {
                            Spacer(Modifier.width(Space.sm))
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
