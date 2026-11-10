package io.github.sharveshar.spendly.domain.ledger

import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val now = 1_760_000_000_000L

    // Accounts
    private val hdfc = AccountRef(1, AccountType.SAVINGS, false)
    private val cash = AccountRef(2, AccountType.CASH, false)
    private val iciciCc = AccountRef(3, AccountType.CREDIT_CARD, false)
    private val hdfcDebit = AccountRef(4, AccountType.DEBIT_CARD, false, linkedAccountId = 1)
    private val sbi = AccountRef(5, AccountType.SAVINGS, false)
    private val archived = AccountRef(6, AccountType.SAVINGS, true)
    private val orphanDebit = AccountRef(7, AccountType.DEBIT_CARD, false, linkedAccountId = null)
    private val accounts = listOf(hdfc, cash, iciciCc, hdfcDebit, sbi, archived, orphanDebit).associateBy { it.id }

    private val food = CategoryRef(10, CategoryKind.EXPENSE, false)
    private val salary = CategoryRef(20, CategoryKind.INCOME, false)
    private val categories = listOf(food, salary).associateBy { it.id }

    private fun draft(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long? = 50_000,
        account: Long? = 1,
        to: Long? = null,
        category: Long? = if (type == TransactionType.EXPENSE || type == TransactionType.REFUND) 10 else null,
        channel: PaymentChannel? = null,
        provider: PaymentProvider? = null,
        refundOf: Long? = null,
        at: Long = now,
    ) = TransactionDraft(type, amount, account, to, category, channel, provider, refundOf, "x", at)

    private fun valid(r: RuleResult): ResolvedTransaction {
        assertTrue("expected valid but was $r", r is RuleResult.Valid)
        return (r as RuleResult.Valid).transaction
    }

    private fun errors(r: RuleResult): List<ValidationError> {
        assertTrue("expected invalid but was $r", r is RuleResult.Invalid)
        return (r as RuleResult.Invalid).errors
    }

    private fun resolve(d: TransactionDraft, original: RefundOriginal? = null, editing: Boolean = false) =
        TransactionRules.resolve(d, accounts, categories, now, original, editing)

    private fun entry(t: ResolvedTransaction) = LedgerEntry(t.type, t.amountPaise, t.accountId, t.toAccountId)

    private val opening = mapOf(1L to 1_000_000L, 2L to 200_000L, 3L to -150_000L, 5L to 500_000L)

    // --- Balance effects ----------------------------------------------------------------------

    @Test fun `UPI expense from bank reduces only that bank once`() {
        // Google Pay → HDFC Savings → ₹500: one expense funded by HDFC, not two deductions.
        val t = valid(resolve(draft(amount = 50_000, account = 1, channel = PaymentChannel.UPI, provider = PaymentProvider.GOOGLE_PAY)))
        val b = BalanceCalculator.balances(opening, listOf(entry(t)))
        assertEquals(950_000L, b[1])
        assertEquals(opening - 1L, b - 1L)
    }

    @Test fun `cash expense reduces cash balance`() {
        val t = valid(resolve(draft(amount = 8_000, account = 2, channel = PaymentChannel.CASH)))
        assertEquals(192_000L, BalanceCalculator.balances(opening, listOf(entry(t)))[2])
    }

    @Test fun `debit card payment reduces the linked bank`() {
        val t = valid(resolve(draft(amount = 30_000, account = 4)))
        assertEquals(1L, t.accountId)
        assertEquals(4L, t.instrumentAccountId)
        assertEquals(PaymentChannel.DEBIT_CARD, t.channel)
        val b = BalanceCalculator.balances(opening, listOf(entry(t)))
        assertEquals(970_000L, b[1])
        assertEquals(null, b[4])
    }

    @Test fun `debit card without linked bank is rejected`() {
        assertEquals(listOf(ValidationError.DEBIT_CARD_UNLINKED), errors(resolve(draft(account = 7))))
    }

    @Test fun `credit card purchase increases outstanding liability`() {
        val t = valid(resolve(draft(amount = 150_000, account = 3, channel = PaymentChannel.CREDIT_CARD)))
        val b = BalanceCalculator.balances(opening, listOf(entry(t)))
        assertEquals(-300_000L, b[3])
        assertEquals(300_000L, BalanceCalculator.outstanding(b[3]!!))
    }

    @Test fun `credit card repayment moves money from bank to card liability`() {
        val t = valid(resolve(draft(type = TransactionType.TRANSFER, amount = 150_000, account = 1, to = 3, category = null)))
        val b = BalanceCalculator.balances(opening, listOf(entry(t)))
        assertEquals(850_000L, b[1])
        assertEquals(0L, BalanceCalculator.outstanding(b[3]!!))
        // Net worth is unchanged by a repayment.
        assertEquals(
            BalanceCalculator.netWorth(opening.values).netPaise,
            BalanceCalculator.netWorth(b.values).netPaise,
        )
    }

    @Test fun `bank to bank transfer is neither expense nor income and conserves money`() {
        val t = valid(resolve(draft(type = TransactionType.TRANSFER, amount = 200_000, account = 1, to = 5, category = null)))
        val b = BalanceCalculator.balances(opening, listOf(entry(t)))
        assertEquals(800_000L, b[1])
        assertEquals(700_000L, b[5])
        assertEquals(opening.values.sum(), b.values.sum())
    }

    @Test fun `transfer validation`() {
        assertEquals(listOf(ValidationError.DESTINATION_MISSING), errors(resolve(draft(type = TransactionType.TRANSFER, category = null))))
        assertEquals(listOf(ValidationError.DESTINATION_SAME), errors(resolve(draft(type = TransactionType.TRANSFER, to = 1, category = null))))
        // Debit card → its own bank is the same account.
        assertEquals(listOf(ValidationError.DESTINATION_SAME), errors(resolve(draft(type = TransactionType.TRANSFER, account = 4, to = 1, category = null))))
        assertTrue(ValidationError.CATEGORY_UNEXPECTED in errors(resolve(draft(type = TransactionType.TRANSFER, to = 5, category = 10))))
        assertTrue(ValidationError.DESTINATION_UNEXPECTED in errors(resolve(draft(to = 5))))
    }

    @Test fun `income increases the chosen account`() {
        val t = valid(resolve(draft(type = TransactionType.INCOME, amount = 5_000_000, account = 1, category = 20)))
        assertEquals(6_000_000L, BalanceCalculator.balances(opening, listOf(entry(t)))[1])
        assertTrue(ValidationError.CATEGORY_WRONG_KIND in errors(resolve(draft(type = TransactionType.INCOME, category = 10))))
    }

    @Test fun `income on a credit card reduces outstanding`() {
        val t = valid(resolve(draft(type = TransactionType.INCOME, amount = 50_000, account = 3, category = 20)))
        assertEquals(100_000L, BalanceCalculator.outstanding(BalanceCalculator.balances(opening, listOf(entry(t)))[3]!!))
    }

    @Test fun `refund reverses original expense on same account`() {
        val exp = valid(resolve(draft(amount = 100_000, account = 3, channel = PaymentChannel.CREDIT_CARD)))
        val original = RefundOriginal(99, TransactionType.EXPENSE, 100_000, 0, 3, 10)
        val refund = valid(resolve(draft(type = TransactionType.REFUND, amount = 100_000, account = 3, category = null, refundOf = 99), original))
        assertEquals(10L, refund.categoryId) // inherited from original
        val b = BalanceCalculator.balances(opening, listOf(entry(exp), entry(refund)))
        assertEquals(opening[3], b[3])
    }

    @Test fun `partial refunds cannot exceed the original`() {
        val original = RefundOriginal(99, TransactionType.EXPENSE, 100_000, 60_000, 1, 10)
        valid(resolve(draft(type = TransactionType.REFUND, amount = 40_000, refundOf = 99), original))
        assertEquals(
            listOf(ValidationError.REFUND_EXCEEDS_ORIGINAL),
            errors(resolve(draft(type = TransactionType.REFUND, amount = 40_001, refundOf = 99), original)),
        )
        val notExpense = original.copy(type = TransactionType.INCOME)
        assertEquals(
            listOf(ValidationError.REFUND_ORIGINAL_INVALID),
            errors(resolve(draft(type = TransactionType.REFUND, amount = 1, refundOf = 99), notExpense)),
        )
        assertTrue(ValidationError.REFUND_LINK_UNEXPECTED in errors(resolve(draft(refundOf = 99), original)))
    }

    @Test fun `negative zero missing and huge amounts are rejected`() {
        assertEquals(listOf(ValidationError.AMOUNT_MISSING), errors(resolve(draft(amount = null))))
        listOf(0L, -1L, -50_000L, 99_999_999_999L + 1, Long.MAX_VALUE).forEach {
            assertEquals(listOf(ValidationError.AMOUNT_INVALID), errors(resolve(draft(amount = it))))
        }
    }

    @Test fun `paise precision is preserved through many operations`() {
        val entries = (1..10_000).map { LedgerEntry(TransactionType.EXPENSE, 1, 1) } +
            (1..3_333).map { LedgerEntry(TransactionType.INCOME, 3, 1) }
        assertEquals(1_000_000L - 10_000 + 9_999, BalanceCalculator.balances(opening, entries)[1])
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow is detected not wrapped`() {
        BalanceCalculator.balances(mapOf(1L to Long.MAX_VALUE), listOf(LedgerEntry(TransactionType.INCOME, 1, 1)))
    }

    @Test fun `account and category checks`() {
        assertEquals(listOf(ValidationError.ACCOUNT_MISSING), errors(resolve(draft(account = null))))
        assertEquals(listOf(ValidationError.ACCOUNT_UNKNOWN), errors(resolve(draft(account = 404))))
        assertEquals(listOf(ValidationError.ACCOUNT_ARCHIVED), errors(resolve(draft(account = 6))))
        // Editing an old record on an archived account is allowed.
        valid(resolve(draft(account = 6), editing = true))
        assertEquals(listOf(ValidationError.CATEGORY_MISSING), errors(resolve(draft(category = null))))
        assertEquals(listOf(ValidationError.CATEGORY_UNKNOWN), errors(resolve(draft(category = 404))))
    }

    @Test fun `channel must match account`() {
        assertEquals(listOf(ValidationError.CHANNEL_MISMATCH), errors(resolve(draft(account = 1, channel = PaymentChannel.CASH))))
        assertEquals(listOf(ValidationError.CHANNEL_MISMATCH), errors(resolve(draft(account = 2, channel = PaymentChannel.UPI))))
        assertEquals(listOf(ValidationError.CHANNEL_MISMATCH), errors(resolve(draft(account = 1, channel = PaymentChannel.CREDIT_CARD))))
        // "Paid by card" straight from a bank account (no separate card account) is valid.
        assertEquals(PaymentChannel.DEBIT_CARD, valid(resolve(draft(account = 1, channel = PaymentChannel.DEBIT_CARD))).channel)
        assertTrue(PaymentChannel.DEBIT_CARD in TransactionRules.channelsFor(AccountType.SAVINGS))
        assertEquals(listOf(ValidationError.CHANNEL_MISMATCH), errors(resolve(draft(account = 2, channel = PaymentChannel.DEBIT_CARD))))
        // RuPay credit card on UPI is valid.
        valid(resolve(draft(account = 3, channel = PaymentChannel.UPI, provider = PaymentProvider.PHONEPE)))
        assertTrue(ValidationError.PROVIDER_UNEXPECTED in errors(resolve(draft(account = 2, channel = PaymentChannel.CASH, provider = PaymentProvider.PAYTM))))
    }

    @Test fun `dates outside the allowed window are rejected`() {
        assertEquals(listOf(ValidationError.DATE_INVALID), errors(resolve(draft(at = 0))))
        assertEquals(listOf(ValidationError.DATE_INVALID), errors(resolve(draft(at = now + 2 * 86_400_000L))))
        valid(resolve(draft(at = now + 3_600_000L)))
    }

    @Test fun `description is sanitised and bounded`() {
        assertEquals("a b c", TransactionRules.sanitizeDescription("  a\u0000\n b\t\tc  "))
        val long = draft().copy(description = "x".repeat(121))
        assertEquals(listOf(ValidationError.DESCRIPTION_TOO_LONG), errors(resolve(long)))
    }

    @Test fun `net worth splits assets and liabilities`() {
        val nw = BalanceCalculator.netWorth(listOf(1_000L, -300L, 200L))
        assertEquals(1_200L, nw.assetsPaise)
        assertEquals(300L, nw.liabilitiesPaise)
        assertEquals(900L, nw.netPaise)
    }
}
