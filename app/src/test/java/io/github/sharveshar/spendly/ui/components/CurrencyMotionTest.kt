package io.github.sharveshar.spendly.ui.components

import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.common.AccountUi
import io.github.sharveshar.spendly.ui.common.netWorth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyMotionTest {
    private fun keys(text: String) = amountSlots(text).associate { it.key to it.char }

    @Test fun `each digit keeps its place when the amount changes`() {
        val before = keys("₹12,840")
        val after = keys("₹12,420")
        // Units, tens and hundreds keep their keys: only the hundreds (8 → 4) and tens (4 → 2) change.
        assertEquals('0', before["i0"]); assertEquals('0', after["i0"])
        assertEquals('4', before["i1"]); assertEquals('2', after["i1"])
        assertEquals('8', before["i2"]); assertEquals('4', after["i2"])
        // The comma sits in the same slot, and ₹ is keyed as a prefix, so neither moves.
        assertEquals(',', before["i3"]); assertEquals(',', after["i3"])
        assertEquals('₹', before["p1"]); assertEquals('₹', after["p1"])
    }

    @Test fun `growing by a digit leaves the existing digits on their keys`() {
        val small = keys("₹9,999")
        val big = keys("₹10,000")
        assertEquals(setOf("p1", "i0", "i1", "i2", "i3", "i4"), small.keys)
        assertEquals(setOf("p1", "i0", "i1", "i2", "i3", "i4", "i5"), big.keys)
        assertEquals('9', small["i4"]); assertEquals('0', big["i4"]) // the thousands digit, same key
    }

    @Test fun `paise and the minus sign are keyed apart from the rupees`() {
        val k = amountSlots("−₹1,250.50")
        assertEquals(listOf("p2", "p1", "i4", "i3", "i2", "i1", "i0", "dot", "f1", "f2"), k.map { it.key })
        // Adding paise doesn't shift the rupee digits' keys.
        assertEquals(keys("₹1,250")["i0"], keys("₹1,250.50")["i0"])
        // Keys are unique, so every character can animate on its own.
        assertEquals(k.size, k.map { it.key }.toSet().size)
    }

    @Test fun `text without digits still gets unique keys`() {
        assertEquals(listOf("p0", "p1"), amountSlots("₹—").map { it.key })
    }

    @Test fun `the confirmation is worded from what was saved`() {
        momentFor(TransactionType.EXPENSE, wasEdit = false, amountPaise = 42_000, category = "Food", account = "HDFC Savings", toAccount = null).let {
            assertEquals(MomentKind.EXPENSE, it.kind)
            assertEquals("Expense Added!", it.title)
            assertEquals("₹420 from Food", it.headline)
            assertEquals("Paid with HDFC Savings", it.detail)
        }
        momentFor(TransactionType.EXPENSE, false, 42_050, category = null, account = null, toAccount = null).let {
            assertEquals("₹420.50", it.headline)
            assertNull(it.detail)
        }
        momentFor(TransactionType.INCOME, false, 15_00_000, category = "Salary", account = "SBI Salary", toAccount = null).let {
            assertEquals(MomentKind.INCOME, it.kind)
            assertEquals("Income Added!", it.title)
            assertEquals("₹15,000 to SBI Salary", it.headline)
        }
        momentFor(TransactionType.TRANSFER, false, 1_00_000, category = null, account = "HDFC Savings", toAccount = "ICICI Coral").let {
            assertEquals(MomentKind.TRANSFER, it.kind)
            assertEquals("Transfer Done!", it.title)
            assertEquals("₹1,000", it.headline)
            assertEquals("HDFC Savings", it.fromLabel)
            assertEquals("ICICI Coral", it.toLabel)
        }
        momentFor(TransactionType.REFUND, false, 50_000, category = "Shopping", account = "HDFC Savings", toAccount = null).let {
            assertEquals(MomentKind.INCOME, it.kind)
            assertEquals("Refund Recorded!", it.title)
            assertEquals("₹500 back to HDFC Savings", it.headline)
        }
        momentFor(TransactionType.EXPENSE, wasEdit = true, amountPaise = 9_900, category = "Food", account = "Cash", toAccount = null).let {
            assertEquals(MomentKind.UPDATED, it.kind)
            assertEquals("Changes Saved", it.title)
        }
    }

    @Test fun `home balance is net worth, the same figure as the Accounts tab`() {
        fun account(id: Long, type: AccountType, balance: Long, archived: Boolean = false) =
            AccountUi(id, "a$id", "", type, balance, null, null, null, 0, archived)
        val accounts = listOf(
            account(1, AccountType.SAVINGS, 50_000_00),
            account(2, AccountType.CASH, 2_500_00),
            // A debit card shows its bank's balance; counting it would count the bank twice.
            account(3, AccountType.DEBIT_CARD, 50_000_00),
            // ₹12,000 owed on a card.
            account(4, AccountType.CREDIT_CARD, -12_000_00),
            account(5, AccountType.WALLET, 300_00, archived = true),
        )
        val nw = accounts.netWorth()
        assertEquals(52_800_00L, nw.assetsPaise)
        assertEquals(12_000_00L, nw.liabilitiesPaise)
        assertEquals(40_800_00L, nw.netPaise)
        assertEquals(nw, io.github.sharveshar.spendly.ui.accounts.AccountsUiState(false, accounts).netWorth)
    }
}
