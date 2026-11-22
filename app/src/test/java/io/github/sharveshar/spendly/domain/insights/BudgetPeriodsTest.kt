// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.domain.insights

import io.github.sharveshar.spendly.domain.model.BudgetPeriod
import io.github.sharveshar.spendly.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Daily, weekly and monthly budgets. November 2026 starts on a Sunday and ends on a Monday. */
class BudgetPeriodsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val nov = YearMonth.of(2026, 11)
    private fun d(s: String) = LocalDate.parse(s)
    private fun spend(amount: Long, date: String, type: TransactionType = TransactionType.EXPENSE, cat: Long? = 1) =
        SpendRecord(type, amount, cat, null, d(date).atTime(13, 0).atZone(zone).toInstant().toEpochMilli())

    private fun dailySpend(vararg records: SpendRecord, month: YearMonth = nov) =
        DailySpend.of(records.toList(), BudgetWindows.around(month), zone)

    @Test fun `each period's window around a date`() {
        assertEquals(BudgetWindow(d("2026-11-18"), 1), BudgetWindows.of(BudgetPeriod.DAILY, d("2026-11-18")))
        // Weeks run Monday to Sunday, whatever day it is.
        assertEquals(BudgetWindow(d("2026-11-16"), 7), BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-18")))
        assertEquals(BudgetWindow(d("2026-11-16"), 7), BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-16")))
        assertEquals(BudgetWindow(d("2026-11-16"), 7), BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-22")))
        assertEquals(BudgetWindow(d("2026-11-01"), 30), BudgetWindows.of(BudgetPeriod.MONTHLY, d("2026-11-18")))
        assertEquals(BudgetWindow(d("2028-02-01"), 29), BudgetWindows.of(BudgetPeriod.MONTHLY, d("2028-02-29")))
        assertTrue(d("2026-11-22") in BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-18")))
        assertTrue(d("2026-11-23") !in BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-18")))
    }

    @Test fun `a month's days, the weeks touching it, or the month itself`() {
        val days = BudgetWindows.inMonth(BudgetPeriod.DAILY, nov)
        assertEquals(30, days.size)
        assertEquals(d("2026-11-30"), days.last().start)
        // Sun 1 Nov sits in the week from Mon 26 Oct; Mon 30 Nov starts the last one: six weeks.
        val weeks = BudgetWindows.inMonth(BudgetPeriod.WEEKLY, nov)
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23", "2026-11-30").map(::d), weeks.map { it.start })
        // February 2027 starts on a Monday and ends on a Sunday: exactly four weeks.
        assertEquals(4, BudgetWindows.inMonth(BudgetPeriod.WEEKLY, YearMonth.of(2027, 2)).size)
        assertEquals(listOf(BudgetWindow(d("2026-11-01"), 30)), BudgetWindows.inMonth(BudgetPeriod.MONTHLY, nov))
        // Everything any of them needs: Monday 26 Oct to Sunday 6 Dec.
        assertEquals(BudgetWindow(d("2026-10-26"), 42), BudgetWindows.around(nov))
    }

    @Test fun `daily spending sums windows, nets refunds and ignores income`() {
        val s = dailySpend(
            spend(300_00, "2026-11-18"),
            spend(200_00, "2026-11-18"),
            spend(50_00, "2026-11-18", TransactionType.REFUND),
            spend(9_999_00, "2026-11-18", TransactionType.INCOME),
            spend(100_00, "2026-11-20"),
            spend(70_00, "2026-10-27"),
        )
        assertEquals(450_00L, s.on(BudgetWindows.of(BudgetPeriod.DAILY, d("2026-11-18"))))
        assertEquals(550_00L, s.on(BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-18"))))
        // The week of 1 November begins in October.
        assertEquals(70_00L, s.on(BudgetWindows.of(BudgetPeriod.WEEKLY, d("2026-11-01"))))
        assertEquals(550_00L, s.on(BudgetWindows.of(BudgetPeriod.MONTHLY, d("2026-11-18"))))
        // Days outside what was loaded count as nothing.
        assertEquals(0L, s.on(BudgetWindow(d("2027-01-01"), 7)))
    }

    @Test fun `this month - today or this week, plus every day or week so far`() {
        val s = dailySpend(spend(450_00, "2026-11-02"), spend(520_00, "2026-11-03"), spend(410_00, "2026-11-04"))
        val v = BudgetMath.view(BudgetPeriod.DAILY, 500_00, nov, d("2026-11-04"), s) as BudgetView.Live
        assertEquals(BudgetStatus(500_00, 410_00), v.status)
        assertEquals(BudgetLevel.WARNING, v.status.level)
        assertEquals(30, v.track.size)
        assertEquals(listOf(BudgetLevel.OK, BudgetLevel.WARNING, BudgetLevel.EXCEEDED, BudgetLevel.WARNING), v.track.take(4))
        assertTrue(v.track.drop(4).all { it == null })
        assertEquals(3, v.kept)
        assertEquals(4, v.elapsed)

        val w = BudgetMath.view(BudgetPeriod.WEEKLY, 1_000_00, nov, d("2026-11-04"), s) as BudgetView.Live
        assertEquals(BudgetStatus(1_000_00, 1_380_00), w.status)
        assertEquals(listOf(BudgetLevel.OK, BudgetLevel.EXCEEDED, null, null, null, null), w.track)
    }

    @Test fun `a month that's over - how each day or week went`() {
        val s = dailySpend(spend(600_00, "2026-11-10"), spend(100_00, "2026-11-11"))
        val v = BudgetMath.view(BudgetPeriod.DAILY, 500_00, nov, d("2026-12-05"), s) as BudgetView.Past
        assertEquals(30, v.track.size)
        assertEquals(29, v.kept)
        assertEquals(1, v.over)
        assertEquals(BudgetLevel.EXCEEDED, v.track[9])
        val w = BudgetMath.view(BudgetPeriod.WEEKLY, 700_00, nov, d("2026-12-05"), s) as BudgetView.Past
        assertEquals(6, w.track.size)
        assertEquals(1, w.over)
    }

    @Test fun `months to come, and monthly budgets in any month`() {
        val s = dailySpend(spend(9_000_00, "2026-11-10"))
        assertEquals(BudgetView.Upcoming(BudgetPeriod.WEEKLY, 2_000_00), BudgetMath.view(BudgetPeriod.WEEKLY, 2_000_00, nov, d("2026-10-30"), s))
        val past = BudgetMath.view(BudgetPeriod.MONTHLY, 20_000_00, nov, d("2027-03-01"), s) as BudgetView.Live
        assertEquals(BudgetStatus(20_000_00, 9_000_00), past.status)
        assertTrue(past.track.isEmpty())
        val ahead = BudgetMath.view(BudgetPeriod.MONTHLY, 20_000_00, nov, d("2026-10-01"), s) as BudgetView.Live
        assertEquals(9_000_00L, ahead.status.spentPaise)
    }

    @Test fun `refunds never give a budget extra room`() {
        val s = dailySpend(spend(200_00, "2026-11-10"), spend(500_00, "2026-11-10", TransactionType.REFUND))
        val v = BudgetMath.view(BudgetPeriod.DAILY, 300_00, nov, d("2026-11-10"), s) as BudgetView.Live
        assertEquals(0L, v.status.spentPaise)
        assertEquals(300_00L, v.status.remainingPaise)
    }

    @Test fun `what a daily or weekly limit comes to over a month`() {
        assertEquals(15_000_00L, BudgetMath.monthEquivalent(BudgetPeriod.DAILY, 500_00, nov))
        assertEquals(15_500_00L, BudgetMath.monthEquivalent(BudgetPeriod.DAILY, 500_00, YearMonth.of(2026, 12)))
        // ₹3,000 a week over 30 days is ₹12,857.14: rounded to the rupee.
        assertEquals(12_857_00L, BudgetMath.monthEquivalent(BudgetPeriod.WEEKLY, 3_000_00, nov))
        assertEquals(20_000_00L, BudgetMath.monthEquivalent(BudgetPeriod.MONTHLY, 20_000_00, nov))
    }
}
