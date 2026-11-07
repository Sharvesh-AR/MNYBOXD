package io.github.sharveshar.spendly.data.backup

import androidx.room.withTransaction
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.data.db.AccountEntity
import io.github.sharveshar.spendly.data.db.BudgetEntity
import io.github.sharveshar.spendly.data.db.CategoryEntity
import io.github.sharveshar.spendly.data.db.SeedData
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.db.TransactionEntity
import io.github.sharveshar.spendly.data.db.TransactionRow
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupRepository(
    private val db: SpendlyDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun snapshot(): BackupSnapshot = db.withTransaction {
        BackupSnapshot(
            schema = BackupCodec.SCHEMA_VERSION,
            createdAt = clock(),
            accounts = db.accountDao().getAll().map {
                BackupAccount(it.id, it.name, it.institution, it.type.name, it.openingBalancePaise, it.linkedAccountId, it.creditLimitPaise, it.colorIndex, it.archived, it.sortOrder, it.createdAt)
            },
            categories = db.categoryDao().getAll().map {
                BackupCategory(it.id, it.key, it.name, it.kind.name, it.iconKey, it.colorIndex, it.archived, it.sortOrder)
            },
            transactions = db.transactionDao().getAll().map {
                BackupTransaction(
                    it.id, it.type.name, it.amountPaise, it.accountId, it.toAccountId, it.categoryId, it.channel?.name,
                    it.provider?.name, it.instrumentAccountId, it.refundOfId, it.description, it.occurredAt, it.createdAt,
                    it.updatedAt, it.clientRef,
                )
            },
            budgets = db.budgetDao().getAll().map { BackupBudget(it.id, it.categoryId, it.amountPaise, it.updatedAt) },
        )
    }

    /** Writes an encrypted backup. The caller supplies the stream for a user-chosen SAF document. */
    suspend fun exportEncrypted(out: OutputStream, password: CharArray) {
        val bytes = BackupCodec.encrypt(snapshot(), password)
        out.write(bytes)
        out.flush()
    }

    /**
     * Replaces ALL local data with [snapshot] in one database transaction. Callers must have
     * validated the snapshot with [BackupValidator] and obtained explicit user confirmation.
     * If anything fails the transaction rolls back and existing data is untouched.
     */
    suspend fun restore(validated: BackupValidation.Valid) {
        val s = validated.snapshot
        db.withTransaction {
            val m = db.maintenanceDao()
            m.deleteRefunds()
            m.deleteTransactions()
            m.deleteBudgets()
            m.deleteLinkedAccounts()
            m.deleteAccounts()
            m.deleteCategories()

            m.insertCategories(s.categories.map {
                CategoryEntity(it.id, it.key, it.name, CategoryKind.valueOf(it.kind), it.iconKey, it.colorIndex, it.archived, it.sortOrder)
            })
            val accounts = s.accounts.map {
                AccountEntity(it.id, it.name, it.institution, AccountType.valueOf(it.type), it.openingBalancePaise, it.linkedAccountId, it.creditLimitPaise, it.colorIndex, it.archived, it.sortOrder, it.createdAt)
            }
            // Parents before the debit cards that link to them.
            m.insertAccounts(accounts.filter { it.linkedAccountId == null })
            m.insertAccounts(accounts.filter { it.linkedAccountId != null })
            val txs = s.transactions.map {
                TransactionEntity(
                    it.id, TransactionType.valueOf(it.type), it.amountPaise, it.accountId, it.toAccountId, it.categoryId,
                    it.channel?.let(PaymentChannel::valueOf), it.provider?.let(PaymentProvider::valueOf),
                    it.instrumentAccountId, it.refundOfId, it.description, it.occurredAt, it.createdAt, it.updatedAt, it.clientRef,
                )
            }
            // Originals before the refunds that reference them.
            m.insertTransactions(txs.filter { it.refundOfId == null })
            m.insertTransactions(txs.filter { it.refundOfId != null })
            m.insertBudgets(s.budgets.map { BudgetEntity(it.id, it.categoryId, it.amountPaise, it.updatedAt) })
        }
    }

    /**
     * Deletes every transaction, budget, account and category and puts back the defaults a new
     * install starts with (default categories and the Cash wallet), all in one database
     * transaction: if anything fails, nothing is deleted. Part of Settings → Reset Spendly.
     */
    suspend fun resetToDefaults(now: Long) {
        db.withTransaction {
            val m = db.maintenanceDao()
            m.deleteRefunds()
            m.deleteTransactions()
            m.deleteBudgets()
            m.deleteLinkedAccounts()
            m.deleteAccounts()
            m.deleteCategories()
            SeedData.insertDefaults(db.openHelper.writableDatabase, now)
        }
    }

    /** Plain-text CSV of all transactions. The UI warns that this file is unencrypted. */
    suspend fun exportCsv(writer: Writer, zone: ZoneId = ZoneId.systemDefault()) {
        CsvExporter.write(db.transactionDao().getAllRows(), writer, zone)
    }
}

object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    val header = listOf("Date", "Time", "Type", "Amount (INR)", "Category", "Account", "To account", "Card", "Payment method", "Payment app", "Description")

    fun write(rows: List<TransactionRow>, writer: Writer, zone: ZoneId) {
        writer.write(header.joinToString(",") { cell(it) })
        writer.write("\r\n")
        for (r in rows) {
            val dt = Instant.ofEpochMilli(r.occurredAt).atZone(zone)
            val line = listOf(
                dt.format(dateFormat),
                dt.format(timeFormat),
                r.type.label,
                Money.toPlainString(r.amountPaise),
                r.categoryName.orEmpty(),
                r.accountName,
                r.toAccountName.orEmpty(),
                r.instrumentName.orEmpty(),
                r.channel?.label.orEmpty(),
                r.provider?.label.orEmpty(),
                r.description,
            )
            writer.write(line.mapIndexed { i, v -> if (i == 3) v else cell(v) }.joinToString(","))
            writer.write("\r\n")
        }
        writer.flush()
    }

    /**
     * Quotes a text cell and neutralises spreadsheet formula injection: values starting with
     * = + - @ tab or CR are prefixed with an apostrophe.
     */
    fun cell(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) "'$raw" else raw
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
