package io.github.sharveshar.spendly.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType

/**
 * A place money lives. No credentials, card numbers, CVVs, PINs or OTPs are ever stored.
 * [openingBalancePaise] uses the signed net-worth convention: a credit card with ₹1,500
 * outstanding is stored as −150000.
 */
@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("linked_account_id")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val institution: String = "",
    val type: AccountType,
    @ColumnInfo(name = "opening_balance_paise") val openingBalancePaise: Long = 0,
    @ColumnInfo(name = "linked_account_id") val linkedAccountId: Long? = null,
    @ColumnInfo(name = "credit_limit_paise") val creditLimitPaise: Long? = null,
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    @ColumnInfo(name = "is_archived") val archived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["key"], unique = true), Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier for built-in categories (null for user-created ones). */
    val key: String? = null,
    val name: String,
    val kind: CategoryKind,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    @ColumnInfo(name = "is_archived") val archived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["to_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["instrument_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["refund_of_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["client_ref"], unique = true),
        Index("account_id"),
        Index("to_account_id"),
        Index("instrument_account_id"),
        Index("category_id"),
        Index("refund_of_id"),
        Index("occurred_at"),
        Index(value = ["type", "occurred_at"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    /** Balance-holding account debited (expense/transfer) or credited (income/refund). */
    @ColumnInfo(name = "account_id") val accountId: Long,
    /** Destination of a transfer; null otherwise. */
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val channel: PaymentChannel? = null,
    val provider: PaymentProvider? = null,
    /** Debit card used for the payment; the money still comes from [accountId]. */
    @ColumnInfo(name = "instrument_account_id") val instrumentAccountId: Long? = null,
    @ColumnInfo(name = "refund_of_id") val refundOfId: Long? = null,
    val description: String = "",
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Idempotency key generated when the entry form opens; a unique index blocks double saves. */
    @ColumnInfo(name = "client_ref") val clientRef: String,
)

/** A recurring monthly limit. [categoryId] null means the overall monthly budget. */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["category_id"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
