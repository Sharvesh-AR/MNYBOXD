// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

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

    @Test fun `week recap compares the days so far with the same days last week`() {
        // Wednesday 11 November 2026: the week runs Monday 9 to Sunday 15.
        val r = listOf(
            rec(TransactionType.EXPENSE, 500_00, cat = 1, date = "2026-11-09"),
            rec(TransactionType.EXPENSE, 1_500_00, cat = 2, date = "2026-11-11"),
            rec(TransactionType.EXPENSE, 200_00, cat = 1, date = "2026-11-11"),
            rec(TransactionType.REFUND, 100_00, cat = 1, date = "2026-11-11"),
            rec(TransactionType.INCOME, 9_000_00, cat = null, date = "2026-11-10"),
            rec(TransactionType.EXPENSE, 9_999_00, cat = 1, date = "2026-11-12"), // tomorrow: not yet
            // Last week: Monday to Wednesday count, Thursday doesn't.
            rec(TransactionType.EXPENSE, 1_000_00, cat = 1, date = "2026-11-02"),
            rec(TransactionType.EXPENSE, 1_000_00, cat = 1, date = "2026-11-04"),
            rec(TransactionType.EXPENSE, 5_000_00, cat = 1, date = "2026-11-05"),
        )
        val w = Insights.weekRecap(r, LocalDate.parse("2026-11-11"), zone)
        assertEquals(LocalDate.parse("2026-11-09"), w.start)
        assertEquals(7, w.totalDays)
        assertEquals(3, w.elapsedDays)
        assertEquals(2_100_00L, w.spentPaise)
        assertEquals(2_000_00L, w.previousPaise)
        assertEquals(5, w.change)
        assertEquals(9_000_00L, w.incomePaise)
        assertEquals(3, w.expenseCount)
        assertEquals(listOf(500_00L, 0L, 1_600_00L, 0L, 0L, 0L, 0L), w.daily)
        assertEquals(2L, w.topCategoryId)
        assertEquals(1_500_00L, w.topCategoryPaise)
        assertEquals(71, w.topShare)
        assertEquals(1_500_00L, w.biggestPaise)
        assertEquals(LocalDate.parse("2026-11-11"), w.biggestDay)
        assertEquals(LocalDate.parse("2026-11-11"), w.busiestDay)
        assertEquals(1, w.noSpendDays)
        assertEquals(700_00L, w.dailyAverage)
    }

    @Test fun `month recap of the current month uses the same days of last month`() {
        val r = listOf(
            rec(TransactionType.EXPENSE, 3_000_00, date = "2026-11-02"),
            rec(TransactionType.EXPENSE, 1_000_00, date = "2026-10-05"),
            rec(TransactionType.EXPENSE, 7_000_00, date = "2026-10-20"),
        )
        val m = Insights.monthRecap(r, YearMonth.of(2026, 11), LocalDate.parse("2026-11-11"), zone)
        assertEquals(11, m.elapsedDays)
        assertEquals(30, m.totalDays)
        assertEquals(3_000_00L, m.spentPaise)
        assertEquals(1_000_00L, m.previousPaise)
        assertEquals(10, m.noSpendDays)
        // A finished month is compared with all of the month before.
        val oct = Insights.monthRecap(r, YearMonth.of(2026, 10), LocalDate.parse("2026-11-11"), zone)
        assertEquals(31, oct.elapsedDays)
        assertEquals(8_000_00L, oct.spentPaise)
        // March has more days than February: compared with the whole of February.
        val feb = listOf(rec(TransactionType.EXPENSE, 400_00, date = "2026-02-27"), rec(TransactionType.EXPENSE, 100_00, date = "2026-03-31"))
        assertEquals(400_00L, Insights.monthRecap(feb, YearMonth.of(2026, 3), LocalDate.parse("2026-04-02"), zone).previousPaise)
    }

    @Test fun `recap of a quiet period has no top category or change`() {
        val w = Insights.weekRecap(emptyList(), LocalDate.parse("2026-11-15"), zone)
        assertEquals(7, w.elapsedDays)
        assertEquals(0L, w.spentPaise)
        assertNull(w.change)
        assertNull(w.topCategoryId)
        assertNull(w.busiestDay)
        assertEquals(7, w.noSpendDays)
        assertEquals(false, w.hasSpending)
    }
}
