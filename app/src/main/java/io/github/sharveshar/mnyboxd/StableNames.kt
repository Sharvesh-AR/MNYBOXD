// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd

/**
 * Names that Android and people's phones already know MNYBOXD by, from before the app was
 * renamed. They must never change:
 *
 * - a different package ID makes Android treat the app as a new one, so updates wouldn't install
 *   over earlier versions;
 * - different file and key names would leave the user's saved data behind;
 * - different entry-point names would drop the home-screen icon, the "Add expense" icon setting
 *   and pinned shortcuts on update (the manifest declares those names as activity aliases).
 *
 * Everything else is called MNYBOXD. StableNamesTest fails if any of these is edited.
 */
object StableNames {
    /** The Android package ID (applicationId in app/build.gradle.kts). */
    const val PACKAGE_ID = "io.github.sharveshar.spendly"

    /** The main launcher entry (an activity alias for [MainActivity]). */
    const val LAUNCHER_ENTRY = "$PACKAGE_ID.MainActivity"

    /** The Quick Add pop-up as launchers and pinned shortcuts know it (an alias for [QuickAddActivity]). */
    const val QUICK_ADD_ENTRY = "$PACKAGE_ID.QuickAddActivity"

    /** The optional "Add expense" app icon (an alias whose enabled state is the user's setting). */
    const val ADD_EXPENSE_ICON_ENTRY = "$PACKAGE_ID.shortcut.QuickAddLauncherActivity"

    /** The encrypted Room database file. */
    const val DATABASE_FILE = "spendly.db"

    /** The wrapped database key file, and the Android Keystore key and associated data that protect it. */
    const val DATABASE_KEY_FILE = "spendly_db.key"
    const val DATABASE_KEY_ALIAS = "spendly_db_wrapping_key_v1"
    const val DATABASE_KEY_AAD = "spendly-db-key-v1"

    /** DataStore holding the settings. */
    const val PREFERENCES_FILE = "spendly_prefs"

    /** SharedPreferences: the first-frame theme mirror, and the widget display choices. */
    const val THEME_CACHE_FILE = "spendly_theme_cache"
    const val WIDGET_PREFS_FILE = "spendly_widgets"

    /** The first eight bytes of backups made before 2.7.1. They still restore. */
    const val OLD_BACKUP_MAGIC = "SPENDLYB"
}
