package io.github.sharveshar.spendly.ui.entry

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.core.money.AmountInput
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.CategoryEntity
import io.github.sharveshar.spendly.data.prefs.PreferencesRepository
import io.github.sharveshar.spendly.data.prefs.UserPreferences
import io.github.sharveshar.spendly.data.repo.BudgetAlert
import io.github.sharveshar.spendly.data.repo.SaveResult
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.ledger.TransactionRules
import io.github.sharveshar.spendly.domain.ledger.ValidationError
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.domain.voice.VoiceAccount
import io.github.sharveshar.spendly.domain.voice.VoiceCategory
import io.github.sharveshar.spendly.domain.voice.VoiceIssue
import io.github.sharveshar.spendly.domain.voice.VoiceParser
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.CategoryUi
import io.github.sharveshar.spendly.ui.navigation.EntryRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Everything the user has entered. Serialised into SavedStateHandle to survive process death. */
@Serializable
data class EntryForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val channel: PaymentChannel? = null,
    val provider: PaymentProvider? = null,
    val note: String = "",
    val occurredAt: Long = 0L,
    val clientRef: String = "",
    val refundOf: Long? = null,
    val editId: Long? = null,
    val voiceTranscript: String? = null,
    val voiceIssues: List<VoiceIssue> = emptyList(),
    val initialized: Boolean = false,
) {
    val amount: AmountInput get() = AmountInput(amountText)
}

@Immutable
data class EntryUiState(
    val form: EntryForm = EntryForm(),
    val accounts: List<AccountUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val channels: List<PaymentChannel> = emptyList(),
    val showProviders: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    val saving: Boolean = false,
    val refundLimitPaise: Long? = null,
    val refundOfLabel: String? = null,
    val loadFailed: Boolean = false,
) {
    val isEdit get() = form.editId != null
    val isRefund get() = form.type == TransactionType.REFUND && form.refundOf != null
    val selectedAccount get() = accounts.firstOrNull { it.id == form.accountId }
}

sealed interface EntryEvent {
    data class Saved(val alert: BudgetAlert?, val wasEdit: Boolean) : EntryEvent
}

