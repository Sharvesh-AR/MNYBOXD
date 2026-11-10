// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import io.github.sharveshar.spendly.MainActivity
import io.github.sharveshar.spendly.QuickAddActivity
import io.github.sharveshar.spendly.R
import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.ui.theme.SpendlyPalette
import kotlin.math.max

/** The home-screen widgets. [id] is stable: it's part of PendingIntent request codes. */
enum class WidgetKind(val id: Int) { QUICK_ADD(1), MONTHLY(2), ACCOUNTS(3), TREND(4), STATS(5), ADD_INCOME(6), ADD_BOTH(7) }

/**
 * Builds the RemoteViews for each widget, its sizes and the active theme.
 *
 * On Android 12+ every size's layout is handed to the launcher at once, so resizing switches
 * layouts instantly without waiting for the app. Older versions get the layout for the current
 * size and are re-rendered when the widget is resized. Every touched view id must exist in the
 * layout it's applied to, or the launcher would show "Can't load widget".
 */
object WidgetRenderer {

    /** Content state: ready with data, or a message (first run, database locked). */
    sealed interface Content {
        data class Ready(val data: WidgetSnapshot) : Content
        data class Message(val text: String) : Content
    }

    fun build(context: Context, kind: WidgetKind, widgetId: Int, content: Content, widthDp: Int, heightDp: Int): RemoteViews {
        val p = WidgetPrefs.palette(context)
        if (content is Content.Message) return message(context, kind, widgetId, content.text, p)
        val data = (content as Content.Ready).data
        val sizes = sizesFor(kind, context, widgetId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(sizes.associate { s -> SizeF(s.minWidth, s.minHeight) to layout(context, kind, widgetId, s, data, p) })
        } else {
            forSize(context, kind, widgetId, content, widthDp, heightDp)
        }
    }

    /** The single layout that fits [widthDp] × [heightDp]: older Android, and in-app previews. */
    fun forSize(context: Context, kind: WidgetKind, widgetId: Int, content: Content, widthDp: Int, heightDp: Int): RemoteViews {
        val p = WidgetPrefs.palette(context)
        if (content is Content.Message) return message(context, kind, widgetId, content.text, p)
        val sizes = sizesFor(kind, context, widgetId)
        // The biggest layout that fits the reported size.
        val fit = sizes.filter { widthDp >= it.minWidth && heightDp >= it.minHeight }.maxByOrNull { it.minWidth * it.minHeight } ?: sizes.first()
        return layout(context, kind, widgetId, fit, (content as Content.Ready).data, p)
    }

    /** One responsive breakpoint: the layout used from this size (dp) upwards. */
    internal data class Breakpoint(val minWidth: Float, val minHeight: Float, @LayoutRes val layout: Int, val chartWidthDp: Int = 0, val chartHeightDp: Int = 0)

