// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.sharveshar.mnyboxd.StableNames

/** System-side state of the optional "Add expense" app-list entry. Read fresh whenever Settings is shown. */
object ShortcutSettings {

    fun isAppIconOn(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(launcherEntry(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Shows or hides the "Add expense" entry in the app list. Off is the manifest default. */
    fun setAppIconOn(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            launcherEntry(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }

    // The alias keeps the name the icon had before the rename, so the user's on/off choice carries over.
    private fun launcherEntry(context: Context) = ComponentName(context, StableNames.ADD_EXPENSE_ICON_ENTRY)
}
