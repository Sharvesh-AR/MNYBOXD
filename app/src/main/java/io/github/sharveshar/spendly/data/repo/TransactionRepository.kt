package io.github.sharveshar.spendly.data.repo

import androidx.room.withTransaction
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.db.TransactionEntity
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.insights.BudgetStatus
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.SpendRecord
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.domain.ledger.AccountRef
import io.github.sharveshar.spendly.domain.ledger.CategoryRef
import io.github.sharveshar.spendly.domain.ledger.RefundOriginal
import io.github.sharveshar.spendly.domain.ledger.RuleResult
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.ledger.TransactionRules
import io.github.sharveshar.spendly.domain.ledger.ValidationError
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class BudgetAlert(val level: BudgetLevel, val categoryName: String?, val percentUsed: Int)

sealed interface SaveResult {
    data class Saved(val id: Long, val alert: BudgetAlert?) : SaveResult
    /** The same entry form was already saved (double tap / retry); nothing new was written. */
    data class Duplicate(val existingId: Long) : SaveResult
    data class Invalid(val errors: List<ValidationError>) : SaveResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotFound : DeleteResult
    data class HasRefunds(val count: Int) : DeleteResult
}

data class TransactionFilter(
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val fromMillis: Long = 0,
    val toMillis: Long = Long.MAX_VALUE,
    val query: String = "",
)

