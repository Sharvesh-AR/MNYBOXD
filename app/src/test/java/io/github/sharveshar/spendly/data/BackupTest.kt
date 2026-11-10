package io.github.sharveshar.spendly.data

import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.backup.BackupCodec
import io.github.sharveshar.spendly.data.backup.BackupFormatException
import io.github.sharveshar.spendly.data.backup.BackupPasswordException
import io.github.sharveshar.spendly.data.backup.BackupRepository
import io.github.sharveshar.spendly.data.backup.BackupValidation
import io.github.sharveshar.spendly.data.backup.BackupValidator
import io.github.sharveshar.spendly.data.backup.CsvExporter
import io.github.sharveshar.spendly.data.repo.AccountInput
import io.github.sharveshar.spendly.data.repo.AccountRepository
import io.github.sharveshar.spendly.data.repo.SaveResult
import io.github.sharveshar.spendly.data.repo.TransactionRepository
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)
    private val now = 1_760_000_000_000L
    private val password = "correct horse battery".toCharArray()
    private val fastIterations = 100_000

    private suspend fun populated(): Pair<io.github.sharveshar.spendly.data.db.SpendlyDatabase, Long> {
        val db = testDatabase()
        val accounts = AccountRepository(db) { now }
        val txs = TransactionRepository(db, { ZoneId.of("UTC") }, { now })
        val bank = (accounts.create(AccountInput("HDFC", "HDFC Bank", AccountType.SAVINGS, 50_000_00, null, null, 1)) as WriteResult.Ok).id
        val card = (accounts.create(AccountInput("HDFC Debit", "", AccountType.DEBIT_CARD, 0, bank, null, 1)) as WriteResult.Ok).id
        val cc = (accounts.create(AccountInput("Coral", "ICICI", AccountType.CREDIT_CARD, 0, null, null, 2)) as WriteResult.Ok).id
        val food = db.categoryDao().getAll().first { it.key == "food" }.id
        val exp = (txs.save(TransactionDraft(TransactionType.EXPENSE, 1_000_00, cc, null, food, null, null, null, "=cmd|' /C calc'!A0", now), "a") as SaveResult.Saved).id
        txs.save(TransactionDraft(TransactionType.REFUND, 250_00, cc, null, null, null, null, exp, "partial", now), "b")
        txs.save(TransactionDraft(TransactionType.EXPENSE, 99_99, card, null, food, null, null, null, "Lunch, \"office\"", now), "c")
        txs.save(TransactionDraft(TransactionType.TRANSFER, 750_00, bank, cc, null, null, null, null, "repay", now), "d")
        val budgets = io.github.sharveshar.spendly.data.repo.BudgetRepository(db) { now }
        budgets.setLimit(null, 20_000_00)
        budgets.setMonthLimit(java.time.YearMonth.of(2026, 12), null, 45_000_00)
        budgets.setMonthLimit(java.time.YearMonth.of(2026, 12), food, 9_000_00)
        return db to bank
    }

    private suspend fun balances(db: io.github.sharveshar.spendly.data.db.SpendlyDatabase) =
        db.accountDao().observeWithBalances().first().associate { it.account.id to it.balancePaise }

    @Test fun `encrypted backup round trips and restores identical balances`() = runTest {
        val (source, _) = populated()
        val snapshot = BackupRepository(source) { now }.snapshot()
        val file = BackupCodec.encrypt(snapshot, password, fastIterations)
        // Ciphertext must not contain plaintext fragments.
        assertTrue(!String(file, Charsets.ISO_8859_1).contains("HDFC"))

        val decoded = BackupCodec.decrypt(file, password)
        assertEquals(snapshot, decoded)
        val validation = BackupValidator.validate(decoded)
        assertTrue(validation is BackupValidation.Valid)

        val target = testDatabase()
        BackupRepository(target).restore(validation as BackupValidation.Valid)
        assertEquals(balances(source), balances(target))
        assertEquals(source.transactionDao().getAll(), target.transactionDao().getAll())
        assertEquals(source.budgetDao().getOverrides(), target.budgetDao().getOverrides())
        source.close(); target.close()
    }

    @Test fun `wrong password and tampering are detected`() = runTest {
        val (db, _) = populated()
        val file = BackupCodec.encrypt(BackupRepository(db) { now }.snapshot(), password, fastIterations)
        expect<BackupPasswordException> { BackupCodec.decrypt(file, "wrong password!".toCharArray()) }
        // Flip a ciphertext byte
        val tampered = file.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        expect<BackupPasswordException> { BackupCodec.decrypt(tampered, password) }
        // Flip a header byte (salt) – header is authenticated
        val header = file.copyOf().also { it[16] = (it[16].toInt() xor 1).toByte() }
        expect<BackupPasswordException> { BackupCodec.decrypt(header, password) }
        db.close()
    }

    @Test fun `malformed files are rejected with clear errors`() {
        expect<BackupFormatException> { BackupCodec.decrypt(ByteArray(0), password) }
        expect<BackupFormatException> { BackupCodec.decrypt("not a backup at all, just text".toByteArray(), password) }
        expect<BackupFormatException> { BackupCodec.decrypt("SPENDLYB".toByteArray(), password) }
        val future = "SPENDLYB".toByteArray() + byteArrayOf(9, 1, 0, 1, 0, 0) + ByteArray(64)
        expect<BackupFormatException> { BackupCodec.decrypt(future, password) }
        // Absurd iteration count (DoS) is refused before any key derivation.
        val dos = "SPENDLYB".toByteArray() + byteArrayOf(1, 1, 0x7F, 0x7F, 0x7F, 0x7F) + ByteArray(64)
        expect<BackupFormatException> { BackupCodec.decrypt(dos, password) }
        expect<IllegalArgumentException> { BackupCodec.encrypt(sampleEmpty(), "short".toCharArray()) }
    }

    @Test fun `validator rejects inconsistent snapshots`() = runTest {
        val (db, bank) = populated()
        val good = BackupRepository(db) { now }.snapshot()
        db.close()
        fun invalid(s: io.github.sharveshar.spendly.data.backup.BackupSnapshot) =
            assertTrue("should be invalid", BackupValidator.validate(s) is BackupValidation.Invalid)

        invalid(good.copy(schema = 2))
        invalid(good.copy(transactions = good.transactions.map { it.copy(amountPaise = -1) }))
        invalid(good.copy(transactions = good.transactions.map { it.copy(accountId = 999) }))
        invalid(good.copy(transactions = good.transactions + good.transactions.first()))
        invalid(good.copy(transactions = good.transactions.map { it.copy(type = "STEAL") }))
        invalid(good.copy(transactions = good.transactions.map { if (it.type == "REFUND") it.copy(amountPaise = 2_000_00) else it }))
        invalid(good.copy(transactions = good.transactions.map { if (it.type == "TRANSFER") it.copy(toAccountId = it.accountId) else it }))
        invalid(good.copy(accounts = good.accounts.map { if (it.id == bank) it.copy(type = "WALLET") else it }))
        invalid(good.copy(accounts = good.accounts.map { it.copy(name = "x".repeat(41)) }))
        invalid(good.copy(transactions = good.transactions.map { it.copy(description = "evil\u0000") }))
        invalid(good.copy(budgetOverrides = good.budgetOverrides.map { it.copy(month = "2026-13") }))
        invalid(good.copy(budgetOverrides = good.budgetOverrides + good.budgetOverrides.first().copy(id = 99)))
        invalid(good.copy(budgetOverrides = good.budgetOverrides.map { it.copy(amountPaise = 0) }))
        // Backups made before month budgets existed still restore.
        assertTrue(BackupValidator.validate(good.copy(budgetOverrides = emptyList())) is BackupValidation.Valid)
        assertEquals(2, good.budgetOverrides.size)
    }

    @Test fun `failed restore rolls back and keeps existing data`() = runTest {
        val (db, _) = populated()
        val before = db.transactionDao().getAll()
        val snapshot = BackupRepository(db) { now }.snapshot()
        // Bypass the validator with a broken reference; the DB must reject and roll back.
        val broken = snapshot.copy(transactions = snapshot.transactions.map { it.copy(accountId = 424242) })
        try {
            BackupRepository(db).restore(BackupValidation.Valid(broken, BackupValidator.run { (validate(snapshot) as BackupValidation.Valid).summary }))
            fail("restore should fail")
        } catch (e: Exception) {
            // expected
        }
        assertEquals(before, db.transactionDao().getAll())
        db.close()
    }

    @Test fun `csv export quotes cells and neutralises formulas`() = runTest {
        val (db, _) = populated()
        val out = StringWriter()
        BackupRepository(db).exportCsv(out, ZoneId.of("UTC"))
        val text = out.toString()
        assertTrue(text.contains("\"'=cmd|' /C calc'!A0\""))
        assertTrue(text.contains("\"Lunch, \"\"office\"\"\""))
        assertTrue(text.contains(",99.99,"))
        assertEquals(5, text.trim().lines().size)
        assertEquals("\"'-5\"", CsvExporter.cell("-5"))
        assertEquals("\"'@SUM(A1)\"", CsvExporter.cell("@SUM(A1)"))
        db.close()
    }

    private fun sampleEmpty() = io.github.sharveshar.spendly.data.backup.BackupSnapshot(1, now, emptyList(), emptyList(), emptyList(), emptyList())

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
        }
    }
}
