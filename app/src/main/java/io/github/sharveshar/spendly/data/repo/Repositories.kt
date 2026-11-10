package io.github.sharveshar.spendly.data.repo

import androidx.room.withTransaction
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.AccountEntity
import io.github.sharveshar.spendly.data.db.AccountWithBalance
import io.github.sharveshar.spendly.data.db.BudgetEntity
import io.github.sharveshar.spendly.data.db.BudgetOverrideEntity
import io.github.sharveshar.spendly.data.db.CategoryEntity
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.domain.ledger.TransactionRules
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

sealed interface WriteResult {
    data class Ok(val id: Long) : WriteResult
    data class Rejected(val reason: String) : WriteResult
}

data class AccountInput(
    val name: String,
    val institution: String,
    val type: AccountType,
    /** For credit cards this is the amount currently owed (entered as a positive number). */
    val openingAmountPaise: Long,
    val linkedAccountId: Long?,
    val creditLimitPaise: Long?,
    val colorIndex: Int,
)

class AccountRepository(private val db: SpendlyDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.accountDao()

    fun observeWithBalances(): Flow<List<AccountWithBalance>> = dao.observeWithBalances()

    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun isReferenced(id: Long) = dao.countReferences(id) > 0

    suspend fun create(input: AccountInput): WriteResult = db.withTransaction {
        validate(input, editingId = null)?.let { return@withTransaction WriteResult.Rejected(it) }
        val id = dao.insert(
            AccountEntity(
                name = clean(input.name),
                institution = clean(input.institution),
                type = input.type,
                openingBalancePaise = signedOpening(input),
                linkedAccountId = input.linkedAccountId.takeIf { input.type == AccountType.DEBIT_CARD },
                creditLimitPaise = input.creditLimitPaise.takeIf { input.type == AccountType.CREDIT_CARD },
                colorIndex = input.colorIndex.coerceIn(0, 9),
                sortOrder = dao.maxSortOrder() + 1,
                createdAt = clock(),
            ),
        )
        WriteResult.Ok(id)
    }

    suspend fun update(id: Long, input: AccountInput): WriteResult = db.withTransaction {
        val existing = dao.getById(id) ?: return@withTransaction WriteResult.Rejected("Account not found")
        validate(input, editingId = id)?.let { return@withTransaction WriteResult.Rejected(it) }
        if (existing.type != input.type && dao.countReferences(id) > 0) {
            return@withTransaction WriteResult.Rejected("The type of an account with transactions can't be changed")
        }
        dao.update(
            existing.copy(
                name = clean(input.name),
                institution = clean(input.institution),
                type = input.type,
                openingBalancePaise = signedOpening(input),
                linkedAccountId = input.linkedAccountId.takeIf { input.type == AccountType.DEBIT_CARD },
                creditLimitPaise = input.creditLimitPaise.takeIf { input.type == AccountType.CREDIT_CARD },
                colorIndex = input.colorIndex.coerceIn(0, 9),
            ),
        )
        WriteResult.Ok(id)
    }

    suspend fun setArchived(id: Long, archived: Boolean): WriteResult = db.withTransaction {
        val existing = dao.getById(id) ?: return@withTransaction WriteResult.Rejected("Account not found")
        dao.update(existing.copy(archived = archived))
        WriteResult.Ok(id)
    }

    /** Deletes only accounts that nothing refers to; otherwise the user must archive instead. */
    suspend fun deleteIfUnused(id: Long): WriteResult = db.withTransaction {
        if (dao.countReferences(id) > 0) return@withTransaction WriteResult.Rejected("This account has transactions. Archive it instead.")
        dao.deleteById(id)
        WriteResult.Ok(id)
    }

    private suspend fun validate(input: AccountInput, editingId: Long?): String? {
        val name = clean(input.name)
        if (name.isEmpty()) return "Enter an account name"
        if (name.length > 40) return "Name must be 40 characters or fewer"
        if (clean(input.institution).length > 40) return "Institution must be 40 characters or fewer"
        if (input.openingAmountPaise < 0 || input.openingAmountPaise > Money.MAX_PAISE) return "Opening amount is out of range"
        if (input.type == AccountType.DEBIT_CARD) {
            if (input.openingAmountPaise != 0L) return "Debit cards use their bank's balance"
            val linkedId = input.linkedAccountId ?: return "Link the debit card to a bank account"
            if (linkedId == editingId) return "A card can't be linked to itself"
            val linked = dao.getById(linkedId) ?: return "Linked account not found"
            if (!linked.type.isBank) return "Debit cards must be linked to a savings or current account"
        }
        if (editingId != null && input.type != AccountType.DEBIT_CARD) {
            // An account other cards point at must remain a bank account.
            val existing = dao.getById(editingId)
            if (existing != null && existing.type.isBank && !input.type.isBank &&
                dao.getAll().any { it.linkedAccountId == editingId }
            ) return "Debit cards are linked to this account"
        }
        input.creditLimitPaise?.let { if (it < 0 || it > Money.MAX_PAISE) return "Credit limit is out of range" }
        return null
    }

    private fun signedOpening(input: AccountInput): Long = when (input.type) {
        AccountType.CREDIT_CARD -> -input.openingAmountPaise
        AccountType.DEBIT_CARD -> 0
        else -> input.openingAmountPaise
    }

    private fun clean(s: String) = TransactionRules.sanitizeDescription(s)
}

