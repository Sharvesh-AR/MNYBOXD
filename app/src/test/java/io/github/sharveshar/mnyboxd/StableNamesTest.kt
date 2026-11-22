// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.mnyboxd.shortcut.QuickAddLauncherActivity
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The names an installed copy of the app already uses. If one changes, an update loses data or
 * stops installing over the old version, so these literals are deliberately repeated here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StableNamesTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `names from before the rename are unchanged`() {
        assertEquals("io.github.sharveshar.spendly", StableNames.PACKAGE_ID)
        assertEquals("io.github.sharveshar.spendly.MainActivity", StableNames.LAUNCHER_ENTRY)
        assertEquals("io.github.sharveshar.spendly.QuickAddActivity", StableNames.QUICK_ADD_ENTRY)
        assertEquals("io.github.sharveshar.spendly.shortcut.QuickAddLauncherActivity", StableNames.ADD_EXPENSE_ICON_ENTRY)
        assertEquals("spendly.db", StableNames.DATABASE_FILE)
        assertEquals("spendly_db.key", StableNames.DATABASE_KEY_FILE)
        assertEquals("spendly_db_wrapping_key_v1", StableNames.DATABASE_KEY_ALIAS)
        assertEquals("spendly-db-key-v1", StableNames.DATABASE_KEY_AAD)
        assertEquals("spendly_prefs", StableNames.PREFERENCES_FILE)
        assertEquals("spendly_theme_cache", StableNames.THEME_CACHE_FILE)
        assertEquals("spendly_widgets", StableNames.WIDGET_PREFS_FILE)
        assertEquals("SPENDLYB", StableNames.OLD_BACKUP_MAGIC)
    }

    @Test fun `the build's package ID is the stable one`() {
        assertEquals(StableNames.PACKAGE_ID, BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
        assertEquals(StableNames.PACKAGE_ID, context.packageName.removeSuffix(".debug"))
    }

    @Test fun `launcher actions are named after the package ID`() {
        for (action in listOf(MainActivity.ACTION_QUICK_ADD, MainActivity.ACTION_VOICE_ADD, MainActivity.ACTION_ADD_INCOME, MainActivity.ACTION_OPEN_ACCOUNT)) {
            assertEquals(true, action.startsWith("${StableNames.PACKAGE_ID}.action."))
        }
    }

    @Test fun `the app-list entries keep their old names and open the renamed activities`() {
        @Suppress("DEPRECATION") // The int-flags overload is the only one available below API 33.
        val entries = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        ).associate { it.activityInfo.name to it.activityInfo.targetActivity }
        assertEquals(
            mapOf(
                StableNames.LAUNCHER_ENTRY to MainActivity::class.java.name,
                StableNames.ADD_EXPENSE_ICON_ENTRY to QuickAddLauncherActivity::class.java.name,
            ),
            entries,
        )
    }

    @Test fun `pinned Quick Add shortcuts still reach Quick Add`() {
        @Suppress("DEPRECATION")
        val alias = context.packageManager.getActivityInfo(ComponentName(context, StableNames.QUICK_ADD_ENTRY), 0)
        assertEquals(QuickAddActivity::class.java.name, alias.targetActivity)
        assertEquals(true, alias.exported)
    }

    @Test fun `the Add expense icon is off until the user switches it on`() {
        @Suppress("DEPRECATION")
        val alias = context.packageManager.getActivityInfo(
            ComponentName(context, StableNames.ADD_EXPENSE_ICON_ENTRY),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertFalse(alias.enabled)
        assertFalse(io.github.sharveshar.mnyboxd.shortcut.ShortcutSettings.isAppIconOn(context))
    }
}
