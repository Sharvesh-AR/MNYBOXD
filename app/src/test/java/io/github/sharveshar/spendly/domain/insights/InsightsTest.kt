package io.github.sharveshar.spendly.domain.insights

import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(date: String, hour: Int = 12) =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun rec(type: TransactionType, amount: Long, cat: Long? = 1, ch: PaymentChannel? = PaymentChannel.UPI, date: String = "2026-11-05", hour: Int = 12) =
        SpendRecord(type, amount, cat, ch, at(date, hour))

    @Test fun `net spend subtracts refunds and ignores transfers and income`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 10_000),
            rec(TransactionType.EXPENSE, 5_050),
            rec(TransactionType.REFUND, 3_000),
            rec(TransactionType.TRANSFER, 999_999),
            rec(TransactionType.INCOME, 100_000),
        )
        assertEquals(12_050L, Insights.netSpend(r))
        assertEquals(100_000L, Insights.income(r))
    }

    @Test fun `category breakdown nets refunds and drops non positive`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 10_000, cat = 1),
            rec(TransactionType.EXPENSE, 20_000, cat = 2),
            rec(TransactionType.REFUND, 20_000, cat = 2),
            rec(TransactionType.EXPENSE, 3_000, cat = 3),
        )
        assertEquals(listOf<Pair<Long?, Long>>(1L to 10_000L, 3L to 3_000L), Insights.byCategory(r))
    }

    @Test fun `channel breakdown`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 100, ch = PaymentChannel.CASH),
            rec(TransactionType.EXPENSE, 300, ch = PaymentChannel.UPI),
            rec(TransactionType.EXPENSE, 200, ch = PaymentChannel.CASH),
        )
        assertEquals(listOf<Pair<PaymentChannel?, Long>>(PaymentChannel.CASH to 300L, PaymentChannel.UPI to 300L).toSet(), Insights.byChannel(r).toSet())
    }

    @Test fun `daily buckets use local time zone boundaries`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 100, date = "2026-11-01", hour = 0),
            rec(TransactionType.EXPENSE, 200, date = "2026-11-01", hour = 23),
            rec(TransactionType.EXPENSE, 400, date = "2026-11-03"),
            rec(TransactionType.REFUND, 50, date = "2026-11-03"),
            rec(TransactionType.EXPENSE, 999, date = "2026-10-31", hour = 23), // before window
        )
        assertEquals(listOf(300L, 0L, 350L), Insights.dailyNetSpend(r, LocalDate.parse("2026-11-01"), 3, zone))
    }

    @Test fun `month range is half open in local zone`() {
        val range = TimeRange.month(YearMonth.of(2026, 11), zone)
        assertEquals(at("2026-11-01", 0), range.startMillis)
        assertEquals(at("2026-12-01", 0), range.endMillis)
        assert(at("2026-11-30", 23) in range)
        assert(range.endMillis !in range)
    }

    @Test fun `monthly totals`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 1_000, date = "2026-10-10"),
            rec(TransactionType.INCOME, 5_000, date = "2026-10-11"),
            rec(TransactionType.EXPENSE, 2_000, date = "2026-11-10"),
        )
        val totals = Insights.monthlyTotals(r, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), zone)
        assertEquals(listOf(1_000L, 2_000L, 0L), totals.map { it.spentPaise })
        assertEquals(listOf(5_000L, 0L, 0L), totals.map { it.incomePaise })
    }

    @Test fun `daily average counts only elapsed days in current month`() {
        val today = LocalDate.parse("2026-11-07")
        assertEquals(1_000L, Insights.dailyAverage(7_000, YearMonth.of(2026, 11), today))
        assertEquals(1_000L, Insights.dailyAverage(31_000, YearMonth.of(2026, 10), today))
        assertEquals(0L, Insights.dailyAverage(31_000, YearMonth.of(2026, 12), today))
        assertEquals(143L, Insights.dailyAverage(1_000, YearMonth.of(2026, 11), today)) // 142.86 → 143
    }

    @Test fun `percent change`() {
        assertEquals(50, Insights.percentChange(150, 100))
        assertEquals(-25, Insights.percentChange(75, 100))
        assertNull(Insights.percentChange(75, 0))
    }

    @Test fun `budget thresholds at 80 and 100 percent`() {
        assertEquals(BudgetLevel.OK, BudgetStatus(10_000, 7_999).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(10_000, 8_000).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(10_000, 9_999).level)
        assertEquals(BudgetLevel.EXCEEDED, BudgetStatus(10_000, 10_000).level)
        assertEquals(BudgetLevel.EXCEEDED, BudgetStatus(10_000, 25_000).level)
        assertEquals(-15_000L, BudgetStatus(10_000, 25_000).remainingPaise)
        assertEquals(250, BudgetStatus(10_000, 25_000).percentUsed)
        assertEquals(1f, BudgetStatus(10_000, 25_000).fraction)
        assertEquals(0f, BudgetStatus(10_000, -500).fraction)
        // Odd limits: 80% of ₹333.33 is 26666.4 paise → 26667 paise needed to warn.
        assertEquals(BudgetLevel.OK, BudgetStatus(33_333, 26_666).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(33_333, 26_667).level)
    }

    @Test fun `crossing thresholds is reported once`() {
        assertEquals(BudgetLevel.WARNING, BudgetStatus.crossed(BudgetStatus(100, 70), BudgetStatus(100, 85)))
        assertEquals(BudgetLevel.EXCEEDED, BudgetStatus.crossed(BudgetStatus(100, 85), BudgetStatus(100, 100)))
        assertEquals(BudgetLevel.EXCEEDED, BudgetStatus.crossed(BudgetStatus(100, 10), BudgetStatus(100, 120)))
        assertNull(BudgetStatus.crossed(BudgetStatus(100, 85), BudgetStatus(100, 90)))
        assertNull(BudgetStatus.crossed(BudgetStatus(100, 85), BudgetStatus(100, 10)))
    }
}
