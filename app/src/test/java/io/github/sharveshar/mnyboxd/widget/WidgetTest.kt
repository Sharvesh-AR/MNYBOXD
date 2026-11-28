// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.R
import io.github.sharveshar.mnyboxd.domain.insights.SpendRecord
import io.github.sharveshar.mnyboxd.domain.model.BudgetPeriod
import io.github.sharveshar.mnyboxd.domain.model.TransactionType
import io.github.sharveshar.mnyboxd.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private val records = listOf(
        SpendRecord(TransactionType.EXPENSE, 400_00, 1, null, at(today)),
        SpendRecord(TransactionType.EXPENSE, 1_000_00, 1, null, at(today.minusDays(2))),
        SpendRecord(TransactionType.REFUND, 100_00, 1, null, at(today.minusDays(2))),
        SpendRecord(TransactionType.INCOME, 80_000_00, null, null, at(today.withDayOfMonth(1))),
        SpendRecord(TransactionType.EXPENSE, 7_000_00, 1, null, at(today.withDayOfMonth(1), 9)), // earlier this month, outside the week
        SpendRecord(TransactionType.EXPENSE, 3_000_00, 1, null, at(today.minusMonths(1))), // last month
    )

    private fun snapshot(budget: Long? = 20_000_00) = WidgetSnapshot.build(today, zone, records, budget, hasAnyTransactions = true)

    @Test fun `month, today, week and budget follow the dashboard's rules`() {
        val s = snapshot()
        assertEquals(400_00L + 1_000_00 - 100_00 + 7_000_00, s.spentMonthPaise) // refunds reduce spending
        assertEquals(80_000_00L, s.incomeMonthPaise)
        assertEquals(400_00L, s.spentTodayPaise)
        assertEquals(listOf(0L, 0L, 0L, 0L, 900_00L, 0L, 400_00L), s.week)
        assertEquals(4, s.highestDayIndex)
        assertEquals(20_000_00L - s.spentMonthPaise, s.budgetRemainingPaise)
        assertEquals(41, s.budgetPercent)
        assertEquals("November", s.monthLabel)
    }

    @Test fun `a daily or weekly overall budget is measured over today or this week`() {
        // Sunday 8 November: today ₹400; the week from Monday 2nd also has ₹1,000 less a ₹100 refund.
        val daily = WidgetSnapshot.build(today, zone, records, 500_00, true, BudgetPeriod.DAILY)
        assertEquals(400_00L, daily.budgetSpentPaise)
        assertEquals(100_00L, daily.budgetRemainingPaise)
        assertEquals(80, daily.budgetPercent)
        val weekly = WidgetSnapshot.build(today, zone, records, 1_000_00, true, BudgetPeriod.WEEKLY)
        assertEquals(1_300_00L, weekly.budgetSpentPaise)
        assertEquals(-300_00L, weekly.budgetRemainingPaise)

        val sizes = WidgetRenderer.sizesFor(WidgetKind.MONTHLY)
        val large = texts(inflate(WidgetRenderer.layout(context, WidgetKind.MONTHLY, 13, sizes.first { it.layout == R.layout.widget_monthly }, daily, Palettes.Default)))
        assertTrue(large.toString(), large.contains("₹100 left today"))
        assertTrue(large.toString(), large.contains("of ₹500 a day · 80%"))
        for (bp in sizes) {
            val shown = texts(inflate(WidgetRenderer.layout(context, WidgetKind.MONTHLY, 13, bp, weekly, Palettes.Default)))
            assertTrue(shown.toString(), shown.contains("₹300 over budget this week"))
        }
    }

    @Test fun `empty database gives honest zeros and no highest day`() {
        val s = WidgetSnapshot.build(today, zone, emptyList(), null, hasAnyTransactions = false)
        assertEquals(0L, s.spentMonthPaise)
        assertNull(s.highestDayIndex)
        assertNull(s.budgetRemainingPaise)
        assertFalse(s.hasAnyTransactions)
    }

    private fun inflate(views: android.widget.RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun `every widget layout inflates at every size, in every theme, with data or empty`() {
        val data = listOf(snapshot(), snapshot(budget = null), WidgetSnapshot.build(today, zone, emptyList(), null, false))
        for (kind in WidgetKind.entries) {
            for (bp in WidgetRenderer.sizesFor(kind)) {
                for (palette in Palettes.all) {
                    for (d in data) {
                        inflate(WidgetRenderer.layout(context, kind, 7, bp, d, palette))
                    }
                }
            }
            inflate(WidgetRenderer.message(context, kind, 7, "Open MNYBOXD to get started", Palettes.Default))
        }
    }

    @Test fun `the Add income widget shows its button at every size`() {
        fun shown(kind: WidgetKind) = WidgetRenderer.sizesFor(kind).map { bp ->
            texts(inflate(WidgetRenderer.layout(context, kind, 12, bp, snapshot(), Palettes.Default))).filter { it.isNotBlank() }
        }
        val income = shown(WidgetKind.ADD_INCOME)
        assertEquals(listOf("Income"), income[0])
        assertEquals(listOf("Add income"), income[1])
        assertTrue(income[2].containsAll(listOf("Add income", "+₹80,000 this month")))
    }

    /**
     * What the launcher's widget picker lists: exactly the four widgets, each once, with a picture
     * of itself (light and dark) as the preview. No previewLayout: with one as well as an image,
     * a phone running 2.7.1 listed every widget twice, once with an empty placeholder.
     */
    @Test fun `the widget picker gets four widgets, each with a picture preview and no preview layout`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            PackageManager.GET_META_DATA,
        )
        assertEquals(
            listOf(QuickAddWidget::class, AddIncomeWidget::class, MonthlyWidget::class, TrendWidget::class).map { it.java.name }.sorted(),
            receivers.map { it.activityInfo.name }.sorted(),
        )
        assertEquals(WidgetKind.entries.map { WidgetUpdater.component(context, it).className }.sorted(), receivers.map { it.activityInfo.name }.sorted())
        for (r in receivers) {
            val info = r.activityInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
            assertNotEquals(r.activityInfo.name, 0, info)
            val xml = context.resources.getXml(info)
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
            assertEquals("appwidget-provider", xml.name)
            assertNull(r.activityInfo.name, xml.getAttributeValue(ANDROID, "previewLayout"))
            val preview = xml.getAttributeResourceValue(ANDROID, "previewImage", 0)
            assertEquals(r.activityInfo.name, "drawable", context.resources.getResourceTypeName(preview))
            assertTrue(r.activityInfo.name, context.resources.getResourceEntryName(preview).startsWith("widget_preview_"))
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
