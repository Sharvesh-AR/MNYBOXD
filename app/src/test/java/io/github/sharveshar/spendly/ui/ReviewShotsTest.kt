package io.github.sharveshar.spendly.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.DebugTools
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.testDatabase
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Visual QA captures, including frames in the middle of transitions. Opt-in:
 *   SPENDLY_REVIEW_DIR=$PWD/build/review ./gradlew testStandardDebugUnitTest --tests '*ReviewShotsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@OptIn(ExperimentalMaterial3Api::class)
class ReviewShotsTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(3.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun captureReview() {
        val dir = System.getenv("SPENDLY_REVIEW_DIR")
        assumeTrue("Set SPENDLY_REVIEW_DIR to capture review shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = testDatabase()
        val container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking {
            container.preferences.setOnboardingDone()
            DebugTools.loadSampleData(container.repositories)
        }
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container, LocalRippleConfiguration provides null) {
                SpendlyTheme { SpendlyApp(container, emptyFlow()) }
            }
        }
        fun settle() {
            compose.mainClock.advanceTimeBy(2_000)
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            compose.waitForIdle()
        }
        fun save(name: String) {
            compose.captureScreen().savePng(File(out, "$name.png"))
        }
        settle(); save("a-home")

        // Dock: Insights lifted and dragged two slots left, then dropped.
        compose.mainClock.autoAdvance = false
        val dockTab = androidx.compose.ui.test.hasContentDescription("Insights") and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
        compose.onNode(dockTab).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(-1.6f * width / 10, 0f)) }
        }
        compose.mainClock.advanceTimeBy(400); ShadowLooper.idleMainLooper()
        save("h-dock-drag")
        compose.onNode(dockTab).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800); ShadowLooper.idleMainLooper()
        save("h-dock-dropped")
        compose.mainClock.autoAdvance = true
        // Let the drop's own save finish on the main thread before resetting the shared order.
        settle()
        runBlocking(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.withTimeout(5_000) { container.preferences.setDockOrder(io.github.sharveshar.spendly.ui.navigation.DockOrder.DEFAULT) } }
        settle()

        // The + gliding from mid-Home to the corner as the tab changes, frame by frame.
        compose.mainClock.autoAdvance = false
        compose.onNode(tab("Activity")).performClick()
        var glideAt = 0L
        for (step in listOf(60L, 140L, 240L, 420L)) {
            compose.mainClock.advanceTimeBy(step - glideAt); glideAt = step
            ShadowLooper.idleMainLooper()
            save("k-glide-%03d".format(step))
        }
        compose.mainClock.autoAdvance = true
        compose.onNode(tab("Home")).performClick()
        settle()

        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Recent"))
        settle(); save("b-home-recent")

        // Quick Add opening, frame by frame.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add expense").performClick()
        for (t in listOf(48L, 96L, 160L, 240L, 400L)) {
            compose.mainClock.advanceTimeBy(if (t == 48L) 48 else t - listOf(48L, 96L, 160L, 240L, 400L).let { it[it.indexOf(t) - 1] })
            save("c-open-%03d".format(t))
        }
        compose.mainClock.autoAdvance = true
        settle(); save("d-quick-add")

        // Closing, frame by frame.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Close without saving").performClick()
        var prev = 0L
        for (t in listOf(48L, 120L, 240L)) {
            compose.mainClock.advanceTimeBy(t - prev); prev = t
            save("f-close-%03d".format(t))
        }
        compose.mainClock.autoAdvance = true
        settle()

        // Settings footer.
        compose.onNodeWithContentDescription("Settings").performClick()
        settle()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Built with", substring = true))
        settle(); save("g-settings-footer")
        db.close()
    }

    /**
     * Robolectric can't capture dialog windows (the capture returns the activity window), so the
     * Quick Add sheet is rendered here in the activity window itself, over the real app.
     */
    @Test fun captureQuickAddSheet() {
        val dir = System.getenv("SPENDLY_REVIEW_DIR")
        assumeTrue("Set SPENDLY_REVIEW_DIR to capture review shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = testDatabase()
        val container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking {
            container.preferences.setOnboardingDone()
            DebugTools.loadSampleData(container.repositories)
        }
        val vm = io.github.sharveshar.spendly.ui.entry.EntryViewModel(container.repositories, container.preferences, androidx.lifecycle.SavedStateHandle())
        var showSheet by androidx.compose.runtime.mutableStateOf(false)
        var palette by androidx.compose.runtime.mutableStateOf(io.github.sharveshar.spendly.ui.theme.Palettes.Mono)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container, LocalRippleConfiguration provides null) {
                SpendlyTheme(palette) {
                    androidx.compose.foundation.layout.Box {
                        SpendlyApp(container, emptyFlow())
                        if (showSheet) io.github.sharveshar.spendly.ui.entry.EntryScreen(vm, startVoice = false, onClose = { showSheet = false }, onAddAccount = {})
                    }
                }
            }
        }
        fun save(name: String) = compose.captureScreen().savePng(File(out, "$name.png"))
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = false
        showSheet = true
        var t = 0L
        for (step in listOf(32L, 64L, 120L, 200L, 320L)) {
            compose.mainClock.advanceTimeBy(step - t); t = step
            save("s-open-%03d".format(step))
        }
        compose.mainClock.advanceTimeBy(1_500)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        save("s-sheet")
        compose.onNodeWithContentDescription("4").performClick()
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithContentDescription("0").performClick()
        // The sheet's chip, not the dashboard's "Where it went" row under the scrim.
        compose.onAllNodesWithText("Food & Dining").onLast().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        save("s-sheet-filled")
        for (p in io.github.sharveshar.spendly.ui.theme.Palettes.all) {
            palette = p
            // The sheet is its own window: let it recompose and redraw before capturing.
            repeat(8) { compose.mainClock.advanceTimeBy(100); ShadowLooper.idleMainLooper() }
            save("t-sheet-${p.theme.name.lowercase()}")
        }
        palette = io.github.sharveshar.spendly.ui.theme.Palettes.Mono
        compose.mainClock.advanceTimeBy(500)
        // Save, then the money moment frame by frame.
        compose.onAllNodesWithText("Add Expense").onLast().performClick()
        var waited = 0L
        while (compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Expense Added!", substring = true)).fetchSemanticsNodes().isEmpty() && waited < 2_000) {
            compose.mainClock.advanceTimeBy(16); ShadowLooper.idleMainLooper(); waited += 16
        }
        var since = 0L
        for (step in listOf(120L, 280L, 420L, 560L, 720L, 880L)) {
            compose.mainClock.advanceTimeBy(step - since); since = step
            ShadowLooper.idleMainLooper()
            save("u-coin-%03d".format(step))
        }
        db.close()
    }

    @Test fun captureCalendar() {
        val dir = System.getenv("SPENDLY_REVIEW_DIR")
        assumeTrue("Set SPENDLY_REVIEW_DIR to capture review shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val today = java.time.LocalDate.of(2026, 11, 8)
        compose.setContent {
            SpendlyTheme {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier
                            .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    ) {
                        io.github.sharveshar.spendly.ui.components.CalendarPicker(
                            selected = java.time.LocalDate.of(2026, 11, 3),
                            today = today,
                            range = io.github.sharveshar.spendly.core.time.SelectableRange.forTransactions(today),
                            onSelect = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.captureScreen().savePng(File(out, "v-calendar.png"))
    }

    /** Every widget layout at its breakpoint size, real sample data, drawn onto a wallpaper-ish backdrop. */
    @Test fun captureWidgets() {
        val dir = System.getenv("SPENDLY_REVIEW_DIR")
        assumeTrue("Set SPENDLY_REVIEW_DIR to capture review shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = testDatabase()
        val container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking { DebugTools.loadSampleData(container.repositories) }
        val data = runBlocking { io.github.sharveshar.spendly.widget.WidgetSnapshot.load(container.repositories) }
        val density = context.resources.displayMetrics.density
        for (palette in listOf(io.github.sharveshar.spendly.ui.theme.Palettes.Mono, io.github.sharveshar.spendly.ui.theme.Palettes.Light)) {
            for (kind in io.github.sharveshar.spendly.widget.WidgetKind.entries) {
                for (bp in io.github.sharveshar.spendly.widget.WidgetRenderer.sizesFor(kind, context, 3)) {
                    // Render at a typical launcher cell size for this breakpoint.
                    val wDp = if (bp.minWidth >= 200f) 330 else 160
                    val hDp = when {
                        bp.minHeight >= 140f -> 230
                        bp.minHeight >= 90f -> 160
                        else -> 76
                    }
                    val view = io.github.sharveshar.spendly.widget.WidgetRenderer.layout(context, kind, 3, bp, data, palette)
                        .apply(context, android.widget.FrameLayout(context))
                    val w = (wDp * density).toInt(); val h = (hDp * density).toInt()
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, w, h)
                    val pad = (16 * density).toInt()
                    val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.rgb(58, 70, 92))
                    canvas.translate(pad.toFloat(), pad.toFloat())
                    view.draw(canvas)
                    val name = context.resources.getResourceEntryName(bp.layout)
                    bmp.savePng(File(out, "w-${palette.theme.name.lowercase()}-$name.png"))
                }
            }
        }
        db.close()
    }

    /** A tour of every screen, for visual review of the redesign. */
    @Test fun captureTour() {
        val dir = System.getenv("SPENDLY_REVIEW_DIR")
        assumeTrue("Set SPENDLY_REVIEW_DIR to capture review shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = testDatabase()
        val container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking {
            container.preferences.setOnboardingDone()
            DebugTools.loadSampleData(container.repositories)
        }
        var tourPalette by androidx.compose.runtime.mutableStateOf(io.github.sharveshar.spendly.ui.theme.Palettes.Mono)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container, LocalRippleConfiguration provides null) {
                SpendlyTheme(tourPalette) { SpendlyApp(container, emptyFlow()) }
            }
        }
        fun settle() {
            compose.mainClock.advanceTimeBy(2_000)
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            compose.waitForIdle()
        }
        fun shot(name: String) { settle(); compose.captureScreen().savePng(File(out, "x-$name.png")) }
        fun back() { compose.onNodeWithContentDescription("Back").performClick(); settle() }
        shot("01-home")
        compose.onNode(tab("Activity")).performClick(); shot("02-activity")
        compose.onAllNodesWithText("Petrol").onFirst().performClick(); shot("03-tx-detail"); back()
        compose.onNode(tab("Accounts")).performClick(); shot("04-accounts")
        compose.onAllNodesWithText("HDFC Savings").onFirst().performClick(); shot("05-account-detail"); back()
        compose.onNode(tab("Budgets")).performClick(); shot("06-budgets")
        compose.onNode(tab("Insights")).performClick(); shot("07-insights")
        val insightsList = hasScrollAction() and !androidx.compose.ui.test.hasAnyAncestor(hasScrollAction())
        val recapPager = hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(hasText("This week")) and !androidx.compose.ui.test.hasAnyDescendant(hasText("Insights"))
        compose.onNode(recapPager).performTouchInput { swipeLeft() }; shot("07b-month-recap")
        compose.onNode(insightsList).performScrollToNode(androidx.compose.ui.test.hasContentDescription("Spent", substring = true)); shot("07c-categories")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Spent", substring = true)).performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.92f, height * 0.5f)) }
        shot("07d-category-focus")
        compose.onNode(tab("Home")).performClick(); settle()
        compose.onNodeWithContentDescription("Settings").performClick(); shot("08-settings")
        compose.onNodeWithText("Themes").performClick(); shot("09-themes"); back()
        compose.onNodeWithText("Shortcut settings").performClick(); shot("10-shortcuts"); back()
        compose.onNodeWithText("Home-screen widgets").performClick(); shot("11-widgets"); back()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Categories"))
        compose.onNodeWithText("Categories").performClick(); shot("12-categories"); back()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("About MNYBOXD"))
        compose.onNodeWithText("About MNYBOXD").performClick(); shot("13-about"); back()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Built with", substring = true))
        shot("14-settings-bottom")
        back()
        tourPalette = io.github.sharveshar.spendly.ui.theme.Palettes.Light
        compose.onNode(tab("Home")).performClick(); shot("15-home-light")
        compose.onNode(tab("Insights")).performClick()
        compose.onNode(insightsList).performScrollToNode(androidx.compose.ui.test.hasContentDescription("Spent", substring = true)); shot("16-categories-light")
        compose.onNode(tab("Budgets")).performClick(); shot("17-budgets-light")
        db.close()
    }

    /** A tab in the bottom dock (icons only; the name is its description). */
    private fun tab(label: String) = androidx.compose.ui.test.hasContentDescription(label) and
        androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
}
