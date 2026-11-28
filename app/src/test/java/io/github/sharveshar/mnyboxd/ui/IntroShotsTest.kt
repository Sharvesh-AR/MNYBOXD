// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.ui.start.BrandIntro
import io.github.sharveshar.mnyboxd.ui.start.BrandIntroAnchor
import io.github.sharveshar.mnyboxd.ui.start.OnboardingTour
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdPalette
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdTheme
import io.github.sharveshar.mnyboxd.ui.theme.Palettes
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * Frames of the opening animation and each tour step, Mono Dark and Mono Light, for visual review.
 * Opt-in: MNYBOXD_REVIEW_DIR=/some/dir ./gradlew testDebugUnitTest --tests '*IntroShotsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class IntroShotsTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(3.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun out(): File {
        val dir = System.getenv("MNYBOXD_REVIEW_DIR")
        assumeTrue("Set MNYBOXD_REVIEW_DIR to capture shots", !dir.isNullOrBlank())
        return File(dir!!).apply { mkdirs() }
    }

    @Test fun captureIntro() {
        val out = out()
        var palette: MnyboxdPalette by mutableStateOf(Palettes.Mono)
        var run by mutableIntStateOf(0)
        // Where Home's header wordmark sits at this size (status bar 0 in tests): 20dp in, 8dp down.
        BrandIntroAnchor.wordmark = Rect(60f, 30f, 60f + 300f, 30f + 84f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MnyboxdTheme(palette) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    key(run) { BrandIntro(onFinished = {}) }
                }
            }
        }
        for (p in listOf(Palettes.Mono, Palettes.Light)) {
            palette = p
            run++
            compose.mainClock.advanceTimeByFrame()
            var t = 0L
            for (frame in listOf(250L, 600L, 1_000L, 1_200L, 1_400L, 1_600L, 1_800L, 2_100L, 2_600L, 2_900L)) {
                compose.mainClock.advanceTimeBy(frame - t)
                t = frame
                compose.onRoot().captureToImage().asAndroidBitmap().savePng(File(out, "i-${p.theme.name.lowercase()}-${"%04d".format(frame)}.png"))
            }
            compose.mainClock.advanceTimeBy(600)
        }
    }

    @Test fun captureTour() {
        val out = out()
        var palette: MnyboxdPalette by mutableStateOf(Palettes.Mono)
        var run by mutableIntStateOf(0)
        compose.setContent { MnyboxdTheme(palette) { key(run) { OnboardingTour(onDone = {}) } } }
        for (p in listOf(Palettes.Mono, Palettes.Light)) {
            palette = p
            run++
            for (step in 0 until 5) {
                compose.mainClock.advanceTimeBy(1_500)
                compose.waitForIdle()
                compose.onRoot().captureToImage().asAndroidBitmap().savePng(File(out, "t-${p.theme.name.lowercase()}-$step.png"))
                if (step < 4) compose.onNodeWithText("Next").performClick()
            }
        }
    }
}
