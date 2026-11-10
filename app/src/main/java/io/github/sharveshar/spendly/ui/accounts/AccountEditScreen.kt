// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.AmountInput
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.repo.AccountInput
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.ledger.BalanceCalculator
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.components.AmountField
import io.github.sharveshar.spendly.ui.components.OriginPopup
import io.github.sharveshar.spendly.ui.components.SelectChip
import io.github.sharveshar.spendly.ui.components.icon
import io.github.sharveshar.spendly.ui.components.originAnchor
import io.github.sharveshar.spendly.ui.components.rememberOriginAnchor
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountEditState(
    val editing: Boolean = false,
    val name: String = "",
    val institution: String = "",
    val type: AccountType = AccountType.SAVINGS,
    val amountText: String = "",
    val linkedAccountId: Long? = null,
    val creditLimitText: String = "",
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val referenced: Boolean = false,
    val banks: List<AccountUi> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

sealed interface AccountEditEvent {
    data object Done : AccountEditEvent
    data object Deleted : AccountEditEvent
}

class AccountEditViewModel(private val repos: Repositories, handle: SavedStateHandle) : ViewModel() {
    // Read by key so the same screen serves AccountEditRoute and AccountEditSheetRoute (both have `id`).
    private val id = handle.get<Long>("id")?.takeIf { it > 0 }
    private val form = MutableStateFlow(
        (if (handle.get<Boolean>("loaded") == true) restore(handle) else null) ?: AccountEditState(editing = id != null),
    )
    private val savedHandle = handle
    private val _events = Channel<AccountEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<AccountEditState> = combine(form, repos.accounts.observeWithBalances()) { f, rows ->
        f.copy(banks = AccountUi.from(rows).filter { it.type.isBank && !it.archived && it.id != id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        if (savedHandle.get<Boolean>("loaded") != true) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (id != null) {
            val a = repos.accounts.getById(id) ?: return
            val signed = a.openingBalancePaise
            val opening = if (a.type == AccountType.CREDIT_CARD) BalanceCalculator.outstanding(signed) else signed
            update(
                form.value.copy(
                    name = a.name,
                    institution = a.institution,
                    type = a.type,
                    amountText = if (opening == 0L) "" else AmountInput.fromPaise(opening).text,
                    linkedAccountId = a.linkedAccountId,
                    creditLimitText = a.creditLimitPaise?.let { AmountInput.fromPaise(it).text }.orEmpty(),
                    colorIndex = a.colorIndex,
                    archived = a.archived,
                    referenced = repos.accounts.isReferenced(id),
                ),
            )
        } else {
            val count = repos.accounts.observeWithBalances().first().size
            update(form.value.copy(colorIndex = count % 10))
        }
    }

    private fun update(s: AccountEditState) {
        form.value = s.copy(error = null)
        savedHandle["loaded"] = true
        savedHandle["name"] = s.name
        savedHandle["institution"] = s.institution
        savedHandle["type"] = s.type.name
        savedHandle["amount"] = s.amountText
        savedHandle["linked"] = s.linkedAccountId
        savedHandle["limit"] = s.creditLimitText
        savedHandle["color"] = s.colorIndex
        savedHandle["archived"] = s.archived
        savedHandle["referenced"] = s.referenced
    }

    private fun restore(h: SavedStateHandle): AccountEditState? = runCatching {
        AccountEditState(
            editing = id != null,
            name = h["name"] ?: "",
            institution = h["institution"] ?: "",
            type = AccountType.valueOf(h["type"] ?: AccountType.SAVINGS.name),
            amountText = h["amount"] ?: "",
            linkedAccountId = h["linked"],
            creditLimitText = h["limit"] ?: "",
            colorIndex = h["color"] ?: 0,
            archived = h["archived"] ?: false,
            referenced = h["referenced"] ?: false,
        )
    }.getOrNull()

    fun setName(v: String) = update(form.value.copy(name = v.take(40)))
    fun setInstitution(v: String) = update(form.value.copy(institution = v.take(40)))
    fun setType(t: AccountType) = update(form.value.copy(type = t, amountText = if (t == AccountType.DEBIT_CARD) "" else form.value.amountText))
    fun setAmount(v: String) = update(form.value.copy(amountText = v))
    fun setLinked(id: Long) = update(form.value.copy(linkedAccountId = id))
    fun setLimit(v: String) = update(form.value.copy(creditLimitText = v))
    fun setColor(i: Int) = update(form.value.copy(colorIndex = i))

    fun save() {
        val f = form.value
        if (f.busy) return
        val amount = if (f.amountText.isBlank()) 0L else Money.parseRupees(f.amountText)
        if (amount == null) { form.value = f.copy(error = "Enter a valid amount, for example 1250.50"); return }
        val limit = if (f.creditLimitText.isBlank()) null else Money.parseRupees(f.creditLimitText)
        if (f.creditLimitText.isNotBlank() && limit == null) { form.value = f.copy(error = "Enter a valid credit limit"); return }
        form.value = f.copy(busy = true)
        viewModelScope.launch {
            val input = AccountInput(f.name, f.institution, f.type, amount, f.linkedAccountId, limit, f.colorIndex)
            val r = if (id == null) repos.accounts.create(input) else repos.accounts.update(id, input)
            when (r) {
                is WriteResult.Ok -> _events.send(AccountEditEvent.Done)
                is WriteResult.Rejected -> form.value = form.value.copy(busy = false, error = r.reason)
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val target = id ?: return
        viewModelScope.launch {
            repos.accounts.setArchived(target, archived)
            _events.send(AccountEditEvent.Done)
        }
    }

    fun delete() {
        val target = id ?: return
        viewModelScope.launch {
            when (val r = repos.accounts.deleteIfUnused(target)) {
                is WriteResult.Ok -> _events.send(AccountEditEvent.Deleted)
                is WriteResult.Rejected -> form.value = form.value.copy(error = r.reason)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(viewModel: AccountEditViewModel, onDone: () -> Unit, onDeleted: () -> Unit) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deleteAnchor = rememberOriginAnchor()
    LaunchedEffect(Unit) {
        viewModel.events.collect { e -> if (e is AccountEditEvent.Deleted) onDeleted() else onDone() }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, contentDescription = "Close without saving") }
            Text(if (s.editing) "Edit account" else "New account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text("Type", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                AccountType.entries.forEach { t ->
                    SelectChip(
                        selected = s.type == t,
                        label = t.label,
                        icon = t.icon(),
                        onClick = { viewModel.setType(t) },
                        enabled = !s.referenced || s.type == t,
                    )
                }
            }
            if (s.referenced) {
                Text("The type can't change once an account has transactions.", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
            }
            OutlinedTextField(
                value = s.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                placeholder = { Text(if (s.type == AccountType.CREDIT_CARD) "e.g. ICICI Coral" else "e.g. HDFC Savings") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.type != AccountType.CASH) {
                OutlinedTextField(
                    value = s.institution,
                    onValueChange = viewModel::setInstitution,
                    label = { Text("Bank or provider (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(s.type == AccountType.DEBIT_CARD) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("Linked bank account", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
                    if (s.banks.isEmpty()) {
                        Text("Add a savings or current account first. Debit card payments come out of that account.", style = MaterialTheme.typography.bodySmall, color = Spendly.colors.warning)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        s.banks.forEach { b -> SelectChip(selected = s.linkedAccountId == b.id, label = b.name, onClick = { viewModel.setLinked(b.id) }) }
                    }
                }
            }
            AnimatedVisibility(s.type != AccountType.DEBIT_CARD) {
                AmountField(
                    value = s.amountText,
                    onValueChange = viewModel::setAmount,
                    label = if (s.type == AccountType.CREDIT_CARD) "Amount currently owed" else "Current balance",
                    supporting = if (s.type == AccountType.CREDIT_CARD) "Your card's outstanding today. Purchases add to it; payments reduce it." else "Your balance right now. MNYBOXD tracks changes from here.",
                )
            }
            AnimatedVisibility(s.type == AccountType.CREDIT_CARD) {
                AmountField(value = s.creditLimitText, onValueChange = viewModel::setLimit, label = "Credit limit (optional)")
            }
            Text("Colour", style = MaterialTheme.typography.labelMedium, color = Spendly.colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Spendly.colors.categoryPalette.forEachIndexed { i, c ->
                    val selected = s.colorIndex == i
                    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, label = "ring")
                    val inner by animateDpAsState(if (selected) 26.dp else 30.dp, label = "swatch")
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(2.dp, ring, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { viewModel.setColor(i) }
                            .semantics { contentDescription = "Colour ${i + 1}" },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(inner).clip(CircleShape).background(c)) }
                }
            }
            Text(
                "MNYBOXD never asks for passwords, PINs, OTPs, CVVs or full card numbers. Don't enter them anywhere in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = Spendly.colors.muted,
            )
            s.error?.let { Text(it, color = Spendly.colors.negative, style = MaterialTheme.typography.bodyMedium) }
            if (s.editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(onClick = { viewModel.setArchived(!s.archived) }) { Text(if (s.archived) "Restore account" else "Archive") }
                    if (!s.referenced) {
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.originAnchor(deleteAnchor)) {
                            Text("Delete", color = Spendly.colors.negative)
                        }
                    }
                }
                Text(
                    if (s.referenced) "Archiving hides the account from new entries but keeps its history and balance."
                    else "This account has no transactions, so it can be deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Spendly.colors.muted,
                )
            }
            Spacer(Modifier.height(Space.lg))
        }
        Button(
            onClick = viewModel::save,
            enabled = !s.busy,
            modifier = Modifier.fillMaxWidth().padding(Space.gutter).height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (s.editing) "Save changes" else "Add account", style = MaterialTheme.typography.titleMedium) }
    }

    OriginPopup(visible = confirmDelete, anchor = deleteAnchor, onDismissRequest = { confirmDelete = false }) {
        Column(Modifier.padding(Space.xxl)) {
            Text("Delete ${s.name}?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))
            Text("This account has no transactions. Deleting it can't be undone.", color = Spendly.colors.muted)
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
