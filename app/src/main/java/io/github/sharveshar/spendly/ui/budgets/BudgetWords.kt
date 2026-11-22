// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.budgets

import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.BudgetPeriod

/** How budgets talk about their period, in one place so every screen says it the same way. */
object BudgetWords {
    /** "Daily budget", "Weekly budget", "Monthly budget". */
    fun title(period: BudgetPeriod) = "${period.label} budget"

    /** "today", "this week", "this month". */
    fun now(period: BudgetPeriod) = when (period) {
        BudgetPeriod.DAILY -> "today"
        BudgetPeriod.WEEKLY -> "this week"
        BudgetPeriod.MONTHLY -> "this month"
    }

    /** " today" or " this week" after an amount; nothing for a monthly budget, whose month is on screen. */
    fun nowSuffix(period: BudgetPeriod) = if (period == BudgetPeriod.MONTHLY) "" else " ${now(period)}"

    /** "Every day", "Every week", "Every month". */
    fun every(period: BudgetPeriod) = "Every ${period.unit}"

    /** "₹500 a day". */
    fun perPeriod(paise: Long, period: BudgetPeriod) = "${Money.format(paise)} a ${period.unit}"

    /** "₹500 a day" or, for a monthly budget, just "₹500"; [compact] gives "₹1.2K a week". */
    fun amount(paise: Long, period: BudgetPeriod, compact: Boolean = false): String {
        val text = if (compact) Money.formatCompact(paise) else Money.format(paise)
        return if (period == BudgetPeriod.MONTHLY) text else "$text a ${period.unit}"
    }

    /** "1 day", "18 days", "1 week". */
    fun count(n: Int, period: BudgetPeriod) = "$n ${period.unit}" + if (n == 1) "" else "s"

    /** What the editor says about when the budget starts over. */
    fun rhythm(period: BudgetPeriod) = when (period) {
        BudgetPeriod.DAILY -> "Starts over every day at midnight."
        BudgetPeriod.WEEKLY -> "Runs Monday to Sunday and starts over every Monday."
        BudgetPeriod.MONTHLY -> "Starts over on the 1st of every month."
    }
}
