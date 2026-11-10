package io.github.sharveshar.spendly.data.backup

import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.ledger.TransactionRules
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType

sealed interface BackupValidation {
    data class Valid(val snapshot: BackupSnapshot, val summary: BackupSummary) : BackupValidation
    data class Invalid(val problems: List<String>) : BackupValidation
}

/**
 * Checks every record of a decrypted backup before it can replace local data: schema version,
 * limits, unique ids, referential integrity, enum values, amounts and transaction shape.
 * Nothing is written unless the whole snapshot is valid.
 */
object BackupValidator {
    private const val MAX_ACCOUNTS = 1_000
    private const val MAX_CATEGORIES = 1_000
    private const val MAX_TRANSACTIONS = 1_000_000
    private const val MAX_BUDGETS = 1_000
    private const val MAX_BUDGET_OVERRIDES = 20_000
    private val MONTH = Regex("""\d{4}-(0[1-9]|1[0-2])""")
    private const val MAX_PROBLEMS = 10

    fun validate(s: BackupSnapshot): BackupValidation {
        val problems = mutableListOf<String>()
        fun fail(msg: String) { if (problems.size < MAX_PROBLEMS) problems += msg }

        if (s.schema != BackupCodec.SCHEMA_VERSION) {
            return BackupValidation.Invalid(listOf("Unsupported backup schema version ${s.schema}"))
        }
        if (s.accounts.size > MAX_ACCOUNTS || s.categories.size > MAX_CATEGORIES ||
            s.transactions.size > MAX_TRANSACTIONS || s.budgets.size > MAX_BUDGETS || s.budgetOverrides.size > MAX_BUDGET_OVERRIDES
        ) return BackupValidation.Invalid(listOf("Backup contains more records than MNYBOXD supports"))

        // --- Accounts ---
        val accountTypes = HashMap<Long, AccountType>()
        for (a in s.accounts) {
            val type = enumOrNull<AccountType>(a.type)
            when {
                a.id <= 0 -> fail("Account has an invalid id")
                accountTypes.containsKey(a.id) -> fail("Duplicate account id ${a.id}")
                type == null -> fail("Account ${a.id} has an unknown type")
                else -> accountTypes[a.id] = type
            }
            if (!validText(a.name, 1, 40)) fail("Account ${a.id} has an invalid name")
            if (!validText(a.institution, 0, 40)) fail("Account ${a.id} has an invalid institution")
            if (kotlin.math.abs(a.openingBalancePaise) > Money.MAX_PAISE) fail("Account ${a.id} opening balance out of range")
            a.creditLimitPaise?.let { if (it < 0 || it > Money.MAX_PAISE) fail("Account ${a.id} credit limit out of range") }
            if (a.colorIndex !in 0..9) fail("Account ${a.id} has an invalid colour")
        }
        for (a in s.accounts) {
            val type = accountTypes[a.id] ?: continue
            if (type == AccountType.DEBIT_CARD) {
                val linkedType = a.linkedAccountId?.let { accountTypes[it] }
                if (linkedType == null || !linkedType.isBank) fail("Debit card ${a.id} is not linked to a bank account")
                if (a.openingBalancePaise != 0L) fail("Debit card ${a.id} has a balance")
            } else if (a.linkedAccountId != null) {
                fail("Account ${a.id} has an unexpected link")
            }
        }

        // --- Categories ---
        val categoryKinds = HashMap<Long, CategoryKind>()
        val keys = HashSet<String>()
        for (c in s.categories) {
            val kind = enumOrNull<CategoryKind>(c.kind)
            when {
                c.id <= 0 -> fail("Category has an invalid id")
                categoryKinds.containsKey(c.id) -> fail("Duplicate category id ${c.id}")
                kind == null -> fail("Category ${c.id} has an unknown kind")
                else -> categoryKinds[c.id] = kind
            }
            c.key?.let { if (!keys.add(it) || !validText(it, 1, 40)) fail("Category ${c.id} has a duplicate key") }
            if (!validText(c.name, 1, 30)) fail("Category ${c.id} has an invalid name")
            if (!validText(c.iconKey, 1, 30)) fail("Category ${c.id} has an invalid icon")
            if (c.colorIndex !in 0..9) fail("Category ${c.id} has an invalid colour")
        }

        // --- Transactions ---
        val txTypes = HashMap<Long, TransactionType>()
        val txAmounts = HashMap<Long, Long>()
        val refs = HashSet<String>()
        for (t in s.transactions) {
            val type = enumOrNull<TransactionType>(t.type)
            when {
                t.id <= 0 -> fail("Transaction has an invalid id")
                txTypes.containsKey(t.id) -> fail("Duplicate transaction id ${t.id}")
                type == null -> fail("Transaction ${t.id} has an unknown type")
                else -> { txTypes[t.id] = type; txAmounts[t.id] = t.amountPaise }
            }
            if (!validText(t.clientRef, 1, 64) || !refs.add(t.clientRef)) fail("Transaction ${t.id} has a duplicate reference")
        }
        val refundedTotals = HashMap<Long, Long>()
        for (t in s.transactions) {
            val type = txTypes[t.id] ?: continue
            if (!Money.isValidAmount(t.amountPaise)) fail("Transaction ${t.id} has an invalid amount")
            if (!validText(t.description, 0, TransactionRules.MAX_DESCRIPTION)) fail("Transaction ${t.id} has an invalid description")
            if (t.occurredAt < TransactionRules.MIN_OCCURRED_AT) fail("Transaction ${t.id} has an invalid date")
            val accType = accountTypes[t.accountId]
            if (accType == null) fail("Transaction ${t.id} refers to a missing account")
            else if (accType == AccountType.DEBIT_CARD) fail("Transaction ${t.id} is funded by a debit card directly")
            if (t.channel != null && enumOrNull<PaymentChannel>(t.channel) == null) fail("Transaction ${t.id} has an unknown payment method")
            if (t.provider != null && enumOrNull<PaymentProvider>(t.provider) == null) fail("Transaction ${t.id} has an unknown payment app")
            t.instrumentAccountId?.let { if (accountTypes[it] != AccountType.DEBIT_CARD) fail("Transaction ${t.id} has an invalid card") }
            when (type) {
                TransactionType.TRANSFER -> {
                    val toType = t.toAccountId?.let { accountTypes[it] }
                    if (toType == null || toType == AccountType.DEBIT_CARD || t.toAccountId == t.accountId) fail("Transfer ${t.id} has an invalid destination")
                    if (t.categoryId != null) fail("Transfer ${t.id} has a category")
                }
                else -> {
                    if (t.toAccountId != null) fail("Transaction ${t.id} has an unexpected destination")
                    val expected = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                    val kind = t.categoryId?.let { categoryKinds[it] }
                    if (t.categoryId != null && kind != expected) fail("Transaction ${t.id} has an invalid category")
                    if (t.categoryId == null && type != TransactionType.INCOME) fail("Transaction ${t.id} is missing a category")
                }
            }
            if (t.refundOfId != null) {
                if (type != TransactionType.REFUND || txTypes[t.refundOfId] != TransactionType.EXPENSE) {
                    fail("Refund ${t.id} is linked to an invalid transaction")
                } else {
                    refundedTotals[t.refundOfId] = (refundedTotals[t.refundOfId] ?: 0L) + t.amountPaise
                }
            }
        }
        for ((originalId, total) in refundedTotals) {
            if (total > (txAmounts[originalId] ?: 0L)) fail("Refunds exceed expense $originalId")
        }

        // --- Budgets ---
        val budgetIds = HashSet<Long>()
        val budgetCats = HashSet<Long?>()
        for (b in s.budgets) {
            if (b.id <= 0 || !budgetIds.add(b.id)) fail("Budget has an invalid id")
            if (!budgetCats.add(b.categoryId)) fail("Duplicate budget")
            if (!Money.isValidAmount(b.amountPaise)) fail("Budget ${b.id} has an invalid amount")
            b.categoryId?.let { if (categoryKinds[it] != CategoryKind.EXPENSE) fail("Budget ${b.id} has an invalid category") }
        }

        val overrideIds = HashSet<Long>()
        val overrideKeys = HashSet<Pair<String, Long?>>()
        for (b in s.budgetOverrides) {
            if (b.id <= 0 || !overrideIds.add(b.id)) fail("Monthly budget has an invalid id")
            if (!MONTH.matches(b.month)) fail("Monthly budget ${b.id} has an invalid month")
            if (!overrideKeys.add(b.month to b.categoryId)) fail("Duplicate monthly budget")
            if (!Money.isValidAmount(b.amountPaise)) fail("Monthly budget ${b.id} has an invalid amount")
            b.categoryId?.let { if (categoryKinds[it] != CategoryKind.EXPENSE) fail("Monthly budget ${b.id} has an invalid category") }
        }

        if (problems.isNotEmpty()) return BackupValidation.Invalid(problems)
        return BackupValidation.Valid(
            s,
            BackupSummary(s.createdAt, s.accounts.size, s.categories.size, s.transactions.size, s.budgets.size),
        )
    }

    private fun validText(s: String, min: Int, max: Int) = s.length in min..max && s.none { it.isISOControl() }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
}
