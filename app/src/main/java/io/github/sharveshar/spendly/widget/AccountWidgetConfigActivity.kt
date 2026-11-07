package io.github.sharveshar.spendly.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sharveshar.spendly.appContainer
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.SubScreen
import io.github.sharveshar.spendly.ui.components.icon
import io.github.sharveshar.spendly.ui.theme.Palettes
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import io.github.sharveshar.spendly.ui.theme.ThemeCache
import kotlinx.coroutines.launch

/**
 * Chooses what one Account widget shows: all accounts (the default) or a single account. Opened
 * by the launcher when the widget is placed (Android 11 and older) or reconfigured.
 */
class AccountWidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = Palettes.of(ThemeCache.read(this).resolve(night))
        val bars = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Only Spendly's own Account widgets can be configured here.
        val info = AppWidgetManager.getInstance(this)?.getAppWidgetInfo(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider != WidgetUpdater.component(this, WidgetKind.ACCOUNTS)) {
            finish()
            return
        }
        // Backing out keeps the widget, showing all accounts.
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val container = appContainer
        val current = WidgetPrefs.account(this, widgetId)

        setContent {
            SpendlyTheme(palette) {
                val accounts by produceState<List<WidgetAccount>?>(null) {
                    val content = WidgetUpdater.loadContent(container)
                    value = (content as? WidgetRenderer.Content.Ready)?.data?.accounts ?: emptyList()
                }
                SubScreen("Account widget", onBack = ::finish) {
                    Text(
                        "Show the total you have across accounts, or one account's balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Spendly.colors.muted,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { Choice("All accounts", "Available money and card dues", current == null, null) { choose(null) } }
                        items(accounts.orEmpty(), key = { it.id }) { a ->
                            Choice(a.name, a.type.label, current == a.id, a) { choose(a.id) }
                        }
                    }
                }
            }
        }
    }

    private fun choose(accountId: Long?) {
        WidgetPrefs.setAccount(this, widgetId, accountId)
        appContainer.appScope.launch { WidgetUpdater.render(applicationContext, WidgetKind.ACCOUNTS, intArrayOf(widgetId)) }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, account: WidgetAccount?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(account?.type?.icon() ?: Icons.Rounded.AccountBalanceWallet, Spendly.colors.accent)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Spendly.colors.muted)
        }
        RadioButton(selected = selected, onClick = null)
    }
}
