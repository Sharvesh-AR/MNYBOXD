package io.github.sharveshar.spendly.data

import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.db.TransactionEntity
import io.github.sharveshar.spendly.data.repo.AccountInput
import io.github.sharveshar.spendly.data.repo.AccountRepository
import io.github.sharveshar.spendly.data.repo.BudgetRepository
import io.github.sharveshar.spendly.data.repo.DeleteResult
import io.github.sharveshar.spendly.data.repo.SaveResult
import io.github.sharveshar.spendly.data.repo.TransactionFilter
import io.github.sharveshar.spendly.data.repo.TransactionRepository
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.insights.BudgetLevel
import io.github.sharveshar.spendly.domain.ledger.BalanceCalculator
import io.github.sharveshar.spendly.domain.ledger.LedgerEntry
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.ledger.ValidationError
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {
    private lateinit var db: SpendlyDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var txs: TransactionRepository
    private lateinit var budgets: BudgetRepository
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = 1_760_000_000_000L // 2025-10-09 IST

    private var cashId = 0L
    private var hdfcId = 0L
    private var sbiId = 0L
    private var ccId = 0L
    private var debitId = 0L
    private var foodId = 0L
    private var shoppingId = 0L
    private var salaryId = 0L

    @Before fun setUp() = runTest {
        db = testDatabase()
        accounts = AccountRepository(db) { now }
        txs = TransactionRepository(db, { zone }, { now })
        budgets = BudgetRepository(db) { now }
        cashId = db.accountDao().getAll().single { it.type == AccountType.CASH }.id
        hdfcId = ok(accounts.create(AccountInput("HDFC Savings", "HDFC Bank", AccountType.SAVINGS, 10_000_00, null, null, 1)))
        sbiId = ok(accounts.create(AccountInput("SBI Savings", "State Bank of India", AccountType.SAVINGS, 5_000_00, null, null, 2)))
        ccId = ok(accounts.create(AccountInput("ICICI Coral", "ICICI Bank", AccountType.CREDIT_CARD, 1_500_00, null, 50_000_00, 3)))
        debitId = ok(accounts.create(AccountInput("HDFC Debit", "HDFC Bank", AccountType.DEBIT_CARD, 0, hdfcId, null, 4)))
        val cats = db.categoryDao().getAll()
        foodId = cats.single { it.key == "food" }.id
        shoppingId = cats.single { it.key == "shopping" }.id
        salaryId = cats.single { it.key == "salary" }.id
    }

    @After fun tearDown() = db.close()

    private fun ok(r: WriteResult): Long = (r as? WriteResult.Ok)?.id ?: error("expected Ok but was $r")

    private fun draft(
        type: TransactionType, amount: Long, account: Long, to: Long? = null, category: Long? = null,
        channel: PaymentChannel? = null, provider: PaymentProvider? = null, refundOf: Long? = null, at: Long = now,
    ) = TransactionDraft(type, amount, account, to, category, channel, provider, refundOf, "test", at)

    private suspend fun save(d: TransactionDraft, ref: String = UUID.randomUUID().toString(), editing: Long? = null): Long {
        val r = txs.save(d, ref, editing)
        return (r as? SaveResult.Saved)?.id ?: error("expected Saved but was $r")
    }

    private suspend fun balances(): Map<Long, Long> =
        db.accountDao().observeWithBalances().first().associate { it.account.id to it.balancePaise }

    @Test fun `seed data creates categories and a zero cash wallet`() = runTest {
        assertTrue(db.categoryDao().getAll().size >= 15)
        assertEquals(0L, balances()[cashId])
    }

    @Test fun `opening balances use signed convention`() = runTest {
        val b = balances()
        assertEquals(10_000_00L, b[hdfcId])
        assertEquals(-1_500_00L, b[ccId])
        assertEquals(1_500_00L, BalanceCalculator.outstanding(b[ccId]!!))
    }

    @Test fun `UPI expense funded by bank is a single deduction`() = runTest {
        save(draft(TransactionType.EXPENSE, 500_00, hdfcId, category = foodId, channel = PaymentChannel.UPI, provider = PaymentProvider.GOOGLE_PAY))
        assertEquals(9_500_00L, balances()[hdfcId])
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test fun `debit card expense is stored against the linked bank`() = runTest {
        val id = save(draft(TransactionType.EXPENSE, 300_00, debitId, category = shoppingId))
        val stored = db.transactionDao().getById(id)!!
        assertEquals(hdfcId, stored.accountId)
        assertEquals(debitId, stored.instrumentAccountId)
        assertEquals(PaymentChannel.DEBIT_CARD, stored.channel)
        assertEquals(9_700_00L, balances()[hdfcId])
        // Filtering by the card finds it too.
        assertEquals(1, txs.observeFiltered(TransactionFilter(accountId = debitId)).first().size)
    }

    @Test fun `card purchase and repayment`() = runTest {
        save(draft(TransactionType.EXPENSE, 2_000_00, ccId, category = shoppingId, channel = PaymentChannel.CREDIT_CARD))
        assertEquals(3_500_00L, BalanceCalculator.outstanding(balances()[ccId]!!))
        save(draft(TransactionType.TRANSFER, 3_500_00, hdfcId, to = ccId))
        val b = balances()
        assertEquals(0L, BalanceCalculator.outstanding(b[ccId]!!))
        assertEquals(6_500_00L, b[hdfcId])
    }

    @Test fun `duplicate save of the same form is prevented`() = runTest {
        val ref = "form-123"
        val d = draft(TransactionType.EXPENSE, 100_00, cashId, category = foodId, channel = PaymentChannel.CASH)
        val first = txs.save(d, ref)
        val second = txs.save(d, ref)
        assertTrue(first is SaveResult.Saved)
        assertEquals(SaveResult.Duplicate((first as SaveResult.Saved).id), second)
        assertEquals(1, db.transactionDao().getAll().size)
        assertEquals(-100_00L, balances()[cashId])
    }

    @Test fun `unique client reference is also enforced by the database`() = runTest {
        val e = TransactionEntity(type = TransactionType.EXPENSE, amountPaise = 1, accountId = cashId, categoryId = foodId, occurredAt = now, createdAt = now, updatedAt = now, clientRef = "dup")
        db.transactionDao().insert(e)
        expectSqlFailure { db.transactionDao().insert(e) }
    }

    @Test fun `integrity triggers reject bad rows that bypass validation`() = runTest {
        val base = TransactionEntity(type = TransactionType.EXPENSE, amountPaise = 1, accountId = cashId, categoryId = foodId, occurredAt = now, createdAt = now, updatedAt = now, clientRef = "x")
        expectSqlFailure { db.transactionDao().insert(base.copy(amountPaise = 0, clientRef = "a")) }
        expectSqlFailure { db.transactionDao().insert(base.copy(amountPaise = -5, clientRef = "b")) }
        expectSqlFailure { db.transactionDao().insert(base.copy(type = TransactionType.TRANSFER, categoryId = null, clientRef = "c")) }
        expectSqlFailure { db.transactionDao().insert(base.copy(type = TransactionType.TRANSFER, toAccountId = cashId, categoryId = null, clientRef = "d")) }
        expectSqlFailure { db.transactionDao().insert(base.copy(accountId = debitId, clientRef = "e")) }
        expectSqlFailure { db.transactionDao().insert(base.copy(toAccountId = hdfcId, clientRef = "f")) }
        val id = db.transactionDao().insert(base.copy(clientRef = "g"))
        expectSqlFailure { db.transactionDao().update(base.copy(id = id, clientRef = "g", amountPaise = 0)) }
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test fun `foreign keys block deleting referenced accounts and categories`() = runTest {
        save(draft(TransactionType.EXPENSE, 100, hdfcId, category = foodId))
        expectSqlFailure { db.accountDao().deleteById(hdfcId) }
        expectSqlFailure { db.categoryDao().deleteById(foodId) }
        val bad = TransactionEntity(type = TransactionType.EXPENSE, amountPaise = 1, accountId = 9_999, categoryId = foodId, occurredAt = now, createdAt = now, updatedAt = now, clientRef = "fk")
        expectSqlFailure { db.transactionDao().insert(bad) }
        assertTrue(accounts.deleteIfUnused(hdfcId) is WriteResult.Rejected)
        assertTrue(accounts.deleteIfUnused(sbiId) is WriteResult.Ok)
    }

    @Test fun `update changes balances and delete restores them`() = runTest {
        val id = save(draft(TransactionType.EXPENSE, 100_00, sbiId, category = foodId))
        assertEquals(4_900_00L, balances()[sbiId])
        save(draft(TransactionType.EXPENSE, 250_50, sbiId, category = foodId), editing = id)
        assertEquals(4_749_50L, balances()[sbiId])
        assertEquals(DeleteResult.Deleted, txs.delete(id))
        assertEquals(5_000_00L, balances()[sbiId])
        assertEquals(DeleteResult.NotFound, txs.delete(id))
    }

    @Test fun `refunds reverse spending and protect the original`() = runTest {
        val exp = save(draft(TransactionType.EXPENSE, 1_000_00, ccId, category = shoppingId, channel = PaymentChannel.CREDIT_CARD))
        val r1 = save(draft(TransactionType.REFUND, 400_00, ccId, refundOf = exp))
        assertEquals(foodId != shoppingId, true)
        assertEquals(shoppingId, db.transactionDao().getById(r1)!!.categoryId)
        assertEquals(600_00L, txs.refundableAmount(exp))
        val tooMuch = txs.save(draft(TransactionType.REFUND, 600_01, ccId, refundOf = exp), "r2")
        assertEquals(SaveResult.Invalid(listOf(ValidationError.REFUND_EXCEEDS_ORIGINAL)), tooMuch)
        assertEquals(DeleteResult.HasRefunds(1), txs.delete(exp))
        // Editing the original below the refunded amount is blocked.
        val shrink = txs.save(draft(TransactionType.EXPENSE, 399_00, ccId, category = shoppingId, channel = PaymentChannel.CREDIT_CARD), "x", exp)
        assertEquals(SaveResult.Invalid(listOf(ValidationError.HAS_REFUNDS)), shrink)
        assertEquals(1_500_00L + 600_00L, BalanceCalculator.outstanding(balances()[ccId]!!))
    }

    @Test fun `invalid amounts are rejected and nothing is written`() = runTest {
        listOf(0L, -100L, 100_000_000_000L).forEach {
            val r = txs.save(draft(TransactionType.EXPENSE, it, cashId, category = foodId), UUID.randomUUID().toString())
            assertEquals(SaveResult.Invalid(listOf(ValidationError.AMOUNT_INVALID)), r)
        }
        assertEquals(0, db.transactionDao().getAll().size)
    }

    @Test fun `budget alerts fire when crossing 80 and 100 percent`() = runTest {
        ok(budgets.setLimit(foodId, 1_000_00))
        val first = txs.save(draft(TransactionType.EXPENSE, 700_00, cashId, category = foodId), "b1") as SaveResult.Saved
        assertEquals(null, first.alert)
        val second = txs.save(draft(TransactionType.EXPENSE, 150_00, cashId, category = foodId), "b2") as SaveResult.Saved
        assertEquals(BudgetLevel.WARNING, second.alert?.level)
        assertEquals("Food & Dining", second.alert?.categoryName)
        val third = txs.save(draft(TransactionType.EXPENSE, 150_00, cashId, category = foodId), "b3") as SaveResult.Saved
        assertEquals(BudgetLevel.EXCEEDED, third.alert?.level)
        assertEquals(100, third.alert?.percentUsed)
        assertTrue(budgets.setLimit(salaryId, 100) is WriteResult.Rejected)
        assertTrue(budgets.setLimit(null, 0) is WriteResult.Rejected)
    }

    @Test fun `search treats wildcards literally and matches amounts`() = runTest {
        save(draft(TransactionType.EXPENSE, 120_50, cashId, category = foodId).copy(description = "Lunch 100% veg"))
        save(draft(TransactionType.EXPENSE, 99_00, cashId, category = foodId).copy(description = "Coffee"))
        assertEquals(1, txs.observeFiltered(TransactionFilter(query = "%")).first().size)
        assertEquals(0, txs.observeFiltered(TransactionFilter(query = "_")).first().size)
        assertEquals(1, txs.observeFiltered(TransactionFilter(query = "lunch")).first().size)
        assertEquals(1, txs.observeFiltered(TransactionFilter(query = "120.50")).first().size)
        assertEquals(2, txs.observeFiltered(TransactionFilter(query = "food")).first().size)
        assertEquals(0, txs.observeFiltered(TransactionFilter(query = "'; DROP TABLE transactions; --")).first().size)
        assertEquals(2, db.transactionDao().getAll().size)
    }

    @Test fun `filters by type account category and date range`() = runTest {
        save(draft(TransactionType.EXPENSE, 100, cashId, category = foodId, at = now - 86_400_000L))
        save(draft(TransactionType.INCOME, 200, hdfcId, category = salaryId))
        save(draft(TransactionType.TRANSFER, 300, hdfcId, to = sbiId))
        assertEquals(1, txs.observeFiltered(TransactionFilter(type = TransactionType.INCOME)).first().size)
        assertEquals(2, txs.observeFiltered(TransactionFilter(accountId = hdfcId)).first().size)
        assertEquals(1, txs.observeFiltered(TransactionFilter(accountId = sbiId)).first().size)
        assertEquals(1, txs.observeFiltered(TransactionFilter(categoryId = foodId)).first().size)
        assertEquals(2, txs.observeFiltered(TransactionFilter(fromMillis = now - 1000, toMillis = now + 1000)).first().size)
    }

    /**
     * Property test: random mixes of all transaction types must give identical balances from the
     * SQL query (used by the UI) and the pure Kotlin reference implementation.
     */
    @Test fun `SQL balances always match the reference calculator`() = runTest {
        val rnd = Random(42)
        val balanceAccounts = listOf(cashId, hdfcId, sbiId, ccId)
        val expenses = mutableListOf<Pair<Long, Long>>() // id to remaining refundable
        repeat(400) { i ->
            when (rnd.nextInt(5)) {
                0, 1 -> {
                    val acc = (balanceAccounts + debitId).random(rnd)
                    val amt = rnd.nextLong(1, 500_000)
                    val ch = if (acc == cashId) PaymentChannel.CASH else null
                    val id = save(draft(TransactionType.EXPENSE, amt, acc, category = foodId, channel = ch), "e$i")
                    expenses += id to amt
                }
                2 -> save(draft(TransactionType.INCOME, rnd.nextLong(1, 900_000), balanceAccounts.random(rnd), category = salaryId), "i$i")
                3 -> {
                    val from = balanceAccounts.random(rnd)
                    val to = (balanceAccounts - from).random(rnd)
                    save(draft(TransactionType.TRANSFER, rnd.nextLong(1, 300_000), from, to = to), "t$i")
                }
                4 -> if (expenses.isNotEmpty()) {
                    val idx = rnd.nextInt(expenses.size)
                    val (origId, remaining) = expenses[idx]
                    if (remaining > 0) {
                        val amt = rnd.nextLong(1, remaining + 1)
                        val acc = db.transactionDao().getById(origId)!!.accountId
                        save(draft(TransactionType.REFUND, amt, acc, refundOf = origId), "r$i")
                        expenses[idx] = origId to remaining - amt
                    }
                }
            }
        }
        val opening = db.accountDao().getAll().associate { it.id to it.openingBalancePaise }
        val entries = db.transactionDao().getAll().map { LedgerEntry(it.type, it.amountPaise, it.accountId, it.toAccountId) }
        val expected = BalanceCalculator.balances(opening, entries)
        assertEquals(expected, balances())
    }

    @Test fun `debit card requires a linked bank account`() = runTest {
        val r = accounts.create(AccountInput("Loose card", "", AccountType.DEBIT_CARD, 0, null, null, 0))
        assertTrue(r is WriteResult.Rejected)
        val r2 = accounts.create(AccountInput("Card on card", "", AccountType.DEBIT_CARD, 0, ccId, null, 0))
        assertTrue(r2 is WriteResult.Rejected)
        assertTrue(accounts.create(AccountInput(" ", "", AccountType.SAVINGS, 0, null, null, 0)) is WriteResult.Rejected)
        assertNotNull(db.accountDao().getById(debitId))
    }

    private suspend fun expectSqlFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected the database to reject the write")
        } catch (e: android.database.SQLException) {
            // expected
        }
    }
}
