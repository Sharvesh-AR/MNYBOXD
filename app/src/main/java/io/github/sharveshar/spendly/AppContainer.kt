package io.github.sharveshar.spendly

import android.app.Application
import android.content.Context
import io.github.sharveshar.spendly.data.backup.BackupRepository
import io.github.sharveshar.spendly.data.db.DatabaseManager
import io.github.sharveshar.spendly.data.db.DatabaseState
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.prefs.PreferencesRepository
import io.github.sharveshar.spendly.data.repo.AccountRepository
import io.github.sharveshar.spendly.data.repo.BudgetRepository
import io.github.sharveshar.spendly.data.repo.CategoryRepository
import io.github.sharveshar.spendly.data.repo.TransactionRepository
import io.github.sharveshar.spendly.shortcut.ShortcutSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Repositories bound to one open database instance. */
class Repositories(db: SpendlyDatabase) {
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

    @Volatile private var cached: Pair<SpendlyDatabase, Repositories>? = null

    /** Repositories for the currently open database. Only call when state is Ready. */
    val repositories: Repositories
        get() {
            val db = (databaseManager.state.value as? DatabaseState.Ready)?.database
                ?: error("Database is not open")
            cached?.let { if (it.first === db) return it.second }
            return Repositories(db).also { cached = db to it }
        }

    /**
     * Settings → Data management → Reset Spendly. Deletes all financial data (one atomic database
     * transaction that also restores the default categories and Cash wallet), then the extras
     * Spendly created on the phone, and finally every preference, which returns the app to its
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
    }
}

class SpendlyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SpendlyApplication).container
