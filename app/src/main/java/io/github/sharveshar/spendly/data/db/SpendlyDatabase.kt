package io.github.sharveshar.spendly.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class],
    version = SpendlyDatabase.VERSION,
    exportSchema = true,
)
abstract class SpendlyDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "spendly.db"

        /**
         * Migrations, newest last. Room is never configured with destructive fallback: if a
         * migration is missing the open fails and the app shows a recovery screen instead of
         * deleting financial data.
         */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}

/** Default categories and the cash wallet created on first launch. */
object SeedData {
    data class SeedCategory(val key: String, val name: String, val kind: CategoryKind, val icon: String, val color: Int)

    val categories = listOf(
        SeedCategory("food", "Food & Dining", CategoryKind.EXPENSE, "restaurant", 0),
        SeedCategory("groceries", "Groceries", CategoryKind.EXPENSE, "grocery", 1),
        SeedCategory("transport", "Transport", CategoryKind.EXPENSE, "commute", 2),
        SeedCategory("fuel", "Fuel", CategoryKind.EXPENSE, "fuel", 3),
        SeedCategory("shopping", "Shopping", CategoryKind.EXPENSE, "shopping", 4),
        SeedCategory("bills", "Bills & Utilities", CategoryKind.EXPENSE, "bills", 5),
        SeedCategory("rent", "Rent & Housing", CategoryKind.EXPENSE, "home", 6),
        SeedCategory("health", "Health", CategoryKind.EXPENSE, "health", 7),
        SeedCategory("entertainment", "Entertainment", CategoryKind.EXPENSE, "movie", 8),
        SeedCategory("travel", "Travel", CategoryKind.EXPENSE, "travel", 9),
        SeedCategory("education", "Education", CategoryKind.EXPENSE, "school", 2),
        SeedCategory("personal_care", "Personal Care", CategoryKind.EXPENSE, "spa", 7),
        SeedCategory("gifts", "Gifts & Donations", CategoryKind.EXPENSE, "gift", 4),
        SeedCategory("other", "Other", CategoryKind.EXPENSE, "other", 9),
        SeedCategory("salary", "Salary", CategoryKind.INCOME, "work", 0),
        SeedCategory("business", "Business", CategoryKind.INCOME, "store", 1),
        SeedCategory("interest", "Interest", CategoryKind.INCOME, "savings", 2),
        SeedCategory("gift_income", "Gifts received", CategoryKind.INCOME, "gift", 4),
        SeedCategory("other_income", "Other income", CategoryKind.INCOME, "other", 9),
    )

    fun insertDefaults(db: SupportSQLiteDatabase, now: Long) {
        categories.forEachIndexed { index, c ->
            db.insert(
                "categories",
                CONFLICT_ABORT,
                ContentValues().apply {
                    put("key", c.key)
                    put("name", c.name)
                    put("kind", c.kind.name)
                    put("icon_key", c.icon)
                    put("color_index", c.color)
                    put("is_archived", 0)
                    put("sort_order", index)
                },
            )
        }
        db.insert(
            "accounts",
            CONFLICT_ABORT,
            ContentValues().apply {
                put("name", "Cash")
                put("institution", "")
                put("type", AccountType.CASH.name)
                put("opening_balance_paise", 0L)
                put("color_index", 0)
                put("is_archived", 0)
                put("sort_order", 0)
                put("created_at", now)
            },
        )
    }
}

/**
 * Integrity rules enforced inside SQLite itself, as a last line of defence beneath the Kotlin
 * validation. They are (re)created on every open with IF NOT EXISTS so restored and migrated
 * databases always carry them.
 */
object IntegrityTriggers {
    private const val MAX = 99_999_999_999L

    val statements = listOf(
        """CREATE TRIGGER IF NOT EXISTS trg_tx_amount_ins BEFORE INSERT ON transactions
           WHEN NEW.amount_paise <= 0 OR NEW.amount_paise > $MAX
           BEGIN SELECT RAISE(ABORT, 'invalid amount'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_tx_amount_upd BEFORE UPDATE ON transactions
           WHEN NEW.amount_paise <= 0 OR NEW.amount_paise > $MAX
           BEGIN SELECT RAISE(ABORT, 'invalid amount'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_tx_shape_ins BEFORE INSERT ON transactions
           WHEN (NEW.type = 'TRANSFER' AND (NEW.to_account_id IS NULL OR NEW.to_account_id = NEW.account_id))
             OR (NEW.type <> 'TRANSFER' AND NEW.to_account_id IS NOT NULL)
             OR (NEW.type NOT IN ('EXPENSE', 'INCOME', 'TRANSFER', 'REFUND'))
             OR (NEW.refund_of_id IS NOT NULL AND NEW.type <> 'REFUND')
           BEGIN SELECT RAISE(ABORT, 'invalid transaction shape'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_tx_shape_upd BEFORE UPDATE ON transactions
           WHEN (NEW.type = 'TRANSFER' AND (NEW.to_account_id IS NULL OR NEW.to_account_id = NEW.account_id))
             OR (NEW.type <> 'TRANSFER' AND NEW.to_account_id IS NOT NULL)
             OR (NEW.type NOT IN ('EXPENSE', 'INCOME', 'TRANSFER', 'REFUND'))
             OR (NEW.refund_of_id IS NOT NULL AND NEW.type <> 'REFUND')
           BEGIN SELECT RAISE(ABORT, 'invalid transaction shape'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_tx_no_debit_card_funding BEFORE INSERT ON transactions
           WHEN (SELECT type FROM accounts WHERE id = NEW.account_id) = 'DEBIT_CARD'
             OR (SELECT type FROM accounts WHERE id = NEW.to_account_id) = 'DEBIT_CARD'
           BEGIN SELECT RAISE(ABORT, 'debit cards hold no balance'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_tx_no_debit_card_funding_upd BEFORE UPDATE ON transactions
           WHEN (SELECT type FROM accounts WHERE id = NEW.account_id) = 'DEBIT_CARD'
             OR (SELECT type FROM accounts WHERE id = NEW.to_account_id) = 'DEBIT_CARD'
           BEGIN SELECT RAISE(ABORT, 'debit cards hold no balance'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_budget_amount_ins BEFORE INSERT ON budgets
           WHEN NEW.amount_paise <= 0 OR NEW.amount_paise > $MAX
           BEGIN SELECT RAISE(ABORT, 'invalid budget'); END""",
        """CREATE TRIGGER IF NOT EXISTS trg_budget_amount_upd BEFORE UPDATE ON budgets
           WHEN NEW.amount_paise <= 0 OR NEW.amount_paise > $MAX
           BEGIN SELECT RAISE(ABORT, 'invalid budget'); END""",
    )
}

class SpendlyDatabaseCallback(private val clock: () -> Long = System::currentTimeMillis) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SeedData.insertDefaults(db, clock())
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        IntegrityTriggers.statements.forEach { db.execSQL(it) }
    }
}
