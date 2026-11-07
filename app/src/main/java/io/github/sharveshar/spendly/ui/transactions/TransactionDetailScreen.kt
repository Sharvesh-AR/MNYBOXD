package io.github.sharveshar.spendly.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.appContainer
import io.github.sharveshar.spendly.data.db.TransactionEntity
import io.github.sharveshar.spendly.data.repo.DeleteResult
import io.github.sharveshar.spendly.ui.components.LocalAppMessages
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import androidx.compose.ui.platform.LocalContext
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.common.Formats
import io.github.sharveshar.spendly.ui.components.CategoryIcons
import io.github.sharveshar.spendly.ui.components.DetailRow
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SectionCard
import io.github.sharveshar.spendly.ui.components.SectionHeader
import io.github.sharveshar.spendly.ui.components.TransactionListItem
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.components.sharedContainer
import io.github.sharveshar.spendly.ui.navigation.TransactionDetailRoute
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class DetailUiState(
    val loading: Boolean = true,
    val row: TransactionRow? = null,
    val refunds: List<TransactionRow> = emptyList(),
    val message: String? = null,
) {
    val refundedPaise get() = refunds.sumOf { it.amountPaise }
    val refundablePaise get() = row?.takeIf { it.type == TransactionType.EXPENSE }?.let { it.amountPaise - refundedPaise } ?: 0L
}

class TransactionDetailViewModel(private val repos: Repositories, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<TransactionDetailRoute>().id
    private val message = MutableStateFlow<String?>(null)
    private val _deleted = Channel<TransactionEntity?>(Channel.CONFLATED)
    val deleted = _deleted.receiveAsFlow()

    val state: StateFlow<DetailUiState> = combine(
        repos.transactions.observeRow(id),
        repos.transactions.observeRefundsOf(id),
        message,
    ) { row, refunds, msg -> DetailUiState(false, row, refunds, msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun delete() {
        viewModelScope.launch {
            val (r, removed) = repos.transactions.deleteForUndo(id)
            when (r) {
                DeleteResult.Deleted, DeleteResult.NotFound -> _deleted.send(removed)
                is DeleteResult.HasRefunds -> message.value = "Delete the ${r.count} refund${if (r.count > 1) "s" else ""} linked to this expense first."
            }
        }
    }
}

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRefund: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deleteAnchor = rememberOriginAnchor()
    val messages = LocalAppMessages.current
    val container = LocalAppContainer.current ?: LocalContext.current.appContainer
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { removed ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onBack()
            if (removed != null && messages != null) {
                // Undo runs in the shell's scope: this screen (and its ViewModel) is already gone.
                messages.showUndo("Transaction deleted") {
                    val ok = container.repositories.transactions.restoreDeleted(removed)
                    if (!ok) messages.show("Couldn't restore it. An account it used no longer exists.")
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .sharedContainer("tx-${viewModel.id}", 0)
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        }
        val row = state.row
        if (row == null) {
            if (!state.loading) Text("This transaction was deleted.", modifier = Modifier.padding(Space.gutter), color = Spendly.colors.muted)
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            val palette = Spendly.colors.categoryPalette
            val tint = if (row.type == TransactionType.TRANSFER) MaterialTheme.colorScheme.secondary else palette[(row.categoryColor ?: 9).coerceIn(0, 9)]
            val icon = if (row.type == TransactionType.TRANSFER) CategoryIcons.transfer else CategoryIcons.forKey(row.categoryIcon)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(icon, tint, size = 56.dp)
                Spacer(Modifier.height(Space.md))
                Text(
                    row.description.ifBlank { row.categoryName ?: row.type.label },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.xs))
                val sign = when (row.type) {
                    TransactionType.EXPENSE -> "−"
                    TransactionType.INCOME, TransactionType.REFUND -> "+"
                    TransactionType.TRANSFER -> ""
                }
                Text(
                    sign + Money.format(row.amountPaise),
                    style = MaterialTheme.typography.displayMedium,
                    color = when (row.type) {
                        TransactionType.INCOME, TransactionType.REFUND -> Spendly.colors.positive
                        TransactionType.TRANSFER -> Spendly.colors.muted
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = Space.sm),
                )
                Text(
                    Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault()).format(Formats.dateTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = Spendly.colors.muted,
                )
            }
            Spacer(Modifier.height(Space.xl))
            SectionCard {
                DetailRow("Type", row.type.label)
                if (row.type != TransactionType.TRANSFER) row.categoryName?.let { DetailRow("Category", it) }
                if (row.type == TransactionType.TRANSFER) {
                    DetailRow("From", row.accountName)
                    DetailRow("To", row.toAccountName.orEmpty())
                } else {
                    DetailRow(if (row.type == TransactionType.EXPENSE) "Paid from" else "Account", row.accountName)
                }
                row.instrumentName?.let { DetailRow("Card", it) }
                row.channel?.let { DetailRow("Method", it.label) }
                row.provider?.let { DetailRow("App", it.label) }
                if (row.description.isNotBlank()) DetailRow("Note", row.description)
            }
            if (row.type == TransactionType.EXPENSE && state.refunds.isNotEmpty()) {
                SectionHeader("Refunds · ${Money.format(state.refundedPaise)} returned", Modifier.padding(top = Space.md))
                state.refunds.forEach { r -> TransactionListItem(r, onClick = { onOpen(r.id) }) }
            }
            state.message?.let {
                Spacer(Modifier.height(Space.md))
                Text(it, color = Spendly.colors.warning, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Space.xl))
        }
        HorizontalDivider(color = Spendly.colors.hairline)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { onEdit(row.id) }, modifier = Modifier.heightIn(min = 48.dp).sharedContainer("entry-detail-edit", 24)) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(Space.sm))
                Text("Edit")
            }
            if (state.refundablePaise > 0) {
                FilledTonalButton(onClick = { onRefund(row.id) }, modifier = Modifier.heightIn(min = 48.dp).sharedContainer("entry-detail-refund", 24)) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                    Spacer(Modifier.width(Space.sm))
                    Text("Refund")
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.originAnchor(deleteAnchor)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete transaction", tint = Spendly.colors.negative)
            }
        }
    }

    OriginPopup(visible = confirmDelete, anchor = deleteAnchor, onDismissRequest = { confirmDelete = false }) {
        val row = state.row
        Column(Modifier.padding(Space.xxl)) {
            Text("Delete this transaction?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text(
                row?.let { "${it.type.label} of ${Money.format(it.amountPaise)} will be removed and balances will be recalculated. You can undo this for a few seconds afterwards." }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Spendly.colors.muted,
            )
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                Spacer(Modifier.width(Space.sm))
                Button(
                    onClick = { confirmDelete = false; viewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Delete") }
            }
        }
    }
}
