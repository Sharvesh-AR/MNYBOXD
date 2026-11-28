// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.widget

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import io.github.sharveshar.mnyboxd.ui.theme.AppTheme
import io.github.sharveshar.mnyboxd.ui.theme.Palettes
import io.github.sharveshar.mnyboxd.ui.theme.MnyboxdPalette
import io.github.sharveshar.mnyboxd.ui.theme.ThemeCache
import io.github.sharveshar.mnyboxd.StableNames

/**
 * Widget settings, kept in plain SharedPreferences because widgets render from a broadcast
 * receiver and must read them synchronously. Only display choices live here, never amounts.
 */
object WidgetPrefs {
    private const val FILE = StableNames.WIDGET_PREFS_FILE
    private const val KEY_STYLE = "style"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The theme widgets use: null = match the app (the default). */
    fun style(context: Context): AppTheme? =
        AppTheme.parse(prefs(context).getString(KEY_STYLE, null))

    fun setStyle(context: Context, theme: AppTheme?) = prefs(context).edit {
        if (theme == null) remove(KEY_STYLE) else putString(KEY_STYLE, theme.name)
    }

    fun palette(context: Context): MnyboxdPalette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Palettes.of(style(context) ?: ThemeCache.read(context).resolve(night))
    }

    /** Part of Reset MNYBOXD. */
    fun clear(context: Context) = prefs(context).edit { clear() }
}
