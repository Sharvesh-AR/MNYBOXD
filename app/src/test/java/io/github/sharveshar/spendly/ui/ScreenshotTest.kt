package io.github.sharveshar.spendly.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.DebugTools
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.testDatabase
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import kotlin.time.Duration.Companion.minutes
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
import java.util.concurrent.TimeUnit
import java.io.File

/**
 * Renders real screens with fictional debug sample data for visual review and the README.
 * Opt-in: runs only when SPENDLY_SCREENSHOT_DIR is set, e.g.
 *   SPENDLY_SCREENSHOT_DIR=$PWD/docs/screenshots ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@OptIn(ExperimentalMaterial3Api::class)
class ScreenshotTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(2.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun captureScreens() {
        val dir = System.getenv("SPENDLY_SCREENSHOT_DIR")
        assumeTrue("Set SPENDLY_SCREENSHOT_DIR to capture screenshots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = testDatabase()
        val container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking {
            container.preferences.setOnboardingDone()
            DebugTools.loadSampleData(container.repositories)
        }
        var dark by mutableStateOf(true)
        // Robolectric captures only the activity window, and Quick Add is a dialog window, so the
        // real Quick Add screen is shown in the activity window for its screenshot.
        var showSheet by mutableStateOf(false)
        val entry = io.github.sharveshar.spendly.ui.entry.EntryViewModel(container.repositories, container.preferences, androidx.lifecycle.SavedStateHandle())
        compose.setContent {
            // Robolectric can't run framework ripple animations to completion; disable them for clean captures.
            CompositionLocalProvider(LocalAppContainer provides container, LocalRippleConfiguration provides null) {
                SpendlyTheme(dark = dark) {
                    androidx.compose.foundation.layout.Box {
                        SpendlyApp(container, emptyFlow())
                        if (showSheet) io.github.sharveshar.spendly.ui.entry.EntryScreen(entry, startVoice = false, onClose = { showSheet = false }, onAddAccount = {})
                    }
                }
            }
        }
        fun shot(name: String) {
            compose.mainClock.advanceTimeBy(2_000)
            // Framework ripples run on the Android looper, not the Compose clock; let them finish.
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            compose.waitForIdle()
            compose.captureScreen().savePng(File(out, "$name.png"))
        }
        shot("01-home")
        compose.onNode(tab("Activity")).performClick(); shot("02-activity")
        compose.onNode(tab("Accounts")).performClick(); shot("03-accounts")
        compose.onNode(tab("Budgets")).performClick(); shot("04-budgets")
        compose.onNode(tab("Insights")).performClick(); shot("05-insights")
        compose.onNode(tab("Home")).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        showSheet = true
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithContentDescription("4").performClick()
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithContentDescription("0").performClick()
        // The sheet's chip, not the dashboard's "Where it went" row under it.
        compose.onAllNodesWithText("Food & Dining").onLast().performClick()
        shot("06-quick-add")
        showSheet = false
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(tab("Activity")).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithText("Petrol").onFirst().performClick()
        shot("07-transaction-detail")
        dark = false
        shot("08-detail-light")
        db.close()
    }

    /** A tab in the bottom dock (icons only; the name is its description). */
    private fun tab(label: String) = androidx.compose.ui.test.hasContentDescription(label) and
        androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)
}
