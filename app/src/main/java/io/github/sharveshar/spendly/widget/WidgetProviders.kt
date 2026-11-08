package io.github.sharveshar.spendly.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.appContainer
import io.github.sharveshar.spendly.data.db.DatabaseState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Shared behaviour of Spendly's widget providers. Rendering runs off the main thread in the
 * app's scope while the broadcast is kept alive with goAsync().
 */
abstract class SpendlyWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, ids)

    /** Resizing (Android 11 and older pick the layout here; 12+ already has every size). */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context, intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) = WidgetPrefs.forget(context, ids)

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) = WidgetPrefs.remap(context, oldIds, newIds)

    override fun onDisabled(context: Context) = WidgetUpdater.scheduleMidnight(context)

    private fun refresh(context: Context, ids: IntArray) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                WidgetUpdater.render(context, kind, ids)
            } finally {
                pending.finish()
            }
        }
    }
}

class QuickAddWidget : SpendlyWidgetProvider(WidgetKind.QUICK_ADD)
class MonthlyWidget : SpendlyWidgetProvider(WidgetKind.MONTHLY)
class AccountsWidget : SpendlyWidgetProvider(WidgetKind.ACCOUNTS)
class TrendWidget : SpendlyWidgetProvider(WidgetKind.TREND)
class StatsWidget : SpendlyWidgetProvider(WidgetKind.STATS)

/** Fires shortly after midnight so "today" and "this month" roll over. Not exported. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                WidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object WidgetUpdater {
    private fun providerClass(kind: WidgetKind): Class<out AppWidgetProvider> = when (kind) {
        WidgetKind.QUICK_ADD -> QuickAddWidget::class.java
        WidgetKind.MONTHLY -> MonthlyWidget::class.java
        WidgetKind.ACCOUNTS -> AccountsWidget::class.java
        WidgetKind.TREND -> TrendWidget::class.java
        WidgetKind.STATS -> StatsWidget::class.java
    }

    fun component(context: Context, kind: WidgetKind) = ComponentName(context, providerClass(kind))

    fun ids(context: Context, kind: WidgetKind): IntArray =
        runCatching { AppWidgetManager.getInstance(context)?.getAppWidgetIds(component(context, kind)) }.getOrNull() ?: IntArray(0)

    fun hasWidgets(context: Context) = WidgetKind.entries.any { ids(context, it).isNotEmpty() }

    /** What widgets can show right now: data, or why not (first run, locked database). */
    suspend fun loadContent(container: AppContainer): WidgetRenderer.Content {
        val state = withTimeoutOrNull(8_000) { container.databaseManager.state.first { it !is DatabaseState.Opening } }
        if (state !is DatabaseState.Ready) return WidgetRenderer.Content.Message("Open MNYBOXD to see your spending")
        val prefs = container.preferences.preferences.first()
        if (!prefs.onboardingDone) return WidgetRenderer.Content.Message("Open MNYBOXD to get started")
        return WidgetRenderer.Content.Ready(WidgetSnapshot.load(container.repositories))
    }

    suspend fun render(context: Context, kind: WidgetKind, ids: IntArray) {
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context) ?: return
        val content = loadContent(context.appContainer)
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
            manager.updateAppWidget(id, WidgetRenderer.build(context, kind, id, content, width, height))
        }
        scheduleMidnight(context)
    }

    suspend fun updateAll(context: Context) {
        for (kind in WidgetKind.entries) render(context, kind, ids(context, kind))
        scheduleMidnight(context)
    }

    /** Fire-and-forget refresh, e.g. after the widget style changes. */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        app.appContainer.appScope.launch { if (hasWidgets(app)) updateAll(app) }
    }

    /**
     * Keeps placed widgets in step with the data while the app is running: Room re-emits balances
     * and budgets whenever a transaction, account or budget changes. No polling; nothing runs
     * when there are no widgets or the app isn't running.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observe(context: Context, container: AppContainer) {
        val app = context.applicationContext
        container.appScope.launch {
            container.databaseManager.state
                .flatMapLatest { state ->
                    if (state !is DatabaseState.Ready) emptyFlow() else {
                        val repos = container.repositories
                        combine(
                            repos.accounts.observeWithBalances(),
                            repos.budgets.observeAll(),
                            repos.transactions.observeCount(),
                            container.preferences.preferences.map { it.theme to it.onboardingDone },
                        ) { a, b, c, t -> listOf(a, b, c, t) }
                    }
                }
                .debounce(500)
                .collect { if (hasWidgets(app)) updateAll(app) }
        }
    }

    /** One inexact alarm a minute after midnight while any widget exists (no special permission). */
    fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!hasWidgets(context)) {
            alarms.cancel(intent)
            return
        }
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant().toEpochMilli()
        alarms.set(AlarmManager.RTC, next, intent)
    }
}