class EntryViewModel(
    private val repos: Repositories,
    private val prefs: PreferencesRepository,
    private val handle: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val route = handle.toRoute<EntryRoute>()
    private val json = Json { ignoreUnknownKeys = true }

    private val form = MutableStateFlow(
        handle.get<String>(KEY_FORM)?.let { runCatching { json.decodeFromString(EntryForm.serializer(), it) }.getOrNull() }
            ?: EntryForm(occurredAt = clock(), clientRef = UUID.randomUUID().toString()),
    )
    private val errors = MutableStateFlow<List<ValidationError>>(emptyList())
    private val saving = MutableStateFlow(false)
    private val refundInfo = MutableStateFlow<Pair<Long?, String?>>(null to null)
    private val loadFailed = MutableStateFlow(false)
    val voiceDisclosureAccepted: StateFlow<Boolean> = prefs.preferences.map { it.voiceDisclosureAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun acceptVoiceDisclosure() {
        viewModelScope.launch { prefs.setVoiceDisclosureAccepted() }
    }

    private val _events = Channel<EntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val accountsFlow = repos.accounts.observeWithBalances()
    private val categoriesFlow = repos.categories.observeAll()

    val state: StateFlow<EntryUiState> = combine(
        form,
        accountsFlow,
        combine(categoriesFlow, repos.categories.observeRecentlyUsed(TransactionType.EXPENSE), repos.categories.observeRecentlyUsed(TransactionType.INCOME)) { c, re, ri -> Triple(c, re, ri) },
        combine(errors, saving, refundInfo, loadFailed) { e, s, r, l -> listOf(e, s, r, l) },
    ) { f, accountRows, (cats, recentExpense, recentIncome), misc ->
        @Suppress("UNCHECKED_CAST") val errs = misc[0] as List<ValidationError>
        @Suppress("UNCHECKED_CAST") val refund = misc[2] as Pair<Long?, String?>
        val allAccounts = AccountUi.from(accountRows)
        val visibleAccounts = allAccounts.filter { !it.archived || it.id == f.accountId || it.id == f.toAccountId }
        val kind = if (f.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val recent = if (kind == CategoryKind.INCOME) recentIncome else recentExpense
        val selected = visibleAccounts.firstOrNull { it.id == f.accountId }
        val channels = if (selected == null || f.type == TransactionType.TRANSFER) emptyList() else TransactionRules.channelsFor(selected.type)
        EntryUiState(
            form = f,
            accounts = visibleAccounts,
            categories = orderCategories(cats, kind, recent, f.categoryId),
            channels = channels,
            showProviders = f.type != TransactionType.TRANSFER && (f.channel == PaymentChannel.UPI || f.channel == PaymentChannel.WALLET),
            errors = errs,
            saving = misc[1] as Boolean,
            refundLimitPaise = refund.first,
            refundOfLabel = refund.second,
            loadFailed = misc[3] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState(form = form.value))

    init {
        if (!form.value.initialized) viewModelScope.launch { initialise() } else if (form.value.refundOf != null) viewModelScope.launch { loadRefundInfo(form.value.refundOf!!) }
    }

    private suspend fun initialise() {
        val accounts = AccountUi.from(accountsFlow.first())
        val p: UserPreferences = prefs.preferences.first()
        var f = form.value
        when {
            route.editId > 0 -> {
                val t = repos.transactions.getById(route.editId)
                if (t == null) { loadFailed.value = true; return }
                f = f.copy(
                    type = t.type,
                    amountText = AmountInput.fromPaise(t.amountPaise).text,
                    categoryId = t.categoryId,
                    accountId = t.instrumentAccountId ?: t.accountId,
                    toAccountId = t.toAccountId,
                    channel = t.channel,
                    provider = t.provider,
                    note = t.description,
                    occurredAt = t.occurredAt,
                    clientRef = t.clientRef,
                    refundOf = t.refundOfId,
                    editId = t.id,
                )
                t.refundOfId?.let { loadRefundInfo(it) }
            }
            route.refundOf > 0 -> {
                val original = repos.transactions.getById(route.refundOf)
                val remaining = repos.transactions.refundableAmount(route.refundOf)
                if (original == null || remaining == null || remaining <= 0) { loadFailed.value = true; return }
                f = f.copy(
                    type = TransactionType.REFUND,
                    amountText = AmountInput.fromPaise(remaining).text,
                    categoryId = original.categoryId,
                    accountId = original.instrumentAccountId ?: original.accountId,
                    channel = original.channel?.takeIf { it != PaymentChannel.DEBIT_CARD || original.instrumentAccountId != null },
                    provider = original.provider,
                    note = if (original.description.isNotBlank()) "Refund: ${original.description}".take(TransactionRules.MAX_DESCRIPTION) else "",
                    refundOf = original.id,
                )
                loadRefundInfo(original.id)
            }
            else -> {
                val type = runCatching { TransactionType.valueOf(route.type) }.getOrDefault(TransactionType.EXPENSE)
                    .takeIf { it != TransactionType.REFUND } ?: TransactionType.EXPENSE
                val active = accounts.filter { !it.archived }
                val account = active.firstOrNull { it.id == route.accountId }
                    ?: active.firstOrNull { it.id == p.lastAccountId }
                    ?: active.firstOrNull { it.type.isBank }
                    ?: active.firstOrNull()
                f = f.copy(type = type, accountId = account?.id)
                f = withChannelFor(f, account?.type, p.lastChannel, p.lastProvider)
            }
        }
        update(f.copy(initialized = true))
    }

    private suspend fun loadRefundInfo(originalId: Long) {
        val original = repos.transactions.getById(originalId) ?: return
        val editingAmount = form.value.editId?.let { form.value.amount.paise } ?: 0L
        val remaining = (repos.transactions.refundableAmount(originalId) ?: 0L) + editingAmount
        val label = original.description.ifBlank { "expense" } + " · " + Money.format(original.amountPaise)
        refundInfo.value = remaining to label
    }

    private fun withChannelFor(f: EntryForm, type: AccountType?, preferred: PaymentChannel?, preferredProvider: PaymentProvider?): EntryForm {
        if (type == null || f.type == TransactionType.TRANSFER) return f.copy(channel = null, provider = null)
        val options = TransactionRules.channelsFor(type)
        val channel = when {
            f.channel in options -> f.channel
            preferred in options -> preferred
            else -> TransactionRules.defaultChannelFor(type)
        }
        val provider = if (channel == PaymentChannel.UPI || channel == PaymentChannel.WALLET) f.provider ?: preferredProvider.takeIf { preferred == channel } else null
        return f.copy(channel = channel, provider = provider)
    }

    private fun update(f: EntryForm) {
        form.value = f
        handle[KEY_FORM] = json.encodeToString(EntryForm.serializer(), f)
        if (errors.value.isNotEmpty()) errors.value = emptyList()
    }

    // --- Intents from the UI -------------------------------------------------------------------

    fun digit(d: Char) = update(form.value.copy(amountText = form.value.amount.appendDigit(d).text))
    fun decimal() = update(form.value.copy(amountText = form.value.amount.appendDecimal().text))
    fun backspace() = update(form.value.copy(amountText = form.value.amount.backspace().text))
    fun clearAmount() = update(form.value.copy(amountText = ""))

    fun setType(type: TransactionType) {
        if (type == TransactionType.REFUND || form.value.refundOf != null) return
        val f = form.value
        if (f.type == type) return
        val keepCategory = (f.type == TransactionType.INCOME) == (type == TransactionType.INCOME)
        var next = f.copy(type = type, categoryId = if (keepCategory && type != TransactionType.TRANSFER) f.categoryId else null, toAccountId = null)
        val acc = state.value.accounts.firstOrNull { it.id == next.accountId }
        next = withChannelFor(next, acc?.type, null, null)
        update(next)
    }

    fun setCategory(id: Long) = update(form.value.copy(categoryId = id))

    fun setAccount(id: Long) {
        val acc = state.value.accounts.firstOrNull { it.id == id } ?: return
        var f = form.value.copy(accountId = id)
        if (f.toAccountId == id) f = f.copy(toAccountId = null)
        update(withChannelFor(f.copy(channel = f.channel), acc.type, null, null))
    }

    fun setToAccount(id: Long) = update(form.value.copy(toAccountId = id))

    fun setChannel(c: PaymentChannel) {
        val f = form.value
        update(f.copy(channel = c, provider = if (c == PaymentChannel.UPI || c == PaymentChannel.WALLET) f.provider else null))
    }

    fun setProvider(p: PaymentProvider?) = update(form.value.copy(provider = if (form.value.provider == p) null else p))

    fun setNote(text: String) = update(form.value.copy(note = text.take(TransactionRules.MAX_DESCRIPTION)))

    fun setOccurredAt(millis: Long) = update(form.value.copy(occurredAt = millis))

    fun dismissVoiceBanner() = update(form.value.copy(voiceTranscript = null, voiceIssues = emptyList()))

    /** Pre-fills the form from a spoken phrase. Nothing is saved until the user taps Save. */
    fun applyVoice(transcript: String) {
        val s = state.value
        val accounts = s.accounts.filter { !it.archived }
        val categories = categoriesSnapshot
        val r = VoiceParser.parse(
            transcript,
            accounts.map { VoiceAccount(it.id, it.name, it.institution, it.type) },
            categories.filter { !it.archived }.map { VoiceCategory(it.id, it.key, it.name, it.kind) },
        )
        var f = form.value
        val type = if (f.refundOf != null) f.type else when (r.type) {
            TransactionType.INCOME -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
        f = f.copy(
            type = type,
            amountText = r.amountPaise?.let { AmountInput.fromPaise(it).text } ?: f.amountText,
            categoryId = r.categoryId ?: f.categoryId.takeIf { type == f.type },
            accountId = r.accountId ?: f.accountId,
            toAccountId = null,
            note = r.description.ifBlank { f.note },
            voiceTranscript = r.transcript,
            voiceIssues = r.issues,
        )
        if (r.dayOffset != 0) f = f.copy(occurredAt = clock() + r.dayOffset * 86_400_000L)
        val acc = accounts.firstOrNull { it.id == f.accountId }
        f = if (acc != null && r.channel != null && r.channel in TransactionRules.channelsFor(acc.type)) {
            f.copy(channel = r.channel, provider = r.provider.takeIf { r.channel == PaymentChannel.UPI || r.channel == PaymentChannel.WALLET })
        } else {
            withChannelFor(f.copy(channel = null, provider = null), acc?.type, r.channel, r.provider)
        }
        update(f)
    }

    private var categoriesSnapshot: List<CategoryEntity> = emptyList()

    init {
        viewModelScope.launch { categoriesFlow.collect { categoriesSnapshot = it } }
    }

    fun save() {
        if (saving.value) return
        val f = form.value
        saving.value = true
        viewModelScope.launch {
            try {
                val draft = TransactionDraft(
                    type = f.type,
                    amountPaise = f.amount.paise,
                    accountId = f.accountId,
                    toAccountId = f.toAccountId.takeIf { f.type == TransactionType.TRANSFER },
                    categoryId = f.categoryId.takeIf { f.type != TransactionType.TRANSFER },
                    channel = f.channel.takeIf { f.type != TransactionType.TRANSFER },
                    provider = f.provider.takeIf { f.type != TransactionType.TRANSFER },
                    refundOfId = f.refundOf,
                    description = f.note,
                    occurredAt = f.occurredAt,
                )
                when (val r = repos.transactions.save(draft, f.clientRef, f.editId)) {
                    is SaveResult.Saved -> {
                        if (f.type == TransactionType.EXPENSE && f.accountId != null) {
                            runCatching { prefs.rememberPaymentChoice(f.accountId, f.channel, f.provider) }
                        }
                        _events.send(EntryEvent.Saved(r.alert, f.editId != null))
                    }
                    is SaveResult.Duplicate -> _events.send(EntryEvent.Saved(null, f.editId != null))
                    is SaveResult.Invalid -> errors.value = r.errors
                }
            } finally {
                saving.value = false
            }
        }
    }

    private fun orderCategories(all: List<CategoryEntity>, kind: CategoryKind, recent: List<Long>, selected: Long?): List<CategoryUi> {
        val ofKind = all.filter { it.kind == kind && (!it.archived || it.id == selected) }
        val rank = recent.withIndex().associate { (i, id) -> id to i }
        return ofKind.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.sortOrder })).map(CategoryUi::from)
    }

    private companion object { const val KEY_FORM = "entry_form" }
}
