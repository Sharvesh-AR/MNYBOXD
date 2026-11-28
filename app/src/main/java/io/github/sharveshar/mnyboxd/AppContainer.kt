// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd

import android.app.Application
import android.content.Context
import io.github.sharveshar.mnyboxd.data.backup.BackupRepository
import io.github.sharveshar.mnyboxd.data.db.DatabaseManager
import io.github.sharveshar.mnyboxd.data.db.DatabaseState
import io.github.sharveshar.mnyboxd.data.db.MnyboxdDatabase
import io.github.sharveshar.mnyboxd.data.prefs.PreferencesRepository
import io.github.sharveshar.mnyboxd.data.repo.AccountRepository
import io.github.sharveshar.mnyboxd.data.repo.BudgetRepository
import io.github.sharveshar.mnyboxd.data.repo.CategoryRepository
import io.github.sharveshar.mnyboxd.data.repo.TransactionRepository
import io.github.sharveshar.mnyboxd.shortcut.ShortcutSettings
import io.github.sharveshar.mnyboxd.update.AppUpdater
import io.github.sharveshar.mnyboxd.reminders.ExpenseCheckIn
import io.github.sharveshar.mnyboxd.widget.WidgetPrefs
import io.github.sharveshar.mnyboxd.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Repositories bound to one open database instance. */
class Repositories(db: MnyboxdDatabase) {
    val accounts = AccountRepository(db)
    val categories = CategoryRepository(db)
    val transactions = TransactionRepository(db)
    val budgets = BudgetRepository(db)
    val backup = BackupRepository(db)
}

/** Minimal manual dependency container; avoids a DI framework dependency. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val databaseManager = DatabaseManager(context.applicationContext)
    val preferences = PreferencesRepository(context.applicationContext)

    /** Settings → Check for updates. Goes online only when the user asks. */
    val updater by lazy { AppUpdater(appContext, appScope, preferences) }

    @Volatile private var cached: Pair<MnyboxdDatabase, Repositories>? = null

    /** Repositories for the currently open database. Only call when state is Ready. */
    val repositories: Repositories
        get() {
            val db = (databaseManager.state.value as? DatabaseState.Ready)?.database
                ?: error("Database is not open")
            cached?.let { if (it.first === db) return it.second }
            return Repositories(db).also { cached = db to it }
        }

    /**
     * Settings → Data management → Reset MNYBOXD. Deletes all financial data (one atomic database
     * transaction that also restores the default categories and Cash wallet), then the extras
     * MNYBOXD created on the phone, and finally every preference, which returns the app to its
     * first-run screen. Only called after the two-step confirmation.
     */
    suspend fun resetEverything(now: Long = System.currentTimeMillis()) {
        repositories.backup.resetToDefaults(now)
        runCatching { ShortcutSettings.setAppIconOn(appContext, false) }
        onDataReset?.invoke(appContext)
        preferences.clearAll()
    }

    /** Hook for components that keep copies of what's shown (home-screen widgets). */
    var onDataReset: ((Context) -> Unit)? = null

    fun start() {
        appScope.launch { databaseManager.open() }
        // An update downloaded in an earlier run has been installed or abandoned by now.
        appScope.launch(Dispatchers.IO) { runCatching { updater.clearDownloads() } }
        // Expense Check-in: (re)sets the one 7 PM alarm; does nothing if it's switched off.
        appScope.launch { runCatching { ExpenseCheckIn.schedule(appContext) } }
    }
}

class MnyboxdApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onDataReset = { context -> WidgetPrefs.clear(context) }
        container.start()
        // Keep home-screen widgets in step with the data while the app runs (no polling).
        WidgetUpdater.observe(this, container)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MnyboxdApplication).container
