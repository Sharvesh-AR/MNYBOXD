// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.HangWatchdog
import io.github.sharveshar.mnyboxd.data.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * The updater against the real GitHub, pretending 2.7.0 is installed: it must find the latest
 * release, download MNYBOXD.apk through GitHub's redirects and match SHA256SUMS.txt. Opt-in,
 * because it needs the network: MNYBOXD_NETWORK_TESTS=1 ./gradlew testDebugUnitTest --tests '*AppUpdaterNetworkTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdaterNetworkTest {
    @get:Rule val watchdog = HangWatchdog(4.minutes)

    private fun waitWhile(timeoutMs: Long, busy: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (busy() && System.currentTimeMillis() < end) Thread.sleep(100)
    }

    @Test fun `finds, downloads and checks the latest release`() {
        assumeTrue("Set MNYBOXD_NETWORK_TESTS=1 to run tests that use the network", System.getenv("MNYBOXD_NETWORK_TESTS") == "1")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val updater = AppUpdater(context, scope, PreferencesRepository(context), installedVersion = "2.7.0", installedVersionCode = 13)
            updater.check()
            waitWhile(30_000) { updater.state.value.step == UpdateState.Step.Checking }
            val found = updater.state.value
            assertEquals(found.toString(), UpdateState.Step.Available, found.step)
            val release = found.release!!
            println("Latest: ${release.version}, ${release.apkBytes} bytes, highlights=${release.highlights}, image=${release.imageUrl}")
            assertTrue(Releases.isNewer(release.version, "2.7.0"))
            assertNotNull(release.sumsUrl)
            assertTrue(release.highlights.isNotEmpty())

            updater.download()
            waitWhile(180_000) { updater.state.value.step is UpdateState.Step.Downloading }
            val step = updater.state.value.step
            println("After download: $step")
            // Downloaded in full and matching SHA256SUMS.txt (a mismatch or a broken download has its
            // own message). The final "is it this app" check can't pass here: the test app's package
            // name ends in ".debug", and Robolectric can't run Android's APK signature reader.
            assertTrue(step.toString(), step is UpdateState.Step.Ready || (step is UpdateState.Step.Failed && step.message.startsWith("This file isn't a MNYBOXD update")))
        } finally {
            scope.cancel()
        }
    }
}
