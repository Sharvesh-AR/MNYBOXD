// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.shortcut.ShortcutSettings
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Google Play Protect's "enhanced fraud protection" blocks installing, from a browser, messaging
 * app or file manager, any app that declares one of four things: RECEIVE_SMS, READ_SMS, a
 * notification listener or an accessibility service. The user sees "App blocked to protect your
 * device". MNYBOXD.apk (the standard build) is installed exactly that way, so it must declare none
 * of them; only the volumeShortcut build may declare its one accessibility service.
 * https://developers.google.com/android/play-protect/warning-dev-guidance
 *
 * Runs against each build's merged manifest (testStandardDebugUnitTest, testVolumeShortcutDebugUnitTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestPolicyTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION") // The int-flags overload is the only one available below API 33.
    private fun packageInfo(): PackageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS,
    )

    private fun servicesGuardedBy(permission: String): List<String> =
        packageInfo().services.orEmpty().filter { it.permission == permission }.map { it.name }

    @Test fun `requests neither SMS permission`() {
        val requested = packageInfo().requestedPermissions.orEmpty().toSet()
        assertEquals(emptySet<String>(), requested intersect setOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    @Test fun `declares no notification listener`() {
        assertEquals(emptyList<String>(), servicesGuardedBy(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE))
    }

    @Test fun `declares an accessibility service only in the volumeShortcut build`() {
        val expected = if (BuildConfig.VOLUME_SHORTCUT) listOf(ShortcutSettings.BUTTON_SERVICE) else emptyList()
        assertEquals(expected, servicesGuardedBy(Manifest.permission.BIND_ACCESSIBILITY_SERVICE))
    }

    @Test fun `requests no permission beyond the microphone`() {
        // Voice entry is the only feature that needs a permission. Anything new here needs a reason
        // in SECURITY.md (library-added ones included, as this reads the merged manifest).
        val requested = packageInfo().requestedPermissions.orEmpty()
            .filterNot { it == "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), requested)
    }
}