    @androidx.annotation.VisibleForTesting
    internal fun sizesFor(kind: WidgetKind, context: Context, widgetId: Int): List<Breakpoint> = when (kind) {
        WidgetKind.QUICK_ADD -> listOf(
            Breakpoint(100f, 40f, R.layout.widget_quick_add_small),
            Breakpoint(200f, 40f, R.layout.widget_quick_add),
            Breakpoint(200f, 110f, R.layout.widget_quick_add_large),
        )
        WidgetKind.MONTHLY -> listOf(
            Breakpoint(100f, 100f, R.layout.widget_monthly_small),
            Breakpoint(220f, 100f, R.layout.widget_monthly, chartWidthDp = 104, chartHeightDp = 70),
        )
        WidgetKind.ACCOUNTS -> if (WidgetPrefs.account(context, widgetId) != null) {
            listOf(Breakpoint(100f, 90f, R.layout.widget_account_single))
        } else {
            listOf(
                Breakpoint(100f, 100f, R.layout.widget_accounts_small),
                Breakpoint(220f, 140f, R.layout.widget_accounts),
            )
        }
        WidgetKind.TREND -> listOf(
            Breakpoint(100f, 100f, R.layout.widget_trend_small, chartWidthDp = 130, chartHeightDp = 60),
            Breakpoint(220f, 100f, R.layout.widget_trend, chartWidthDp = 260, chartHeightDp = 80),
        )
        WidgetKind.STATS -> listOf(
            Breakpoint(200f, 40f, R.layout.widget_stats_wide),
            Breakpoint(100f, 100f, R.layout.widget_stats),
        )
        WidgetKind.ADD_INCOME -> listOf(
            Breakpoint(100f, 40f, R.layout.widget_add_income_small),
            Breakpoint(200f, 40f, R.layout.widget_add_income),
            Breakpoint(200f, 110f, R.layout.widget_add_income_large),
        )
        // Labelled buttons need about 100dp each; narrower, they show just the arrows.
        WidgetKind.ADD_BOTH -> listOf(
            Breakpoint(100f, 40f, R.layout.widget_add_both_small),
            Breakpoint(220f, 40f, R.layout.widget_add_both),
            Breakpoint(220f, 110f, R.layout.widget_add_both_large),
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun layout(context: Context, kind: WidgetKind, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette): RemoteViews {
        val v = RemoteViews(context.packageName, bp.layout)
        v.tint(R.id.bg, p.surface)
        when (kind) {
            WidgetKind.QUICK_ADD -> quickAdd(context, v, widgetId, bp, d, p)
            WidgetKind.MONTHLY -> monthly(context, v, widgetId, bp, d, p)
            WidgetKind.ACCOUNTS -> accounts(context, v, widgetId, bp, d, p)
            WidgetKind.TREND -> trend(context, v, widgetId, bp, d, p)
            WidgetKind.STATS -> stats(context, v, widgetId, bp, d, p)
            WidgetKind.ADD_INCOME -> addIncome(context, v, widgetId, bp, d, p)
            WidgetKind.ADD_BOTH -> addBoth(context, v, widgetId, bp, d, p)
        }
        return v
    }

    // --- Widgets ---------------------------------------------------------------------------

    private fun quickAdd(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.tint(R.id.add_bg, p.accent)
        v.tint(R.id.add_icon, p.onAccent)
        v.setTextColor(R.id.add_label, p.onAccent.toArgb())
        v.tint(R.id.voice_bg, p.surfaceElevated)
        v.tint(R.id.voice_icon, p.accent)
        v.setOnClickPendingIntent(R.id.add_button, quickAddIntent(context, widgetId, voice = false))
        v.setOnClickPendingIntent(R.id.voice_button, quickAddIntent(context, widgetId, voice = true))
        v.setContentDescription(R.id.add_button, "Add expense")
        v.setContentDescription(R.id.voice_button, "Add expense by voice")
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.QUICK_ADD, MainActivity.ACTION_OPEN_HOME))
        when (bp.layout) {
            R.layout.widget_quick_add -> logoTile(context, v, widgetId, WidgetKind.QUICK_ADD, p)
            R.layout.widget_quick_add_large -> {
                v.tint(R.id.logo, p.accent)
                v.setTextColor(R.id.brand, p.text.toArgb())
                v.setTextColor(R.id.caption, p.textMuted.toArgb())
                v.text(R.id.today, if (d.spentTodayPaise != 0L) "Today ${Money.format(d.spentTodayPaise)}" else "Nothing spent today", p.textMuted)
            }
        }
    }

