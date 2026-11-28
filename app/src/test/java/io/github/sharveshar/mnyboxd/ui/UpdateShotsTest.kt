// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.AppContainer
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.ui.home.HomeHeader
import io.github.sharveshar.mnyboxd.ui.components.rememberOriginAnchor
import io.github.sharveshar.mnyboxd.ui.navigation.LocalAppContainer
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdPalette
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdTheme
import io.github.sharveshar.mnyboxd.ui.theme.Mnyboxd
import io.github.sharveshar.mnyboxd.ui.theme.Palettes
import io.github.sharveshar.mnyboxd.ui.update.UpdateCard
import io.github.sharveshar.mnyboxd.update.Release
import io.github.sharveshar.mnyboxd.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
 * Visual check of Home's header and the update pop-up in each state, Mono Dark and Mono Light.
 * Opt-in: MNYBOXD_REVIEW_DIR=/some/dir ./gradlew testDebugUnitTest --tests '*UpdateShotsTest*'
 * (Robolectric can't capture dialog windows, so the pop-up's card is drawn in the activity.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class UpdateShotsTest {
    @get:Rule(order = 0) val watchdog = HangWatchdog(3.minutes)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val release = Release(
        version = "2.9.0",
        pageUrl = "https://github.com/Sharvesh-AR/MNYBOXD/releases/tag/v2.9.0",
        apkUrl = "https://github.com/Sharvesh-AR/MNYBOXD/releases/download/v2.9.0/MNYBOXD.apk",
        apkBytes = 10_268_792,
        sumsUrl = null,
        imageUrl = null,
        highlights = listOf(
            "Check for updates: see what's new, download and install without leaving MNYBOXD.",
            "A new header: a two-weight wordmark and one capsule for updates, alerts and you.",
            "Export backup and Import backup, easier to find.",
        ),
    )

    private fun picture(): Bitmap {
        val bmp = Bitmap.createBitmap(1050, 560, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1050f, 560f, 0xFF1C1C20.toInt(), 0xFF3A3A44.toInt(), Shader.TileMode.CLAMP) })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F5F7.toInt(); textSize = 96f; letterSpacing = 0.2f }
        c.drawText("MNYBOXD 2.9", 90f, 310f, text)
        return bmp
    }

    @Test fun captureHeaderAndUpdatePopup() {
        val dir = System.getenv("MNYBOXD_REVIEW_DIR")
        assumeTrue("Set MNYBOXD_REVIEW_DIR to capture the update shots", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        runBlocking(Dispatchers.IO) { withTimeout(10_000) { container.preferences.setUpdateAvailable("9.9.9") } }
        val image = picture()
        val states = listOf(
            "available" to UpdateState(UpdateState.Step.Available, release, image),
            "downloading" to UpdateState(UpdateState.Step.Downloading(3_300_000, 10_268_792), release, image),
            "ready" to UpdateState(UpdateState.Step.Ready(File("x.apk")), release, image),
            "uptodate" to UpdateState(UpdateState.Step.UpToDate),
            "checking" to UpdateState(UpdateState.Step.Checking),
        )
        var palette: MnyboxdPalette by mutableStateOf(Palettes.Mono)
        var state by mutableStateOf(states.first().second)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                MnyboxdTheme(palette) {
                    Column(Modifier.fillMaxWidth().background(Mnyboxd.colors.background).padding(bottom = 24.dp)) {
                        HomeHeader(alertCount = 2, bell = rememberOriginAnchor(), onAlerts = {}, onSettings = {})
                        Box(Modifier.padding(top = 40.dp, start = 20.dp, end = 20.dp).width(353.dp).clip(RoundedCornerShape(28.dp)).background(Mnyboxd.colors.cardElevated)) {
                            UpdateCard(state, needsPermission = state.step is UpdateState.Step.Ready, onDownload = {}, onCancel = {}, onInstall = {}, onRetry = {}, onClose = {})
                        }
                    }
                }
            }
        }
        for (p in listOf(Palettes.Mono, Palettes.Light)) {
            for ((name, s) in states) {
                palette = p
                state = s
                compose.mainClock.advanceTimeBy(2_000)
                compose.waitForIdle()
                compose.onRoot().captureToImage().asAndroidBitmap().savePng(File(out, "u-${p.theme.name.lowercase()}-$name.png"))
            }
        }
    }
}
