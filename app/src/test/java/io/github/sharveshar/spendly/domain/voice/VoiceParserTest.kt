// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.domain.voice

import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private val accounts = listOf(
        VoiceAccount(1, "HDFC Savings", "HDFC Bank", AccountType.SAVINGS),
        VoiceAccount(2, "Cash", "", AccountType.CASH),
        VoiceAccount(3, "ICICI Coral", "ICICI Bank", AccountType.CREDIT_CARD),
        VoiceAccount(4, "ICICI Salary", "ICICI Bank", AccountType.SAVINGS),
        VoiceAccount(5, "SBI Savings", "State Bank of India", AccountType.SAVINGS),
        VoiceAccount(6, "HDFC Debit", "HDFC Bank", AccountType.DEBIT_CARD),
    )
    private val categories = listOf(
        VoiceCategory(10, "food", "Food & Dining", CategoryKind.EXPENSE),
        VoiceCategory(11, "fuel", "Fuel", CategoryKind.EXPENSE),
        VoiceCategory(12, "shopping", "Shopping", CategoryKind.EXPENSE),
        VoiceCategory(13, "groceries", "Groceries", CategoryKind.EXPENSE),
        VoiceCategory(14, "transport", "Transport", CategoryKind.EXPENSE),
        VoiceCategory(15, null, "Pets", CategoryKind.EXPENSE),
        VoiceCategory(20, "salary", "Salary", CategoryKind.INCOME),
    )

    private fun parse(s: String, acc: List<VoiceAccount> = accounts) = VoiceParser.parse(s, acc, categories)

    @Test fun `120 rupees lunch`() {
        val r = parse("120 rupees lunch")
        assertEquals(12_000L, r.amountPaise)
        assertEquals("Lunch", r.description)
        assertEquals(10L, r.categoryId)
        assertEquals(TransactionType.EXPENSE, r.type)
        assertTrue(r.issues.isEmpty())
    }

    @Test fun `petrol using PhonePe from SBI`() {
        val r = parse("Spent 500 on petrol using PhonePe from SBI")
        assertEquals(50_000L, r.amountPaise)
        assertEquals("Petrol", r.description)
        assertEquals(11L, r.categoryId)
        assertEquals(PaymentChannel.UPI, r.channel)
        assertEquals(PaymentProvider.PHONEPE, r.provider)
        assertEquals(5L, r.accountId)
        assertTrue(r.issues.isEmpty())
    }

    @Test fun `shopping on ICICI credit card picks the card not the savings account`() {
        val r = parse("1500 shopping on ICICI credit card")
        assertEquals(150_000L, r.amountPaise)
        assertEquals(3L, r.accountId)
        assertEquals(PaymentChannel.CREDIT_CARD, r.channel)
        assertEquals(12L, r.categoryId)
        assertEquals("Shopping", r.description)
    }

    @Test fun `80 coffee cash`() {
        val r = parse("80 coffee cash")
        assertEquals(8_000L, r.amountPaise)
        assertEquals(2L, r.accountId)
        assertEquals(PaymentChannel.CASH, r.channel)
        assertEquals(10L, r.categoryId)
        assertEquals("Coffee", r.description)
        assertNull(r.provider)
    }

    @Test fun `currency symbols decimals and grouping`() {
        assertEquals(12_050L, parse("₹120.50 snacks").amountPaise)
        assertEquals(150_000L, parse("Rs.1,500 groceries").amountPaise)
        assertEquals(150_000L, parse("rs 1500 groceries").amountPaise)
        assertEquals(1_20_000_00L, parse("1,20,000 rent").amountPaise)
        assertEquals(25_000L, parse("250 rs auto").amountPaise)
    }

    @Test fun `multipliers and number words`() {
        assertEquals(500_000L, parse("5 thousand shopping").amountPaise)
        assertEquals(150_000L, parse("1.5k shopping").amountPaise)
        assertEquals(2_00_000_00L, parse("2 lakh rent").amountPaise)
        assertEquals(25_000L, parse("two hundred fifty for dinner").amountPaise)
        assertEquals(500_000L, parse("five thousand rupees rent").amountPaise)
    }

    @Test fun `missing amount is reported`() {
        val r = parse("lunch with friends")
        assertNull(r.amountPaise)
        assertTrue(VoiceIssue.MISSING_AMOUNT in r.issues)
        assertEquals(10L, r.categoryId)
    }

    @Test fun `multiple numbers are flagged as ambiguous`() {
        val r = parse("2 coffees 300")
        assertTrue(VoiceIssue.MULTIPLE_AMOUNTS in r.issues)
        // A currency marker resolves the ambiguity.
        val r2 = parse("2 coffees for 300 rupees")
        assertEquals(30_000L, r2.amountPaise)
        assertTrue(r2.issues.isEmpty())
    }

    @Test fun `out of range amount is reported`() {
        val r = parse("5000000000 shopping")
        assertNull(r.amountPaise)
        assertTrue(VoiceIssue.AMOUNT_OUT_OF_RANGE in r.issues)
    }

    @Test fun `ambiguous account is not guessed`() {
        // Two ICICI accounts and no type hint, neither a sole bank: savings preferred over card.
        assertEquals(4L, parse("300 dinner from ICICI").accountId)
        val twoBanks = accounts + VoiceAccount(7, "HDFC Joint", "HDFC Bank", AccountType.SAVINGS)
        val r = parse("300 dinner from HDFC", twoBanks)
        assertNull(r.accountId)
        assertTrue(VoiceIssue.ACCOUNT_AMBIGUOUS in r.issues)
    }

    @Test fun `unknown bank mention is reported`() {
        val r = parse("400 groceries from Axis")
        assertNull(r.accountId)
        assertTrue(VoiceIssue.ACCOUNT_NOT_FOUND in r.issues)
    }

    @Test fun `debit card resolves to the debit card account`() {
        val r = parse("700 shoes on HDFC debit card")
        assertEquals(6L, r.accountId)
        assertEquals(PaymentChannel.DEBIT_CARD, r.channel)
    }

    @Test fun `long bank names and providers`() {
        val r = parse("paid 99 via google pay from state bank for metro")
        assertEquals(5L, r.accountId)
        assertEquals(PaymentProvider.GOOGLE_PAY, r.provider)
        assertEquals(14L, r.categoryId)
        assertEquals("Metro", r.description)
    }

    @Test fun `income and refund detection`() {
        val r = parse("received 50000 salary in HDFC")
        assertEquals(TransactionType.INCOME, r.type)
        assertEquals(20L, r.categoryId)
        assertEquals(1L, r.accountId)
        assertEquals(TransactionType.REFUND, parse("refund 200 shopping").type)
    }

    @Test fun `user created category matched by name`() {
        assertEquals(15L, parse("600 pets food").categoryId)
    }

    @Test fun `yesterday shifts the date`() {
        assertEquals(-1, parse("yesterday 200 lunch").dayOffset)
        assertEquals(0, parse("200 lunch").dayOffset)
    }

    @Test fun `garbage input never throws`() {
        listOf("", "   ", "!!!", "₹", "rupees", "...", "1.2.3", ",,,", "k", "hundred", "and and").forEach {
            val r = parse(it)
            assertNull("'$it'", r.amountPaise)
        }
    }

    @Test fun `no accounts at all`() {
        val r = parse("Spent 500 on petrol using PhonePe from SBI", emptyList())
        assertEquals(50_000L, r.amountPaise)
        assertNull(r.accountId)
        assertTrue(VoiceIssue.ACCOUNT_NOT_FOUND in r.issues)
    }
}
