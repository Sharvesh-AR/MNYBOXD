package io.github.sharveshar.spendly.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.testDatabase
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.navigation.LocalAppContainer
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import io.github.sharveshar.spendly.ui.voice.VoiceRecognizer
import io.github.sharveshar.spendly.ui.voice.VoiceState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiTest {
    @get:Rule val compose = createComposeRule()

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

    /** Room emits on background executors that waitForIdle() doesn't track, so wait for content explicitly. */
    private fun shown(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(5_000) { runBlocking { db.transactionDao().getAll().isNotEmpty() } }
        val saved = runBlocking { db.transactionDao().getAll() }.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(12_050L, saved.amountPaise)
        assertEquals("Cash", runBlocking { db.accountDao().getById(saved.accountId)!!.name })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
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
}
