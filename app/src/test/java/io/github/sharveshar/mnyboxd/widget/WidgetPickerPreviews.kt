// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.R
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdPalette
import io.github.sharveshar.mnyboxd.ui.theme.Palettes
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * Draws the pictures the launcher's widget picker shows (each widget's `previewImage`): the real
 * widget layouts with illustrative figures, Mono Light in drawable-nodpi and Mono Dark in
 * drawable-night-nodpi. Opt-in; run after changing a widget's look and commit the PNGs:
 *
 *   MNYBOXD_WIDGET_PREVIEWS=$PWD/app/src/main/res ./gradlew testDebugUnitTest --tests '*WidgetPickerPreviews*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xxhdpi")
class WidgetPickerPreviews {
    @get:Rule val watchdog = HangWatchdog(3.minutes)

    /** The size each widget is placed at by default (4×1 or 4×2), in dp, and its layout there. */
    private class Picture(val kind: WidgetKind, val file: String, val layout: Int, val widthDp: Int, val heightDp: Int)

    private val pictures = listOf(
        Picture(WidgetKind.QUICK_ADD, "widget_preview_quick_add", R.layout.widget_quick_add, 330, 80),
        Picture(WidgetKind.ADD_INCOME, "widget_preview_add_income", R.layout.widget_add_income, 330, 80),
        Picture(WidgetKind.MONTHLY, "widget_preview_monthly", R.layout.widget_monthly, 330, 160),
        Picture(WidgetKind.TREND, "widget_preview_trend", R.layout.widget_trend, 330, 160),
    )

    /** Saturday 21 November: ₹28,921 of a ₹45,000 budget, ₹8,394 over the week, Monday the highest. */
    private val sample = WidgetSnapshot(
        today = LocalDate.of(2026, 11, 21),
        spentMonthPaise = 28_921_00,
        incomeMonthPaise = 80_000_00,
        spentTodayPaise = 345_00,
        budgetPaise = 45_000_00,
        week = listOf(1_250_00, 2_400_00, 640_00, 1_890_00, 980_00, 889_00, 345_00),
        hasAnyTransactions = true,
    )

    @Test fun drawPickerPreviews() {
        val res = System.getenv("MNYBOXD_WIDGET_PREVIEWS")
        assumeTrue("Set MNYBOXD_WIDGET_PREVIEWS to the res directory to redraw the picker previews", !res.isNullOrBlank())
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((folder, palette) in listOf("drawable-nodpi" to Palettes.Light, "drawable-night-nodpi" to Palettes.Mono)) {
            val out = File(res!!, folder).apply { mkdirs() }
            for (p in pictures) draw(context, p, palette).let { bmp ->
                File(out, "${p.file}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun draw(context: Context, p: Picture, palette: MnyboxdPalette): Bitmap {
        val bp = WidgetRenderer.sizesFor(p.kind).single { it.layout == p.layout }
        val view = WidgetRenderer.layout(context, p.kind, PREVIEW_ID, bp, sample, palette).apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val w = (p.widthDp * density).toInt()
        val h = (p.heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        // Transparent outside the widget's rounded corners.
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private companion object {
        const val PREVIEW_ID = 1
    }
}
