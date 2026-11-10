package io.github.sharveshar.spendly.domain.ledger

import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType

/** Minimal view of a transaction needed to compute balances. */
data class LedgerEntry(
    val type: TransactionType,
    val amountPaise: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
)

/**
 * Pure reference implementation of balance rules. The database computes the same result with
 * SQL (see AccountDao.observeBalances); tests assert both agree.
 *
 * Signed net-worth convention (see [AccountType]):
 *  - EXPENSE  : account −amount   (bank/cash/wallet balance falls; card outstanding rises)
 *  - INCOME   : account +amount
 *  - REFUND   : account +amount   (money returns to where it was spent from)
 *  - TRANSFER : from −amount, to +amount  (card repayment = transfer bank → card)
 */
object BalanceCalculator {
    fun effects(entry: LedgerEntry): List<Pair<Long, Long>> = when (entry.type) {
        TransactionType.EXPENSE -> listOf(entry.accountId to -entry.amountPaise)
        TransactionType.INCOME, TransactionType.REFUND -> listOf(entry.accountId to entry.amountPaise)
        TransactionType.TRANSFER -> listOf(
            entry.accountId to -entry.amountPaise,
            requireNotNull(entry.toAccountId) { "Transfer without destination" } to entry.amountPaise,
        )
    }

    /** Returns balances for each id in [openingBalances], applying [entries] with overflow checks. */
    fun balances(openingBalances: Map<Long, Long>, entries: List<LedgerEntry>): Map<Long, Long> {
        val result = openingBalances.toMutableMap()
        for (e in entries) {
            for ((id, delta) in effects(e)) {
                result[id] = Math.addExact(result[id] ?: 0L, delta)
            }
        }
        return result
    }

    /** Amount owed on a credit card given its signed balance. */
    fun outstanding(balancePaise: Long): Long = if (balancePaise < 0) -balancePaise else 0L

    /** Net-worth split: assets are positive balances, liabilities are what is owed. */
    fun netWorth(balances: Collection<Long>): NetWorth {
        var assets = 0L
        var liabilities = 0L
        for (b in balances) if (b >= 0) assets = Math.addExact(assets, b) else liabilities = Math.addExact(liabilities, -b)
        return NetWorth(assets, liabilities)
    }
}

data class NetWorth(val assetsPaise: Long, val liabilitiesPaise: Long) {
    val netPaise: Long get() = assetsPaise - liabilitiesPaise
}

/** Account facts needed for validation. */
data class AccountRef(
    val id: Long,
    val type: AccountType,
    val archived: Boolean,
    val linkedAccountId: Long? = null,
)

data class CategoryRef(val id: Long, val kind: CategoryKind, val archived: Boolean)

/** What the user entered, before resolution and validation. */
data class TransactionDraft(
    val type: TransactionType,
    val amountPaise: Long?,
    /** The account the user picked (may be a debit card; see [TransactionRules.resolve]). */
    val accountId: Long?,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val channel: PaymentChannel? = null,
    val provider: PaymentProvider? = null,
    val refundOfId: Long? = null,
    val description: String = "",
    val occurredAt: Long,
)

/** A draft after debit-card resolution: [accountId] is always a balance-holding account. */
data class ResolvedTransaction(
    val type: TransactionType,
    val amountPaise: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val channel: PaymentChannel?,
    val provider: PaymentProvider?,
    val instrumentAccountId: Long?,
    val refundOfId: Long?,
    val description: String,
    val occurredAt: Long,
)

/** Facts about the original expense when recording a refund. */
data class RefundOriginal(
    val id: Long,
    val type: TransactionType,
    val amountPaise: Long,
    /** Sum of other refunds already recorded against the original (excluding the one being edited). */
    val alreadyRefundedPaise: Long,
    val accountId: Long,
    val categoryId: Long?,
)

enum class ValidationError(val message: String) {
    AMOUNT_MISSING("Enter an amount"),
    AMOUNT_INVALID("Amount must be between ₹0.01 and ₹99,99,99,999.99"),
    ACCOUNT_MISSING("Choose an account"),
    ACCOUNT_UNKNOWN("That account no longer exists"),
    ACCOUNT_ARCHIVED("That account is archived"),
    DEBIT_CARD_UNLINKED("This debit card is not linked to a bank account"),
    DESTINATION_MISSING("Choose where the money goes"),
    DESTINATION_SAME("Choose two different accounts"),
    DESTINATION_UNEXPECTED("Only transfers have a destination account"),
    CATEGORY_MISSING("Choose a category"),
    CATEGORY_UNKNOWN("That category no longer exists"),
    CATEGORY_WRONG_KIND("That category can't be used for this type"),
    CATEGORY_UNEXPECTED("Transfers don't have a category"),
    CHANNEL_MISMATCH("Payment method doesn't match the account"),
    PROVIDER_UNEXPECTED("A payment app only applies to UPI or wallet payments"),
    REFUND_ORIGINAL_INVALID("Refunds can only be linked to an expense"),
    REFUND_EXCEEDS_ORIGINAL("Refund is more than what is left to refund"),
    REFUND_LINK_UNEXPECTED("Only refunds can be linked to an expense"),
    DATE_INVALID("Date is out of range"),
    DESCRIPTION_TOO_LONG("Description is too long"),
    HAS_REFUNDS("This expense has refunds: it must stay an expense of at least the refunded amount"),
    NOT_FOUND("This transaction no longer exists"),
}

