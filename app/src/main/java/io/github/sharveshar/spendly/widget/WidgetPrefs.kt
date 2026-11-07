package io.github.sharveshar.spendly.widget

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import io.github.sharveshar.spendly.ui.theme.AppTheme
import io.github.sharveshar.spendly.ui.theme.Palettes
import io.github.sharveshar.spendly.ui.theme.SpendlyPalette
import io.github.sharveshar.spendly.ui.theme.ThemeCache

/**
 * Widget settings, kept in plain SharedPreferences because widgets render from a broadcast
 * receiver and must read them synchronously. Only display choices live here, never amounts.
 */
object WidgetPrefs {
    private const val FILE = "spendly_widgets"
    private const val KEY_STYLE = "style"
    private fun accountKey(widgetId: Int) = "account_$widgetId"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The theme widgets use: null = match the app (the default). */
    fun style(context: Context): AppTheme? =
        AppTheme.parse(prefs(context).getString(KEY_STYLE, null))

    fun setStyle(context: Context, theme: AppTheme?) = prefs(context).edit {
        if (theme == null) remove(KEY_STYLE) else putString(KEY_STYLE, theme.name)
    }

    fun palette(context: Context): SpendlyPalette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Palettes.of(style(context) ?: ThemeCache.read(context).resolve(night))
    }

    /** Account shown by one Account widget; null = all accounts (the default). */
    fun account(context: Context, widgetId: Int): Long? = prefs(context).getLong(accountKey(widgetId), 0L).takeIf { it > 0 }

    fun setAccount(context: Context, widgetId: Int, accountId: Long?) = prefs(context).edit {
        if (accountId == null || accountId <= 0) remove(accountKey(widgetId)) else putLong(accountKey(widgetId), accountId)
    }

    fun forget(context: Context, widgetIds: IntArray) = prefs(context).edit { widgetIds.forEach { remove(accountKey(it)) } }

    /** Widgets get new ids when restored (e.g. moved to a new launcher); carry settings over. */
    fun remap(context: Context, oldIds: IntArray, newIds: IntArray) {
        val p = prefs(context)
        p.edit {
            oldIds.zip(newIds).forEach { (old, new) ->
                val account = p.getLong(accountKey(old), 0L)
                remove(accountKey(old))
                if (account > 0) putLong(accountKey(new), account)
            }
        }
    }

    /** Part of Reset Spendly. */
    fun clear(context: Context) = prefs(context).edit { clear() }
}
