// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.AppContainer
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.data.db.MnyboxdDatabase
import io.github.sharveshar.mnyboxd.data.testDatabase
import io.github.sharveshar.mnyboxd.ui.navigation.LocalAppContainer
import io.github.sharveshar.mnyboxd.ui.start.OnboardingTour
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TourTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(2.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    private lateinit var db: MnyboxdDatabase
    private lateinit var container: AppContainer
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        db = testDatabase()
        container = AppContainer(context)
        container.databaseManager.useForTesting(db)
        // DataStore keeps one instance per process, so settings from an earlier test would linger.
        runBlocking(Dispatchers.IO) { withTimeout(5_000) { container.preferences.clearAll() } }
    }

    @After fun tearDown() = db.close()

    private fun saved() = runBlocking(Dispatchers.IO) { withTimeout(5_000) { container.preferences.preferences.first() } }
    private fun waitFor(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    /** Next, after the step before has settled (the tour ignores taps mid-slide). */
    private fun next() {
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Next").performClick()
        compose.mainClock.advanceTimeBy(600)
    }

    @Test fun `Next and Back walk the five steps, Back isn't offered on the first, Get Started finishes`() {
        var done = 0
        compose.setContent { MnyboxdTheme { OnboardingTour(onDone = { done++ }) } }
        waitFor("Your money. Your control.")
        assertTrue(gone("Back"))
        next(); waitFor("Everything at a glance.")
        compose.onNodeWithText("Back").performClick()
        compose.mainClock.advanceTimeBy(600)
        waitFor("Your money. Your control.")
        next(); next(); waitFor("Track it in seconds.")
        next(); waitFor("Stay within your limits.")
        next(); waitFor("Understand your habits.")
        assertTrue(gone("Next"))
        assertEquals(0, done)
        compose.onNodeWithText("Get Started").performClick()
        assertEquals(1, done)
    }

    @Test fun `Skip is there on every step`() {
        var done = 0
        compose.setContent { MnyboxdTheme { OnboardingTour(onDone = { done++ }) } }
        waitFor("Skip")
        next(); next()
        compose.onNodeWithText("Skip").assertIsDisplayed().performClick()
        assertEquals(1, done)
    }

    @Test fun `a new user sees the tour once after setup, Skip goes to Home and it never returns`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) { MnyboxdTheme { MnyboxdApp(container, emptyFlow()) } }
        }
        waitFor("Get started")
        compose.onNodeWithText("Get started").performClick()
        waitFor("Your money. Your control.")
        assertTrue(saved().tourPending)
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(5_000) { !saved().tourPending }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitUntil(5_000) { gone("Your money. Your control.") }
        assertTrue(saved().onboardingDone)
        assertFalse(saved().tourPending)
    }

    @Test fun `someone who already uses MNYBOXD never sees it`() {
        runBlocking(Dispatchers.IO) { withTimeout(5_000) { container.preferences.setOnboardingDone() } }
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) { MnyboxdTheme { MnyboxdApp(container, emptyFlow()) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("MNYBOXD").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(gone("Your money. Your control."))
        assertTrue(gone("Skip"))
    }
}
