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