    /** Like Quick Add, but the button opens Quick Add on Income. */
    private fun addIncome(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.tint(R.id.income_bg, p.accent)
        v.tint(R.id.income_icon, p.onAccent)
        v.setTextColor(R.id.income_label, p.onAccent.toArgb())
        v.setOnClickPendingIntent(R.id.income_button, addIncomeIntent(context, widgetId))
        v.setContentDescription(R.id.income_button, "Add income")
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.ADD_INCOME, MainActivity.ACTION_OPEN_HOME))
        when (bp.layout) {
            R.layout.widget_add_income -> logoTile(context, v, widgetId, WidgetKind.ADD_INCOME, p)
            R.layout.widget_add_income_large -> {
                v.tint(R.id.logo, p.accent)
                v.setTextColor(R.id.brand, p.text.toArgb())
                v.setTextColor(R.id.caption, p.textMuted.toArgb())
                val inThisMonth = d.incomeMonthPaise
                v.text(R.id.today, if (inThisMonth > 0) "+${Money.format(inThisMonth)} this month" else "Nothing in yet this month", if (inThisMonth > 0) p.positive else p.textMuted)
            }
        }
    }

    /** Two buttons: Expense (the main, filled one) and Income (money in, in the positive colour). */
    private fun addBoth(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.tint(R.id.add_bg, p.accent)
        v.tint(R.id.add_icon, p.onAccent)
        v.tint(R.id.income_bg, p.surfaceElevated)
        v.tint(R.id.income_icon, p.positive)
        // The narrowest size shows only the arrows, so there are no labels to colour.
        if (bp.layout != R.layout.widget_add_both_small) {
            v.setTextColor(R.id.add_label, p.onAccent.toArgb())
            v.setTextColor(R.id.income_label, p.positive.toArgb())
        }
        v.setOnClickPendingIntent(R.id.add_button, quickAddIntent(context, widgetId, voice = false))
        v.setOnClickPendingIntent(R.id.income_button, addIncomeIntent(context, widgetId))
        v.setContentDescription(R.id.add_button, "Add expense")
        v.setContentDescription(R.id.income_button, "Add income")
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.ADD_BOTH, MainActivity.ACTION_OPEN_HOME))
        if (bp.layout == R.layout.widget_add_both_large) {
            v.tint(R.id.logo, p.accent)
            v.setTextColor(R.id.brand, p.text.toArgb())
            v.setTextColor(R.id.caption, p.textMuted.toArgb())
            v.text(R.id.today, if (d.spentTodayPaise != 0L) "Today ${Money.format(d.spentTodayPaise)}" else "Nothing spent today", p.textMuted)
        }
    }

    private fun logoTile(context: Context, v: RemoteViews, widgetId: Int, kind: WidgetKind, p: SpendlyPalette) {
        v.tint(R.id.logo_bg, p.surfaceElevated)
        v.tint(R.id.logo, p.accent)
        v.setOnClickPendingIntent(R.id.logo_tile, openApp(context, widgetId, kind, MainActivity.ACTION_OPEN_HOME))
        v.setContentDescription(R.id.logo_tile, "Open MNYBOXD")
    }

    private fun monthly(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.MONTHLY, MainActivity.ACTION_OPEN_BUDGETS))
        v.text(R.id.label, "Spent in ${d.monthLabel}", p.textMuted)
        v.text(R.id.amount, Money.format(d.spentMonthPaise), p.text)
        val fraction = d.budgetFraction
        val barColor = when {
            fraction == null -> p.track
            fraction >= 1f -> p.negative
            fraction >= 0.8f -> p.warning
            else -> p.accent
        }
        v.setImageViewBitmap(R.id.progress, progressBitmap(context, if (bp.layout == R.layout.widget_monthly) 200 else 120, fraction ?: 0f, p.track, barColor))
        val remaining = d.budgetRemainingPaise
        val caption = when {
            remaining == null -> "No monthly budget set"
            remaining >= 0 -> "${Money.format(remaining)} left" + if (bp.layout == R.layout.widget_monthly) " this month" else ""
            else -> "${Money.format(-remaining)} over budget"
        }
        v.text(R.id.caption, caption, if (remaining != null && remaining < 0) p.negative else p.textMuted)
        if (bp.layout == R.layout.widget_monthly) {
            v.text(R.id.detail, d.budgetPaise?.let { "of ${Money.format(it)} · ${d.budgetPercent}%" } ?: "${Money.format(d.incomeMonthPaise)} income", p.textMuted)
            v.setTextColor(R.id.chart_label, p.textMuted.toArgb())
            v.setImageViewBitmap(R.id.chart, barsBitmap(context, bp.chartWidthDp, bp.chartHeightDp, d, p, labels = false))
            v.setContentDescription(R.id.chart, weekDescription(d))
        }
    }

    private fun accounts(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        if (bp.layout == R.layout.widget_account_single) {
            val id = WidgetPrefs.account(context, widgetId)
            val a = d.accounts.firstOrNull { it.id == id }
            v.setOnClickPendingIntent(android.R.id.background, openAccount(context, widgetId, 10, id))
            v.tint(R.id.icon_bg, p.surfaceElevated)
            v.tint(R.id.icon, p.accent)
            if (a == null) {
                // The chosen account was deleted or archived.
                v.text(R.id.name, "Account not found", p.text)
                v.text(R.id.type, "Tap to choose another", p.textMuted)
                v.text(R.id.label, "", p.textMuted)
                v.text(R.id.amount, "", p.text)
                v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.ACCOUNTS, MainActivity.ACTION_OPEN_ACCOUNTS))
            } else {
                v.setImageViewResource(R.id.icon, iconFor(a.type))
                v.text(R.id.name, a.name, p.text)
                v.text(R.id.type, a.type.label, p.textMuted)
                v.text(R.id.label, if (a.isCard) "Owed" else "Balance", p.textMuted)
                v.text(R.id.amount, Money.format(a.amountPaise), if ((a.isCard && a.amountPaise > 0) || (!a.isCard && a.amountPaise < 0)) p.negative else p.text)
            }
            return
        }
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.ACCOUNTS, MainActivity.ACTION_OPEN_ACCOUNTS))
        v.setTextColor(R.id.avail_label, p.textMuted.toArgb())
        v.text(R.id.avail, Money.format(d.availablePaise), if (d.availablePaise < 0) p.negative else p.text)
        v.setTextColor(R.id.owed_label, p.textMuted.toArgb())
        val hasCards = d.accounts.any { it.isCard }
        v.text(R.id.owed, if (hasCards) Money.format(d.cardsOwedPaise) else "No cards", if (d.cardsOwedPaise > 0) p.negative else p.textMuted)
        if (bp.layout == R.layout.widget_accounts) {
            val rows = listOf(
                Row(R.id.row1, R.id.row1_icon_bg, R.id.row1_icon, R.id.row1_name, R.id.row1_amount),
                Row(R.id.row2, R.id.row2_icon_bg, R.id.row2_icon, R.id.row2_name, R.id.row2_amount),
                Row(R.id.row3, R.id.row3_icon_bg, R.id.row3_icon, R.id.row3_name, R.id.row3_amount),
                Row(R.id.row4, R.id.row4_icon_bg, R.id.row4_icon, R.id.row4_name, R.id.row4_amount),
            )
            rows.forEachIndexed { i, r ->
                val a = d.accounts.getOrNull(i)
                // Empty slots stay in the layout (invisible) so rows never jump in height.
                v.setViewVisibility(r.container, if (a == null) View.INVISIBLE else View.VISIBLE)
                if (a != null) {
                    v.tint(r.iconBg, p.surfaceElevated)
                    v.setImageViewResource(r.icon, iconFor(a.type))
                    v.tint(r.icon, if (a.isCard) p.negative else p.accent)
                    v.text(r.name, a.name, p.text)
                    v.text(r.amount, Money.format(a.amountPaise), if ((a.isCard && a.amountPaise > 0) || (!a.isCard && a.amountPaise < 0)) p.negative else p.text)
                    v.setOnClickPendingIntent(r.container, openAccount(context, widgetId, 20 + i, a.id))
                }
            }
            val more = d.accounts.size - rows.size
            v.text(R.id.more, if (more > 0) "+$more more in MNYBOXD" else "", p.textMuted)
        }
    }

    private data class Row(@IdRes val container: Int, @IdRes val iconBg: Int, @IdRes val icon: Int, @IdRes val name: Int, @IdRes val amount: Int)

    private fun trend(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.TREND, MainActivity.ACTION_OPEN_INSIGHTS))
        v.setTextColor(R.id.label, p.textMuted.toArgb())
        v.setImageViewBitmap(R.id.chart, barsBitmap(context, bp.chartWidthDp, bp.chartHeightDp, d, p, labels = bp.layout == R.layout.widget_trend))
        v.setContentDescription(R.id.chart, weekDescription(d))
        if (bp.layout == R.layout.widget_trend) {
            v.text(R.id.total, Money.format(d.weekTotalPaise), p.text)
            val hi = d.highestDayIndex
            v.text(R.id.caption, if (hi == null) "Nothing spent in the last 7 days" else "Highest: ${d.dayLabel(hi)} · ${Money.format(d.week[hi])}", p.textMuted)
        } else {
            v.text(R.id.amount, Money.format(d.weekTotalPaise), p.text)
        }
    }

    private fun stats(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: SpendlyPalette) {
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, WidgetKind.STATS, MainActivity.ACTION_OPEN_HOME))
        val remaining = d.budgetRemainingPaise
        // Short labels on the 2×2 grid so nothing is cut off; the wide strip has room for more.
        val wide = bp.layout == R.layout.widget_stats_wide
        val cells = listOf(
            Triple("Today", Money.format(d.spentTodayPaise), p.text),
            Triple(if (wide) "This month" else "Month", Money.format(d.spentMonthPaise), p.text),
            Triple("Entries", d.transactionsThisMonth.toString(), p.text),
            when {
                remaining == null -> Triple("Budget", "Not set", p.textMuted)
                remaining >= 0 -> Triple(if (wide) "Budget left" else "Left", Money.format(remaining), p.positive)
                else -> Triple(if (wide) "Over budget" else "Over", Money.format(-remaining), p.negative)
            },
        )
        val ids = listOf(
            Triple(R.id.stat1_bg, R.id.stat1_label, R.id.stat1_value),
            Triple(R.id.stat2_bg, R.id.stat2_label, R.id.stat2_value),
            Triple(R.id.stat3_bg, R.id.stat3_label, R.id.stat3_value),
            Triple(R.id.stat4_bg, R.id.stat4_label, R.id.stat4_value),
        )
        ids.zip(cells).forEach { (id, cell) ->
            v.tint(id.first, p.surfaceElevated)
            v.text(id.second, cell.first, p.textMuted)
            v.text(id.third, cell.second, cell.third)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun message(context: Context, kind: WidgetKind, widgetId: Int, text: String, p: SpendlyPalette): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_message)
        v.tint(R.id.bg, p.surface)
        v.tint(R.id.logo, p.accent)
        v.text(R.id.message, text, p.textMuted)
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, widgetId, kind, MainActivity.ACTION_OPEN_HOME))
        return v
    }

    // --- Helpers ---------------------------------------------------------------------------

    private fun RemoteViews.tint(@IdRes id: Int, color: Color) = setInt(id, "setColorFilter", color.toArgb())

    private fun RemoteViews.text(@IdRes id: Int, text: String, color: Color) {
        setTextViewText(id, text)
        setTextColor(id, color.toArgb())
    }

    private fun iconFor(type: AccountType) = when (type) {
        AccountType.CASH -> R.drawable.ic_w_cash
        AccountType.CREDIT_CARD, AccountType.DEBIT_CARD -> R.drawable.ic_w_card
        AccountType.WALLET -> R.drawable.ic_w_wallet
        AccountType.SAVINGS, AccountType.CURRENT -> R.drawable.ic_w_bank
    }

    private fun weekDescription(d: WidgetSnapshot): String =
        "Spending over the last 7 days: " + d.week.indices.joinToString(", ") { "${d.dayLabel(it)} ${Money.format(d.week[it])}" }

    /**
     * Unique, stable request codes so each widget's buttons keep their own PendingIntent (extras
     * aren't part of a PendingIntent's identity, so two account rows must never share a code).
     * Slots: 1 add, 2 voice, 3 + kind id (4..10) open app, 10 single account (only in the
     * account widget, never alongside kind 7's), 20..29 account rows, 30 add income.
     */
    private fun requestCode(widgetId: Int, slot: Int) = widgetId * 64 + slot

    private fun quickAddIntent(context: Context, widgetId: Int, voice: Boolean): PendingIntent {
        val intent = QuickAddActivity.launchIntent(context, if (voice) MainActivity.ACTION_VOICE_ADD else MainActivity.ACTION_QUICK_ADD)
        return PendingIntent.getActivity(context, requestCode(widgetId, if (voice) 2 else 1), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun addIncomeIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = QuickAddActivity.launchIntent(context, MainActivity.ACTION_ADD_INCOME)
        return PendingIntent.getActivity(context, requestCode(widgetId, 30), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(context: Context, widgetId: Int, kind: WidgetKind, action: String): PendingIntent {
        val intent = Intent(action).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode(widgetId, 3 + kind.id), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAccount(context: Context, widgetId: Int, slot: Int, accountId: Long?): PendingIntent {
        val intent = Intent(MainActivity.ACTION_OPEN_ACCOUNT).setClass(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId ?: 0L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode(widgetId, slot), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // --- Bitmaps (drawn here because RemoteViews can't draw shapes or charts) -----------------

    private fun px(context: Context, dp: Number) = (dp.toFloat() * context.resources.displayMetrics.density)

    /** Rounded budget bar. The colour already says whether the budget is fine, close or over. */
    private fun progressBitmap(context: Context, widthDp: Int, fraction: Float, track: Color, fill: Color): Bitmap {
        val w = px(context, widthDp).toInt().coerceAtLeast(1)
        val h = px(context, 8).toInt().coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = h / 2f
        paint.color = track.toArgb()
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        val filled = (w * fraction.coerceIn(0f, 1f))
        if (filled > 0f) {
            paint.color = fill.toArgb()
            c.drawRoundRect(RectF(0f, 0f, max(filled, h.toFloat()), h.toFloat()), r, r, paint)
        }
        return bmp
    }

    /** Seven rounded bars, today's in the accent colour; optional day initials underneath. */
    private fun barsBitmap(context: Context, widthDp: Int, heightDp: Int, d: WidgetSnapshot, p: SpendlyPalette, labels: Boolean): Bitmap {
        val w = px(context, widthDp).toInt().coerceAtLeast(1)
        val h = px(context, heightDp).toInt().coerceAtLeast(1)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelSpace = if (labels) px(context, 16) else 0f
        val chartH = h - labelSpace
        val n = d.week.size
        val gap = px(context, if (widthDp > 160) 8 else 5)
        val barW = (w - gap * (n - 1)) / n
        val maxV = d.week.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val radius = minOf(barW / 2.4f, px(context, 6))
        val minBar = px(context, 3)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px(context, 10)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        for (i in 0 until n) {
            val x = i * (barW + gap)
            // Track (the full possible height) then the bar itself.
            paint.color = p.track.toArgb()
            c.drawRoundRect(RectF(x, 0f, x + barW, chartH), radius, radius, paint)
            val value = d.week[i].coerceAtLeast(0L)
            if (value > 0) {
                // True to scale: a small day stays small (just a sliver, so it's still visible).
                val bh = max(chartH * (value.toFloat() / maxV), minBar)
                val r = minOf(radius, bh / 2f)
                paint.color = (if (i == n - 1) p.accent else p.secondary.copy(alpha = 0.75f)).toArgb()
                c.drawRoundRect(RectF(x, chartH - bh, x + barW, chartH), r, r, paint)
            }
            if (labels) {
                text.color = (if (i == n - 1) p.text else p.textMuted).toArgb()
                c.drawText(d.dayLabel(i).take(1), x + barW / 2f, h - px(context, 3), text)
            }
        }
        return bmp
    }
}