class CategoryRepository(private val db: SpendlyDatabase) {
    private val dao = db.categoryDao()

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    fun observeRecentlyUsed(type: TransactionType, limit: Int = 6): Flow<List<Long>> =
        dao.observeRecentlyUsed(type.name, limit)

    suspend fun create(name: String, kind: CategoryKind, iconKey: String, colorIndex: Int): WriteResult = db.withTransaction {
        val clean = TransactionRules.sanitizeDescription(name)
        validateName(clean)?.let { return@withTransaction WriteResult.Rejected(it) }
        if (dao.findByName(kind.name, clean) != null) return@withTransaction WriteResult.Rejected("A category with that name already exists")
        val id = dao.insert(
            CategoryEntity(name = clean, kind = kind, iconKey = iconKey, colorIndex = colorIndex.coerceIn(0, 9), sortOrder = dao.maxSortOrder() + 1),
        )
        WriteResult.Ok(id)
    }

    suspend fun update(id: Long, name: String, iconKey: String, colorIndex: Int): WriteResult = db.withTransaction {
        val existing = dao.getById(id) ?: return@withTransaction WriteResult.Rejected("Category not found")
        val clean = TransactionRules.sanitizeDescription(name)
        validateName(clean)?.let { return@withTransaction WriteResult.Rejected(it) }
        val clash = dao.findByName(existing.kind.name, clean)
        if (clash != null && clash.id != id) return@withTransaction WriteResult.Rejected("A category with that name already exists")
        dao.update(existing.copy(name = clean, iconKey = iconKey, colorIndex = colorIndex.coerceIn(0, 9)))
        WriteResult.Ok(id)
    }

    suspend fun setArchived(id: Long, archived: Boolean): WriteResult = db.withTransaction {
        val existing = dao.getById(id) ?: return@withTransaction WriteResult.Rejected("Category not found")
        dao.update(existing.copy(archived = archived))
        WriteResult.Ok(id)
    }

    suspend fun deleteIfUnused(id: Long): WriteResult = db.withTransaction {
        if (dao.countTransactions(id) > 0) return@withTransaction WriteResult.Rejected("This category is used by transactions. Archive it instead.")
        dao.deleteById(id) // budgets for it cascade
        WriteResult.Ok(id)
    }

    private fun validateName(name: String): String? = when {
        name.isEmpty() -> "Enter a name"
        name.length > 30 -> "Name must be 30 characters or fewer"
        else -> null
    }
}

/**
 * The limit that applies in one month: the usual recurring one, or the amount set for that month
 * alone. [categoryId] null is the overall budget.
 */
data class MonthBudget(
    val categoryId: Long?,
    val amountPaise: Long,
    /** Set for this month only (overrides [usualPaise], if there is one). */
    val custom: Boolean,
    /** The recurring limit, if one exists. */
    val usualPaise: Long?,
)

