package io.github.sharveshar.spendly.data.backup

import kotlinx.serialization.Serializable

/**
 * Backup payload. Plain data classes with primitive fields only (no polymorphism, no class names),
 * so decoding can never instantiate arbitrary types. Enum values are carried as strings and
 * checked by [BackupValidator] before anything touches the database.
 */
@Serializable
data class BackupSnapshot(
    val schema: Int,
    val createdAt: Long,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val budgets: List<BackupBudget>,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val institution: String,
    val type: String,
    val openingBalancePaise: Long,
    val linkedAccountId: Long? = null,
    val creditLimitPaise: Long? = null,
    val colorIndex: Int,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val key: String? = null,
    val name: String,
    val kind: String,
    val iconKey: String,
    val colorIndex: Int,
    val archived: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupTransaction(
    val id: Long,
    val type: String,
    val amountPaise: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val channel: String? = null,
    val provider: String? = null,
    val instrumentAccountId: Long? = null,
    val refundOfId: Long? = null,
    val description: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val clientRef: String,
)

@Serializable
data class BackupBudget(
    val id: Long,
    val categoryId: Long? = null,
    val amountPaise: Long,
    val updatedAt: Long,
)

data class BackupSummary(
    val createdAt: Long,
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val budgets: Int,
)
