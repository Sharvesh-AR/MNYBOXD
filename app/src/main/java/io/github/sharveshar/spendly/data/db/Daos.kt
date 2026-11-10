package io.github.sharveshar.spendly.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.sharveshar.spendly.domain.insights.SpendRecord
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "balance_paise") val balancePaise: Long,
)

/** Flattened row for transaction lists. All joins are parameterised; no SQL is built from strings. */
data class TransactionRow(
    val id: Long,
    val type: TransactionType,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val description: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val channel: PaymentChannel?,
    val provider: PaymentProvider?,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long?,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "refund_of_id") val refundOfId: Long?,
    @ColumnInfo(name = "instrument_account_id") val instrumentAccountId: Long?,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "account_type") val accountType: AccountType,
    @ColumnInfo(name = "to_account_name") val toAccountName: String?,
    @ColumnInfo(name = "instrument_name") val instrumentName: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_icon") val categoryIcon: String?,
    @ColumnInfo(name = "category_color") val categoryColor: Int?,
)

private const val ROW_SELECT = """
    SELECT t.id, t.type, t.amount_paise, t.description, t.occurred_at, t.channel, t.provider,
           t.account_id, t.to_account_id, t.category_id, t.refund_of_id, t.instrument_account_id,
           a.name AS account_name, a.type AS account_type, ta.name AS to_account_name,
           ia.name AS instrument_name, c.name AS category_name, c.icon_key AS category_icon,
           c.color_index AS category_color
    FROM transactions t
    JOIN accounts a ON a.id = t.account_id
    LEFT JOIN accounts ta ON ta.id = t.to_account_id
    LEFT JOIN accounts ia ON ia.id = t.instrument_account_id
    LEFT JOIN categories c ON c.id = t.category_id
"""

@Dao
interface AccountDao {
    /**
     * Current balance of every account, computed in SQL with exact 64-bit integer sums.
     * Mirrors BalanceCalculator: income/refund add, expense/transfer-out subtract, transfer-in adds.
     */
    @Query(
        """
        SELECT a.*, a.opening_balance_paise
          + COALESCE((SELECT SUM(CASE WHEN t.type IN ('INCOME', 'REFUND') THEN t.amount_paise ELSE -t.amount_paise END)
                      FROM transactions t WHERE t.account_id = a.id), 0)
          + COALESCE((SELECT SUM(t.amount_paise) FROM transactions t
                      WHERE t.to_account_id = a.id AND t.type = 'TRANSFER'), 0) AS balance_paise
        FROM accounts a
        ORDER BY a.is_archived, a.sort_order, a.id
        """,
    )
    fun observeWithBalances(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts ORDER BY is_archived, sort_order, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE account_id = :id OR to_account_id = :id OR instrument_account_id = :id)
             + (SELECT COUNT(*) FROM accounts WHERE linked_account_id = :id)
        """,
    )
    suspend fun countReferences(id: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM accounts")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY is_archived, kind, sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE kind = :kind AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(kind: String, name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun countTransactions(id: Long): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    /** Most recently used expense categories, newest first. */
    @Query(
        """
        SELECT category_id FROM transactions
        WHERE category_id IS NOT NULL AND type = :type
        GROUP BY category_id ORDER BY MAX(occurred_at) DESC LIMIT :limit
        """,
    )
    fun observeRecentlyUsed(type: String, limit: Int): Flow<List<Long>>
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT id FROM transactions WHERE client_ref = :clientRef")
    suspend fun findIdByClientRef(clientRef: String): Long?

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE refund_of_id = :originalId AND type = 'REFUND' AND id != :excludeId")
    suspend fun refundedTotal(originalId: Long, excludeId: Long): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE refund_of_id = :id")
    suspend fun countRefundsOf(id: Long): Int

    @Query("$ROW_SELECT WHERE t.id = :id")
    fun observeRow(id: Long): Flow<TransactionRow?>

    @Query("$ROW_SELECT ORDER BY t.occurred_at DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT WHERE t.refund_of_id = :originalId ORDER BY t.occurred_at")
    fun observeRefundsOf(originalId: Long): Flow<List<TransactionRow>>

    /**
     * Filtered history. [query] must already be a LIKE pattern with %, _ and \ escaped
     * (see TransactionRepository.likePattern). Every value is bound as a parameter.
     */
    @Query(
        """
        $ROW_SELECT
        WHERE (:type IS NULL OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId OR t.to_account_id = :accountId OR t.instrument_account_id = :accountId)
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
          AND t.occurred_at >= :fromMillis AND t.occurred_at < :toMillis
          AND (:query IS NULL
               OR t.description LIKE :query ESCAPE '\'
               OR c.name LIKE :query ESCAPE '\'
               OR a.name LIKE :query ESCAPE '\'
               OR (:amountPaise IS NOT NULL AND t.amount_paise = :amountPaise))
        ORDER BY t.occurred_at DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeFiltered(
        type: String?,
        accountId: Long?,
        categoryId: Long?,
        fromMillis: Long,
        toMillis: Long,
        query: String?,
        amountPaise: Long?,
        limit: Int,
    ): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT type, amount_paise AS amountPaise, category_id AS categoryId, channel, occurred_at AS occurredAt
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND', 'INCOME') AND occurred_at >= :fromMillis AND occurred_at < :toMillis
        """,
    )
    fun observeSpendRecords(fromMillis: Long, toMillis: Long): Flow<List<SpendRecord>>

    @Query(
        """
        SELECT type, amount_paise AS amountPaise, category_id AS categoryId, channel, occurred_at AS occurredAt
        FROM transactions
        WHERE type IN ('EXPENSE', 'REFUND', 'INCOME') AND occurred_at >= :fromMillis AND occurred_at < :toMillis
        """,
    )
    suspend fun getSpendRecords(fromMillis: Long, toMillis: Long): List<SpendRecord>

    @Query("SELECT * FROM transactions ORDER BY occurred_at, id")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("$ROW_SELECT ORDER BY t.occurred_at, t.id")
    suspend fun getAllRows(): List<TransactionRow>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE category_id IS NULL LIMIT 1")
    suspend fun getOverall(): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId LIMIT 1")
    suspend fun getForCategory(categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budget_overrides")
    fun observeOverrides(): Flow<List<BudgetOverrideEntity>>

    @Query("SELECT * FROM budget_overrides ORDER BY id")
    suspend fun getOverrides(): List<BudgetOverrideEntity>

    @Query("SELECT * FROM budget_overrides WHERE month = :month")
    suspend fun getOverridesFor(month: String): List<BudgetOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOverride(override: BudgetOverrideEntity): Long

    @Update
    suspend fun updateOverride(override: BudgetOverrideEntity)

    @Query("DELETE FROM budget_overrides WHERE id = :id")
    suspend fun deleteOverrideById(id: Long)
}

/** Bulk operations used only by backup restore, always inside a single transaction. */
@Dao
interface MaintenanceDao {
    @Query("DELETE FROM transactions WHERE refund_of_id IS NOT NULL")
    suspend fun deleteRefunds()

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM budgets")
    suspend fun deleteBudgets()

    @Query("DELETE FROM budget_overrides")
    suspend fun deleteBudgetOverrides()

    @Query("DELETE FROM accounts WHERE linked_account_id IS NOT NULL")
    suspend fun deleteLinkedAccounts()

    @Query("DELETE FROM accounts")
    suspend fun deleteAccounts()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudgetOverrides(items: List<BudgetOverrideEntity>)
}