/** Every budget, recurring and month-specific; [forMonth] works out what applies when. */
data class BudgetBook(val usual: List<BudgetEntity> = emptyList(), val overrides: List<BudgetOverrideEntity> = emptyList()) {
    fun forMonth(month: YearMonth): List<MonthBudget> {
        val key = month.toString()
        val custom = overrides.filter { it.month == key }.associateBy { it.categoryId }
        val usualByCategory = usual.associateBy { it.categoryId }
        return (usualByCategory.keys + custom.keys).map { id ->
            val o = custom[id]
            val u = usualByCategory[id]
            MonthBudget(id, o?.amountPaise ?: u!!.amountPaise, custom = o != null, usualPaise = u?.amountPaise)
        }
    }

    /** The overall budget for [month], if any. */
    fun overall(month: YearMonth): MonthBudget? = forMonth(month).firstOrNull { it.categoryId == null }
}

class BudgetRepository(private val db: SpendlyDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.budgetDao()

    /** The recurring budgets only. Use [observeBook] to know what applies in a given month. */
    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    fun observeBook(): Flow<BudgetBook> = combine(dao.observeAll(), dao.observeOverrides(), ::BudgetBook)

    suspend fun book(): BudgetBook = BudgetBook(dao.getAll(), dao.getOverrides())

    private suspend fun rejection(categoryId: Long?, amountPaise: Long): WriteResult.Rejected? {
        if (!Money.isValidAmount(amountPaise)) return WriteResult.Rejected("Enter a budget amount")
        if (categoryId != null) {
            val cat = db.categoryDao().getById(categoryId) ?: return WriteResult.Rejected("Category not found")
            if (cat.kind != CategoryKind.EXPENSE) return WriteResult.Rejected("Budgets apply to expense categories")
        }
        return null
    }

    /** Creates or replaces the recurring monthly limit for [categoryId] (null = overall budget). */
    suspend fun setLimit(categoryId: Long?, amountPaise: Long): WriteResult = db.withTransaction {
        rejection(categoryId, amountPaise)?.let { return@withTransaction it }
        val existing = if (categoryId == null) dao.getOverall() else dao.getForCategory(categoryId)
        val now = clock()
        val id = if (existing != null) {
            dao.update(existing.copy(amountPaise = amountPaise, updatedAt = now))
            existing.id
        } else {
            dao.insert(BudgetEntity(categoryId = categoryId, amountPaise = amountPaise, updatedAt = now))
        }
        WriteResult.Ok(id)
    }

    /** Creates or replaces the limit for [month] alone; other months keep the recurring one. */
    suspend fun setMonthLimit(month: YearMonth, categoryId: Long?, amountPaise: Long): WriteResult = db.withTransaction {
        rejection(categoryId, amountPaise)?.let { return@withTransaction it }
        val existing = dao.getOverridesFor(month.toString()).firstOrNull { it.categoryId == categoryId }
        val now = clock()
        val id = if (existing != null) {
            dao.updateOverride(existing.copy(amountPaise = amountPaise, updatedAt = now))
            existing.id
        } else {
            dao.insertOverride(BudgetOverrideEntity(month = month.toString(), categoryId = categoryId, amountPaise = amountPaise, updatedAt = now))
        }
        WriteResult.Ok(id)
    }

    /** Sets the recurring limit and drops [month]'s own amount, so that month follows it again. */
    suspend fun setLimitFrom(month: YearMonth, categoryId: Long?, amountPaise: Long): WriteResult = db.withTransaction {
        val r = setLimit(categoryId, amountPaise)
        if (r is WriteResult.Ok) clearMonthLimit(month, categoryId)
        r
    }

    /** [month] goes back to the recurring limit (or to no budget, if there isn't one). */
    suspend fun clearMonthLimit(month: YearMonth, categoryId: Long?) = db.withTransaction {
        dao.getOverridesFor(month.toString()).filter { it.categoryId == categoryId }.forEach { dao.deleteOverrideById(it.id) }
    }

    /** Removes the recurring limit for [categoryId]. Amounts set for single months stay. */
    suspend fun removeUsual(categoryId: Long?) = db.withTransaction {
        (if (categoryId == null) dao.getOverall() else dao.getForCategory(categoryId))?.let { dao.deleteById(it.id) }
    }

    suspend fun remove(id: Long) = dao.deleteById(id)
}
