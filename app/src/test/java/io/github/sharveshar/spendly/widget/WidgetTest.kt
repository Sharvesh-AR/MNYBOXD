package io.github.sharveshar.spendly.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.db.AccountEntity
import io.github.sharveshar.spendly.data.db.AccountWithBalance
import io.github.sharveshar.spendly.domain.insights.SpendRecord
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 8)

    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun account(id: Long, name: String, type: AccountType, balance: Long, archived: Boolean = false) =
        AccountWithBalance(AccountEntity(id, name, "", type, 0L, null, null, 0, archived, id.toInt(), 0L), balance)

    private val accounts = listOf(
        account(1, "Cash", AccountType.CASH, 2_000_00),
        account(2, "HDFC Savings", AccountType.SAVINGS, 50_000_00),
        account(3, "ICICI Coral", AccountType.CREDIT_CARD, -12_500_00),
        account(4, "HDFC Debit", AccountType.DEBIT_CARD, 50_000_00),
        account(5, "Old wallet", AccountType.WALLET, 900_00, archived = true),
        account(6, "Paid-off card", AccountType.CREDIT_CARD, 0),
    )
    private val records = listOf(
        SpendRecord(TransactionType.EXPENSE, 400_00, 1, null, at(today)),
        SpendRecord(TransactionType.EXPENSE, 1_000_00, 1, null, at(today.minusDays(2))),
        SpendRecord(TransactionType.REFUND, 100_00, 1, null, at(today.minusDays(2))),
        SpendRecord(TransactionType.INCOME, 80_000_00, null, null, at(today.withDayOfMonth(1))),
        SpendRecord(TransactionType.EXPENSE, 7_000_00, 1, null, at(today.withDayOfMonth(1), 9)), // earlier this month, outside the week
        SpendRecord(TransactionType.EXPENSE, 3_000_00, 1, null, at(today.minusMonths(1))), // last month
    )

    private fun snapshot(budget: Long? = 20_000_00) = WidgetSnapshot.build(today, zone, records, accounts, budget, hasAnyTransactions = true)

    @Test fun `card debt is never mixed into available money`() {
        val s = snapshot()
        // Cash + HDFC Savings. The debit card spends from HDFC (no double count), the archived wallet is hidden.
        assertEquals(52_000_00L, s.availablePaise)
        assertEquals(12_500_00L, s.cardsOwedPaise)
        assertEquals(listOf("Cash", "HDFC Savings", "ICICI Coral", "Paid-off card"), s.accounts.map { it.name })
        assertEquals(12_500_00L, s.accounts.single { it.name == "ICICI Coral" }.amountPaise)
    }

    @Test fun `month, today, week and budget follow the dashboard's rules`() {
        val s = snapshot()
        assertEquals(400_00L + 1_000_00 - 100_00 + 7_000_00, s.spentMonthPaise) // refunds reduce spending
        assertEquals(80_000_00L, s.incomeMonthPaise)
        assertEquals(400_00L, s.spentTodayPaise)
        assertEquals(5, s.transactionsThisMonth)
        assertEquals(listOf(0L, 0L, 0L, 0L, 900_00L, 0L, 400_00L), s.week)
        assertEquals(4, s.highestDayIndex)
        assertEquals(20_000_00L - s.spentMonthPaise, s.budgetRemainingPaise)
        assertEquals(41, s.budgetPercent)
        assertEquals("November", s.monthLabel)
    }

    @Test fun `empty database gives honest zeros and no highest day`() {
        val s = WidgetSnapshot.build(today, zone, emptyList(), listOf(account(1, "Cash", AccountType.CASH, 0)), null, hasAnyTransactions = false)
        assertEquals(0L, s.spentMonthPaise)
        assertNull(s.highestDayIndex)
        assertNull(s.budgetRemainingPaise)
        assertEquals(0L, s.cardsOwedPaise)
        assertFalse(s.hasAnyTransactions)
    }

    private fun inflate(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun `every widget layout inflates at every size, in every theme, with data or empty`() {
        val data = listOf(snapshot(), snapshot(budget = null), WidgetSnapshot.build(today, zone, emptyList(), emptyList(), null, false))
        for (kind in WidgetKind.entries) {
            for (bp in WidgetRenderer.sizesFor(kind, context, widgetId = 7)) {
                for (palette in Palettes.all) {
                    for (d in data) {
                        inflate(WidgetRenderer.layout(context, kind, 7, bp, d, palette))
                    }
                }
            }
            inflate(WidgetRenderer.message(context, kind, 7, "Open MNYBOXD to get started", Palettes.Default))
        }
    }

    @Test fun `a single-account widget shows that account, and a missing one says so`() {
        WidgetPrefs.setAccount(context, 9, 3)
        val bp = WidgetRenderer.sizesFor(WidgetKind.ACCOUNTS, context, 9).single()
        val shown = texts(inflate(WidgetRenderer.layout(context, WidgetKind.ACCOUNTS, 9, bp, snapshot(), Palettes.Default)))
        assertTrue(shown.containsAll(listOf("ICICI Coral", "Owed", "₹12,500")))
        WidgetPrefs.setAccount(context, 9, 999)
        val missing = texts(inflate(WidgetRenderer.layout(context, WidgetKind.ACCOUNTS, 9, bp, snapshot(), Palettes.Default)))
        assertTrue(missing.contains("Account not found"))
        WidgetPrefs.forget(context, intArrayOf(9))
        assertNull(WidgetPrefs.account(context, 9))
    }

    @Test fun `account widget shows available money and card dues separately`() {
        val bp = WidgetRenderer.sizesFor(WidgetKind.ACCOUNTS, context, 11).last()
        val shown = texts(inflate(WidgetRenderer.layout(context, WidgetKind.ACCOUNTS, 11, bp, snapshot(), Palettes.Default)))
        assertTrue(shown.contains("₹52,000"))
        assertTrue(shown.contains("₹12,500"))
    }

    @Test fun `Add income and Expense + Income widgets show their buttons at every size`() {
        fun shown(kind: WidgetKind) = WidgetRenderer.sizesFor(kind, context, 12).map { bp ->
            texts(inflate(WidgetRenderer.layout(context, kind, 12, bp, snapshot(), Palettes.Default))).filter { it.isNotBlank() }
        }
        val income = shown(WidgetKind.ADD_INCOME)
        assertEquals(listOf("Income"), income[0])
        assertEquals(listOf("Add income"), income[1])
        assertTrue(income[2].containsAll(listOf("Add income", "+₹80,000 this month")))

        val both = shown(WidgetKind.ADD_BOTH)
        assertEquals(emptyList<String>(), both[0]) // just the two arrows when narrow
        assertEquals(listOf("Expense", "Income"), both[1])
        assertTrue(both[2].containsAll(listOf("Expense", "Income", "Today ₹400")))
    }
}
