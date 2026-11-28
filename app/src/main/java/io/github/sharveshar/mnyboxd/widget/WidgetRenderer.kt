// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.widget

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
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import io.github.sharveshar.mnyboxd.MainActivity
import io.github.sharveshar.mnyboxd.QuickAddActivity
import io.github.sharveshar.mnyboxd.R
import io.github.sharveshar.mnyboxd.core.money.Money
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdPalette
import kotlin.math.max

/**
 * The home-screen widgets. [id] is stable: it's part of PendingIntent request codes. Ids 3, 5
 * and 7 belonged to the Account balance, Quick stats and Expense + Income widgets (removed in
 * 2.8); don't reuse them.
 */
enum class WidgetKind(val id: Int) { QUICK_ADD(1), MONTHLY(2), TREND(4), ADD_INCOME(6) }

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
        val sizes = sizesFor(kind)
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
        val sizes = sizesFor(kind)
        // The biggest layout that fits the reported size.
        val fit = sizes.filter { widthDp >= it.minWidth && heightDp >= it.minHeight }.maxByOrNull { it.minWidth * it.minHeight } ?: sizes.first()
        return layout(context, kind, widgetId, fit, (content as Content.Ready).data, p)
    }

    /** One responsive breakpoint: the layout used from this size (dp) upwards. */
    internal data class Breakpoint(val minWidth: Float, val minHeight: Float, @LayoutRes val layout: Int, val chartWidthDp: Int = 0, val chartHeightDp: Int = 0)

    @androidx.annotation.VisibleForTesting
    internal fun sizesFor(kind: WidgetKind): List<Breakpoint> = when (kind) {
        WidgetKind.QUICK_ADD -> listOf(
            Breakpoint(100f, 40f, R.layout.widget_quick_add_small),
            Breakpoint(200f, 40f, R.layout.widget_quick_add),
            Breakpoint(200f, 110f, R.layout.widget_quick_add_large),
        )
        WidgetKind.MONTHLY -> listOf(
            Breakpoint(100f, 100f, R.layout.widget_monthly_small),
            Breakpoint(220f, 100f, R.layout.widget_monthly, chartWidthDp = 104, chartHeightDp = 70),
        )
        WidgetKind.TREND -> listOf(
            Breakpoint(100f, 100f, R.layout.widget_trend_small, chartWidthDp = 130, chartHeightDp = 60),
            Breakpoint(220f, 100f, R.layout.widget_trend, chartWidthDp = 260, chartHeightDp = 80),
        )
        WidgetKind.ADD_INCOME -> listOf(
            Breakpoint(100f, 40f, R.layout.widget_add_income_small),
            Breakpoint(200f, 40f, R.layout.widget_add_income),
            Breakpoint(200f, 110f, R.layout.widget_add_income_large),
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun layout(context: Context, kind: WidgetKind, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: MnyboxdPalette): RemoteViews {
        val v = RemoteViews(context.packageName, bp.layout)
        v.tint(R.id.bg, p.surface)
        when (kind) {
            WidgetKind.QUICK_ADD -> quickAdd(context, v, widgetId, bp, d, p)
            WidgetKind.MONTHLY -> monthly(context, v, widgetId, bp, d, p)
            WidgetKind.TREND -> trend(context, v, widgetId, bp, d, p)
            WidgetKind.ADD_INCOME -> addIncome(context, v, widgetId, bp, d, p)
        }
        return v
    }

    // --- Widgets ---------------------------------------------------------------------------

    private fun quickAdd(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: MnyboxdPalette) {
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
    private fun addIncome(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: MnyboxdPalette) {
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

    private fun logoTile(context: Context, v: RemoteViews, widgetId: Int, kind: WidgetKind, p: MnyboxdPalette) {
        v.tint(R.id.logo_bg, p.surfaceElevated)
        v.tint(R.id.logo, p.accent)
        v.setOnClickPendingIntent(R.id.logo_tile, openApp(context, widgetId, kind, MainActivity.ACTION_OPEN_HOME))
        v.setContentDescription(R.id.logo_tile, "Open MNYBOXD")
    }

    private fun monthly(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: MnyboxdPalette) {
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
        val large = bp.layout == R.layout.widget_monthly
        // A daily or weekly budget always says which stretch it means; a monthly one on the large layout.
        val now = when (d.budgetPeriod) {
            BudgetPeriod.DAILY -> " today"
            BudgetPeriod.WEEKLY -> " this week"
            BudgetPeriod.MONTHLY -> if (large) " this month" else ""
        }
        val caption = when {
            remaining == null -> "No budget set"
            remaining >= 0 -> "${Money.format(remaining)} left$now"
            else -> "${Money.format(-remaining)} over budget" + if (d.budgetPeriod == BudgetPeriod.MONTHLY) "" else now
        }
        v.text(R.id.caption, caption, if (remaining != null && remaining < 0) p.negative else p.textMuted)
        if (large) {
            val per = if (d.budgetPeriod == BudgetPeriod.MONTHLY) "" else " a ${d.budgetPeriod.unit}"
            v.text(R.id.detail, d.budgetPaise?.let { "of ${Money.format(it)}$per · ${d.budgetPercent}%" } ?: "${Money.format(d.incomeMonthPaise)} income", p.textMuted)
            v.setTextColor(R.id.chart_label, p.textMuted.toArgb())
            v.setImageViewBitmap(R.id.chart, barsBitmap(context, bp.chartWidthDp, bp.chartHeightDp, d, p, labels = false))
            v.setContentDescription(R.id.chart, weekDescription(d))
        }
    }

    private fun trend(context: Context, v: RemoteViews, widgetId: Int, bp: Breakpoint, d: WidgetSnapshot, p: MnyboxdPalette) {
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

    @androidx.annotation.VisibleForTesting
    internal fun message(context: Context, kind: WidgetKind, widgetId: Int, text: String, p: MnyboxdPalette): RemoteViews {
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

    private fun weekDescription(d: WidgetSnapshot): String =
        "Spending over the last 7 days: " + d.week.indices.joinToString(", ") { "${d.dayLabel(it)} ${Money.format(d.week[it])}" }

    /**
     * Unique, stable request codes so each widget's buttons keep their own PendingIntent.
     * Slots: 1 add, 2 voice, 3 + kind id (4..9) open app, 30 add income.
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
    private fun barsBitmap(context: Context, widthDp: Int, heightDp: Int, d: WidgetSnapshot, p: MnyboxdPalette, labels: Boolean): Bitmap {
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