sealed interface RuleResult {
    data class Valid(val transaction: ResolvedTransaction) : RuleResult
    data class Invalid(val errors: List<ValidationError>) : RuleResult
}

object TransactionRules {
    const val MAX_DESCRIPTION = 120
    /** 1 Jan 2000 UTC. */
    const val MIN_OCCURRED_AT = 946_684_800_000L
    /** Future dates are allowed up to one day ahead (time-zone slack), not beyond. */
    const val FUTURE_SLACK_MS = 24L * 60 * 60 * 1000

    /** Removes control characters and collapses whitespace. */
    fun sanitizeDescription(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim()

    /**
     * Validates [draft] against the current [accounts] and [categories] and resolves debit-card
     * payments to their linked bank account.
     *
     * @param editing true when updating an existing record: archived accounts/categories it already
     *   used remain acceptable so old records can still be corrected.
     */
    fun resolve(
        draft: TransactionDraft,
        accounts: Map<Long, AccountRef>,
        categories: Map<Long, CategoryRef>,
        now: Long,
        refundOriginal: RefundOriginal? = null,
        editing: Boolean = false,
    ): RuleResult {
        val errors = mutableListOf<ValidationError>()

        val amount = draft.amountPaise
        when {
            amount == null -> errors += ValidationError.AMOUNT_MISSING
            !Money.isValidAmount(amount) -> errors += ValidationError.AMOUNT_INVALID
        }

        val description = sanitizeDescription(draft.description)
        if (description.length > MAX_DESCRIPTION) errors += ValidationError.DESCRIPTION_TOO_LONG

        if (draft.occurredAt < MIN_OCCURRED_AT || draft.occurredAt > now + FUTURE_SLACK_MS) {
            errors += ValidationError.DATE_INVALID
        }

        // --- Account resolution -------------------------------------------------------------
        var fundingId: Long? = null
        var instrumentId: Long? = null
        var channel = draft.channel
        val picked = draft.accountId?.let { accounts[it] }
        when {
            draft.accountId == null -> errors += ValidationError.ACCOUNT_MISSING
            picked == null -> errors += ValidationError.ACCOUNT_UNKNOWN
            picked.archived && !editing -> errors += ValidationError.ACCOUNT_ARCHIVED
            picked.type == AccountType.DEBIT_CARD -> {
                val linked = picked.linkedAccountId?.let { accounts[it] }
                if (linked == null || !linked.type.holdsBalance || linked.type == AccountType.CREDIT_CARD) {
                    errors += ValidationError.DEBIT_CARD_UNLINKED
                } else if (linked.archived && !editing) {
                    errors += ValidationError.ACCOUNT_ARCHIVED
                } else {
                    fundingId = linked.id
                    instrumentId = picked.id
                    if (draft.type != TransactionType.TRANSFER) channel = PaymentChannel.DEBIT_CARD
                }
            }
            else -> fundingId = picked.id
        }
        val funding = fundingId?.let { accounts[it] }

        // --- Destination --------------------------------------------------------------------
        var toId: Long? = null
        if (draft.type == TransactionType.TRANSFER) {
            val dest = draft.toAccountId?.let { accounts[it] }
            when {
                draft.toAccountId == null -> errors += ValidationError.DESTINATION_MISSING
                dest == null -> errors += ValidationError.ACCOUNT_UNKNOWN
                dest.archived && !editing -> errors += ValidationError.ACCOUNT_ARCHIVED
                dest.type == AccountType.DEBIT_CARD -> {
                    val linked = dest.linkedAccountId?.let { accounts[it] }
                    if (linked == null) errors += ValidationError.DEBIT_CARD_UNLINKED else toId = linked.id
                }
                else -> toId = dest.id
            }
            if (toId != null && fundingId != null && toId == fundingId) errors += ValidationError.DESTINATION_SAME
        } else if (draft.toAccountId != null) {
            errors += ValidationError.DESTINATION_UNEXPECTED
        }

        // --- Category -----------------------------------------------------------------------
        var categoryId = draft.categoryId
        when (draft.type) {
            TransactionType.TRANSFER -> if (categoryId != null) errors += ValidationError.CATEGORY_UNEXPECTED
            TransactionType.EXPENSE, TransactionType.REFUND, TransactionType.INCOME -> {
                if (draft.type == TransactionType.REFUND && categoryId == null) categoryId = refundOriginal?.categoryId
                val cat = categoryId?.let { categories[it] }
                val expectedKind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                when {
                    categoryId == null -> if (draft.type != TransactionType.INCOME) errors += ValidationError.CATEGORY_MISSING
                    cat == null -> errors += ValidationError.CATEGORY_UNKNOWN
                    cat.kind != expectedKind -> errors += ValidationError.CATEGORY_WRONG_KIND
                }
            }
        }

        // --- Channel / provider consistency -------------------------------------------------
        if (funding != null && channel != null && draft.type != TransactionType.TRANSFER) {
            val ok = when (channel) {
                PaymentChannel.CASH -> funding.type == AccountType.CASH
                // A card kept as its own account, or simply "paid by card" from a bank account.
                PaymentChannel.DEBIT_CARD -> instrumentId != null || funding.type.isBank
                PaymentChannel.CREDIT_CARD -> funding.type == AccountType.CREDIT_CARD
                PaymentChannel.WALLET -> funding.type == AccountType.WALLET
                PaymentChannel.UPI -> funding.type != AccountType.CASH
                PaymentChannel.NET_BANKING, PaymentChannel.BANK_TRANSFER -> funding.type.isBank
                PaymentChannel.OTHER -> true
            }
            if (!ok) errors += ValidationError.CHANNEL_MISMATCH
        }
        val provider = draft.provider
        if (provider != null && channel != PaymentChannel.UPI && channel != PaymentChannel.WALLET) {
            errors += ValidationError.PROVIDER_UNEXPECTED
        }

        // --- Refund link --------------------------------------------------------------------
        if (draft.refundOfId != null && draft.type != TransactionType.REFUND) errors += ValidationError.REFUND_LINK_UNEXPECTED
        if (draft.type == TransactionType.REFUND && draft.refundOfId != null) {
            if (refundOriginal == null || refundOriginal.id != draft.refundOfId || refundOriginal.type != TransactionType.EXPENSE) {
                errors += ValidationError.REFUND_ORIGINAL_INVALID
            } else if (amount != null && amount > refundOriginal.amountPaise - refundOriginal.alreadyRefundedPaise) {
                errors += ValidationError.REFUND_EXCEEDS_ORIGINAL
            }
        }

        if (errors.isNotEmpty()) return RuleResult.Invalid(errors.distinct())
        return RuleResult.Valid(
            ResolvedTransaction(
                type = draft.type,
                amountPaise = amount!!,
                accountId = fundingId!!,
                toAccountId = toId,
                categoryId = if (draft.type == TransactionType.TRANSFER) null else categoryId,
                channel = channel,
                provider = provider,
                instrumentAccountId = instrumentId,
                refundOfId = draft.refundOfId,
                description = description,
                occurredAt = draft.occurredAt,
            ),
        )
    }

    /** Sensible default payment channel for an account type. */
    fun defaultChannelFor(type: AccountType): PaymentChannel = when (type) {
        AccountType.CASH -> PaymentChannel.CASH
        AccountType.CREDIT_CARD -> PaymentChannel.CREDIT_CARD
        AccountType.DEBIT_CARD -> PaymentChannel.DEBIT_CARD
        AccountType.WALLET -> PaymentChannel.WALLET
        AccountType.SAVINGS, AccountType.CURRENT -> PaymentChannel.UPI
    }

    /** Channels that make sense for a picked account type, in display order. */
    fun channelsFor(type: AccountType): List<PaymentChannel> = when (type) {
        AccountType.CASH -> listOf(PaymentChannel.CASH)
        AccountType.CREDIT_CARD -> listOf(PaymentChannel.CREDIT_CARD, PaymentChannel.UPI, PaymentChannel.OTHER)
        AccountType.DEBIT_CARD -> listOf(PaymentChannel.DEBIT_CARD)
        AccountType.WALLET -> listOf(PaymentChannel.WALLET, PaymentChannel.UPI, PaymentChannel.OTHER)
        AccountType.SAVINGS, AccountType.CURRENT ->
            listOf(PaymentChannel.UPI, PaymentChannel.DEBIT_CARD, PaymentChannel.NET_BANKING, PaymentChannel.BANK_TRANSFER, PaymentChannel.OTHER)
    }
}