class TransactionRepository(
    private val db: SpendlyDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.transactionDao()

    fun observeRecent(limit: Int): Flow<List<TransactionRow>> = dao.observeRecent(limit)

    fun observeRow(id: Long): Flow<TransactionRow?> = dao.observeRow(id)

    fun observeRefundsOf(id: Long): Flow<List<TransactionRow>> = dao.observeRefundsOf(id)

    fun observeSpendRecords(range: TimeRange): Flow<List<SpendRecord>> =
        dao.observeSpendRecords(range.startMillis, range.endMillis)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeFiltered(filter: TransactionFilter, limit: Int = 2_000): Flow<List<TransactionRow>> {
        val q = filter.query.trim().take(60)
        return dao.observeFiltered(
            type = filter.type?.name,
            accountId = filter.accountId,
            categoryId = filter.categoryId,
            fromMillis = filter.fromMillis,
            toMillis = filter.toMillis,
            query = if (q.isEmpty()) null else likePattern(q),
            amountPaise = if (q.isEmpty()) null else Money.parseRupees(q),
            limit = limit,
        )
    }

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    /** Remaining refundable amount for an expense. */
    suspend fun refundableAmount(originalId: Long): Long? {
        val original = dao.getById(originalId) ?: return null
        if (original.type != TransactionType.EXPENSE) return null
        return original.amountPaise - dao.refundedTotal(originalId, -1)
    }

    /**
     * Validates and stores a transaction atomically.
     *
     * @param clientRef idempotency key created when the form opened; saving the same form twice
     *   returns [SaveResult.Duplicate] instead of inserting a second record.
     * @param editingId id of the record being edited, or null to insert.
     */
    suspend fun save(draft: TransactionDraft, clientRef: String, editingId: Long? = null): SaveResult = db.withTransaction {
        if (editingId == null) {
            dao.findIdByClientRef(clientRef)?.let { return@withTransaction SaveResult.Duplicate(it) }
        }
        val existing = editingId?.let { dao.getById(it) ?: return@withTransaction SaveResult.Invalid(listOf(ValidationError.NOT_FOUND)) }

        val accounts = db.accountDao().getAll().associate { it.id to AccountRef(it.id, it.type, it.archived, it.linkedAccountId) }
        val categories = db.categoryDao().getAll().associate { it.id to CategoryRef(it.id, it.kind, it.archived) }
        val refundOriginal = draft.refundOfId?.let { originalId ->
            dao.getById(originalId)?.let { o ->
                RefundOriginal(o.id, o.type, o.amountPaise, dao.refundedTotal(o.id, editingId ?: -1), o.accountId, o.categoryId)
            }
        }
        val now = clock()

        val result = TransactionRules.resolve(draft, accounts, categories, now, refundOriginal, editing = editingId != null)
        if (result is RuleResult.Invalid) return@withTransaction SaveResult.Invalid(result.errors)
        val t = (result as RuleResult.Valid).transaction

        if (existing != null) {
            val refunded = dao.refundedTotal(existing.id, -1)
            if (refunded > 0 && (t.type != TransactionType.EXPENSE || t.amountPaise < refunded)) {
                return@withTransaction SaveResult.Invalid(listOf(ValidationError.HAS_REFUNDS))
            }
        }

        val month = YearMonth.from(Instant.ofEpochMilli(t.occurredAt).atZone(zone()))
        val range = TimeRange.month(month, zone())
        val before = if (affectsBudgets(t.type)) budgetStatuses(month, range) else emptyMap()

        val entity = TransactionEntity(
            id = existing?.id ?: 0,
            type = t.type,
            amountPaise = t.amountPaise,
            accountId = t.accountId,
            toAccountId = t.toAccountId,
            categoryId = t.categoryId,
            channel = t.channel,
            provider = t.provider,
            instrumentAccountId = t.instrumentAccountId,
            refundOfId = t.refundOfId,
            description = t.description,
            occurredAt = t.occurredAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            clientRef = existing?.clientRef ?: clientRef,
        )
        val id = if (existing != null) {
            dao.update(entity)
            existing.id
        } else {
            dao.insert(entity)
        }

        val alert = if (affectsBudgets(t.type)) {
            val after = budgetStatuses(month, range)
            worstCrossing(before, after, t.categoryId)
        } else null
        SaveResult.Saved(id, alert)
    }

    suspend fun delete(id: Long): DeleteResult = db.withTransaction {
        dao.getById(id) ?: return@withTransaction DeleteResult.NotFound
        val refunds = dao.countRefundsOf(id)
        if (refunds > 0) return@withTransaction DeleteResult.HasRefunds(refunds)
        dao.deleteById(id)
        DeleteResult.Deleted
    }

    /**
     * Deletes like [delete] and also returns the removed row, so the UI can offer Undo. The row
     * is held only in memory; nothing extra is stored.
     */
    suspend fun deleteForUndo(id: Long): Pair<DeleteResult, TransactionEntity?> = db.withTransaction {
        val existing = dao.getById(id) ?: return@withTransaction DeleteResult.NotFound to null
        val refunds = dao.countRefundsOf(id)
        if (refunds > 0) return@withTransaction DeleteResult.HasRefunds(refunds) to null
        dao.deleteById(id)
        DeleteResult.Deleted to existing
    }

    /**
     * Undo for [deleteForUndo]: puts the exact row back (same id, amount, accounts and dates), so
     * every balance and total returns to what it was. Returns false if it can't be restored,
     * e.g. an account it used was deleted in the meantime.
     */
    suspend fun restoreDeleted(row: TransactionEntity): Boolean = try {
        db.withTransaction {
            if (dao.getById(row.id) != null) return@withTransaction false
            db.maintenanceDao().insertTransactions(listOf(row))
            true
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        // A constraint failed (SQLite or SQLCipher exception): leave the data as it is.
        false
    }

    private fun affectsBudgets(type: TransactionType) = type == TransactionType.EXPENSE || type == TransactionType.REFUND

    /** Budget status keyed by category id (null = overall) for budgets that exist. */
    private suspend fun budgetStatuses(month: YearMonth, range: TimeRange): Map<Long?, BudgetStatus> {
        val budgets = BudgetBook(db.budgetDao().getAll(), db.budgetDao().getOverridesFor(month.toString())).forMonth(month)
        if (budgets.isEmpty()) return emptyMap()
        val records = dao.getSpendRecords(range.startMillis, range.endMillis)
        val total = Insights.netSpend(records)
        val byCategory = Insights.byCategory(records).toMap()
        return budgets.associate { b ->
            b.categoryId to BudgetStatus(b.amountPaise, if (b.categoryId == null) total else byCategory[b.categoryId] ?: 0L)
        }
    }

    private suspend fun worstCrossing(before: Map<Long?, BudgetStatus>, after: Map<Long?, BudgetStatus>, categoryId: Long?): BudgetAlert? {
        val candidates = listOfNotNull(
            categoryId?.let { id -> after[id]?.let { a -> before[id]?.let { b -> BudgetStatus.crossed(b, a) }?.let { Triple(id, it, a) } } },
            after[null]?.let { a -> before[null]?.let { b -> BudgetStatus.crossed(b, a) }?.let { Triple<Long?, BudgetLevel, BudgetStatus>(null, it, a) } },
        )
        val worst = candidates.maxByOrNull { it.second.ordinal } ?: return null
        val name = worst.first?.let { db.categoryDao().getById(it)?.name }
        return BudgetAlert(worst.second, name, worst.third.percentUsed)
    }

    companion object {
        /** Escapes LIKE wildcards so user text is matched literally. */
        fun likePattern(query: String): String {
            val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
