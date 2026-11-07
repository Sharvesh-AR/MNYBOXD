package io.github.sharveshar.spendly.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.BuildConfig
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.testDatabase
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.shortcut.ButtonShortcut
import io.github.sharveshar.spendly.shortcut.ShortcutButton
import io.github.sharveshar.spendly.shortcut.ShortcutSettings
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import io.github.sharveshar.spendly.ui.voice.VoiceRecognizer
import io.github.sharveshar.spendly.ui.voice.VoiceState
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(2.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    private lateinit var db: SpendlyDatabase
    private lateinit var container: AppContainer
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        db = testDatabase()
        container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        runBlocking { container.preferences.setOnboardingDone() }
    }

    @After fun tearDown() = db.close()

    /**
     * Room emits on background executors that waitForIdle() doesn't track, so wait for content explicitly.
     * [scroll] brings the text into view first; it needs a scrollable ancestor, so pass false for text in an
     * OriginPopup, whose card merges its content (scroll column included) into one node.
     */
    private fun shown(text: String, scroll: Boolean = true) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
    }

    /**
     * Saved preferences, read on an IO thread with a deadline. Never block the main thread on DataStore
     * once UI exists: a write started by a tap finishes on the main thread, so blocking it can deadlock.
     */
    private fun savedPreferences() = runBlocking(Dispatchers.IO) { withTimeout(5_000) { container.preferences.preferences.first() } }

    /** Advances the paused test clock in small steps until [condition] holds. */
    private fun stepUntil(timeoutMs: Long, condition: () -> Boolean) {
        var waited = 0L
        while (!condition()) {
            check(waited < timeoutMs) { "Condition not met within $timeoutMs ms" }
            compose.mainClock.advanceTimeBy(50)
            ShadowLooper.idleMainLooper()
            waited += 50
        }
    }

    private fun openShortcutSettings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        shown("Shortcut settings")
        compose.onNodeWithText("Shortcut settings").performClick()
        compose.waitForIdle()
    }

    private fun launchApp() {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                SpendlyTheme { SpendlyApp(container, emptyFlow()) }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `bottom navigation reaches every tab`() {
        launchApp()
        shown("Spendly")
        compose.onNodeWithText("Activity").performClick()
        shown("Transactions")
        compose.onNodeWithText("Accounts").performClick()
        shown("Net worth")
        compose.onNodeWithText("Budgets").performClick()
        shown("Monthly budget")
        compose.onNodeWithText("Insights").performClick()
        shown("Income vs spending")
        compose.onNodeWithText("Home").performClick()
        shown("Spendly")
    }

    @Test fun `empty states are shown on a fresh install`() {
        launchApp()
        shown("Nothing recorded yet")
        compose.onNodeWithText("Activity").performClick()
        compose.waitForIdle()
        shown("No transactions yet")
        compose.onNodeWithText("Budgets").performClick()
        shown("No category budgets")
    }

    @Test fun `quick add saves an expense with the keypad`() {
        launchApp()
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("1").performClick()
        compose.onNodeWithContentDescription("2").performClick()
        compose.onNodeWithContentDescription("0").performClick()
        compose.onNodeWithContentDescription("Decimal point").performClick()
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithText("Food & Dining").performClick()
        // Step the clock by hand: an auto-advancing clock would play the whole save animation
        // (coin, confirmation, sheet closing) inside a single wait and never show "Saved".
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Save expense").performClick()
        // "Saved" shows only after the transaction is committed (and the coin has landed).
        stepUntil(5_000) { compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = true
        val saved = runBlocking { db.transactionDao().getAll() }.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(12_050L, saved.amountPaise)
        assertEquals("Cash", runBlocking { db.accountDao().getById(saved.accountId)!!.name })
        // Then Quick Add closes by itself and the dashboard is back, with no second sheet.
        compose.waitUntil(5_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }
        shown("Spendly")
    }

    @Test fun `quick add validation blocks empty and categoryless entries`() {
        launchApp()
        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Enter an amount").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("5").performClick()
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Choose a category").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { db.transactionDao().getAll() }.isEmpty())
    }

    @Test fun `every clickable element has an accessible label`() {
        launchApp()
        val labelled = hasContentDescriptionAny() or hasTextAny() or hasAnyDescendantLabel()
        for (tab in listOf("Home", "Activity", "Accounts", "Budgets", "Insights")) {
            compose.onNodeWithText(tab).performClick()
            compose.waitForIdle()
            val unlabeled = compose.onAllNodes(hasClickAction() and !labelled, useUnmergedTree = false).fetchSemanticsNodes()
            assertTrue("Unlabelled clickable nodes on $tab: ${unlabeled.map { it.config }}", unlabeled.isEmpty())
        }
    }

    @Test fun `settings switches the add-expense app icon on and off`() {
        launchApp()
        openShortcutSettings()
        shown("\u201CAdd expense\u201D app icon")

        assertFalse(ShortcutSettings.isAppIconOn(context))
        compose.onNodeWithText("\u201CAdd expense\u201D app icon").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(ShortcutSettings.isAppIconOn(context))
        compose.onNodeWithText("\u201CAdd expense\u201D app icon").performClick()
        compose.waitForIdle()
        assertFalse(ShortcutSettings.isAppIconOn(context))
    }

    @Test fun `the standard build offers no volume-button shortcut`() {
        assumeFalse("volumeShortcut build", BuildConfig.VOLUME_SHORTCUT)
        launchApp()
        openShortcutSettings()
        shown("\u201CAdd expense\u201D app icon")
        assertTrue(compose.onAllNodesWithText("Volume button shortcut").fetchSemanticsNodes().isEmpty())
        assertFalse(ShortcutSettings.isButtonShortcutOn(context))
    }

    @Test fun `settings sets up the volume-button shortcut`() {
        assumeTrue("standard build", BuildConfig.VOLUME_SHORTCUT)
        // DataStore is one instance per JVM: start from the default pattern. Only block on it
        // here, before any UI exists; UI writes resume on the main thread, which runBlocking would hold.
        runBlocking {
            container.preferences.setShortcutButton(ShortcutButton.VOLUME_DOWN)
            container.preferences.setShortcutPresses(ButtonShortcut.DEFAULT_PRESSES)
        }
        launchApp()
        openShortcutSettings()
        shown("Volume button shortcut")

        compose.onNodeWithText("Volume up").performScrollTo().performClick()
        compose.onNodeWithText("Twice").performScrollTo().performClick()

        // Not switched on in Android yet: the row explains, using the saved pattern, and links to settings.
        compose.onNodeWithText("Volume button shortcut").performScrollTo().performClick()
        shown("Add expenses with a volume button", scroll = false)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Press volume up twice", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(ButtonShortcut(ShortcutButton.VOLUME_UP, 2), savedPreferences().buttonShortcut)

        compose.onNodeWithText("Accessibility settings").performClick()
        compose.waitForIdle()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, started?.action)
    }

    @Test fun `voice entry reports a missing speech service instead of failing silently`() {
        // Simulates a device with no speech recognition service (e.g. some de-Googled phones).
        val recognizer = VoiceRecognizer(context, serviceAvailable = { false })
        recognizer.start()
        val state = recognizer.state.value
        assertTrue(state is VoiceState.Failed)
        assertTrue((state as VoiceState.Failed).message.contains("keypad"))
    }

    private fun hasContentDescriptionAny() = SemanticsMatcher("has content description") {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.isNotBlank() } == true
    }

    private fun hasTextAny() = SemanticsMatcher("has text") {
        it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.isNotBlank() } == true
    }

    private fun hasAnyDescendantLabel() = SemanticsMatcher("has labelled descendant") { node ->
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode): Boolean =
            n.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true ||
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true ||
                n.children.any(::walk)
        walk(node)
    }

    @Test fun `reset needs two confirmations and the exact phrase, then starts fresh`() {
        runBlocking { io.github.sharveshar.spendly.DebugTools.loadSampleData(container.repositories) }
        fun txCount() = runBlocking { db.transactionDao().getAll().size }
        val before = txCount()
        assertTrue(before > 0)
        launchApp()
        compose.onNodeWithContentDescription("Settings").performClick()
        shown("Reset Spendly")

        // Step 1, cancelled: nothing happens.
        compose.onNodeWithText("Reset Spendly").performClick()
        shown("Reset Spendly?", scroll = false)
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(before, txCount())

        // Step 1, continued: the separate confirmation screen.
        compose.onNodeWithText("Reset Spendly").performScrollTo().performClick()
        shown("Reset Spendly?", scroll = false)
        compose.onNodeWithText("Continue").performClick()
        shown("This will permanently delete all your Spendly data.")
        val delete = compose.onNodeWithText("Delete everything permanently")
        delete.assertIsNotEnabled()

        // Anything but the exact phrase keeps the button disabled and deletes nothing.
        val field = compose.onNode(hasSetTextAction())
        for (wrong in listOf("delete everything", "DELETE  EVERYTHING", "DELETE EVERYTHIN", "DELETE EVERYTHING ")) {
            field.performTextReplacement(wrong)
            compose.waitForIdle()
            delete.assertIsNotEnabled()
        }
        delete.performClick()
        compose.waitForIdle()
        assertEquals(before, txCount())

        // The exact phrase enables it; deleting returns to first run with only the defaults.
        field.performTextReplacement("DELETE EVERYTHING")
        compose.waitForIdle()
        delete.assertIsEnabled()
        delete.performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, txCount())
        assertEquals(listOf("Cash"), runBlocking { db.accountDao().getAll() }.map { it.name })
        assertFalse(savedPreferences().onboardingDone)
    }

    @Test fun `deleting a transaction offers undo, and undo brings it back`() {
        runBlocking {
            val cash = db.accountDao().getAll().single().id
            val food = db.categoryDao().getAll().first { it.key == "food" }.id
            container.repositories.transactions.save(
                io.github.sharveshar.spendly.domain.ledger.TransactionDraft(TransactionType.EXPENSE, 250_00, cash, null, food, null, null, null, "Lunch", System.currentTimeMillis()),
                "undo-test", null,
            )
        }
        launchApp()
        compose.onNodeWithText("Activity").performClick()
        shown("Lunch")
        compose.onNodeWithText("Lunch").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Delete transaction").performClick()
        shown("Delete this transaction?", scroll = false)
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.waitUntil(5_000) { runBlocking { db.transactionDao().getAll().isEmpty() } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { db.transactionDao().getAll().size == 1 } }
        assertEquals(250_00L, runBlocking { db.transactionDao().getAll().single().amountPaise })
    }
}
