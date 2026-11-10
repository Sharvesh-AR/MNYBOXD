// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.data.db

import android.content.Context
import androidx.room.Room
import io.github.sharveshar.spendly.data.security.DatabaseKeyManager
import io.github.sharveshar.spendly.data.security.DatabaseKeyUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

sealed interface DatabaseState {
    data object Opening : DatabaseState
    data class Ready(val database: SpendlyDatabase) : DatabaseState

    /** The encrypted database exists but cannot be unlocked on this device. */
    data object Locked : DatabaseState

    /** Opening failed for another reason (for example a failed upgrade). Data is left untouched. */
    data class Failed(val code: String) : DatabaseState
}

/**
 * Opens the SQLCipher-encrypted Room database off the main thread and exposes its state.
 * Never deletes data on failure; [eraseEverything] is only reachable from an explicit,
 * typed confirmation in the recovery screen.
 */
class DatabaseManager(private val context: Context) {
    private val keyManager = DatabaseKeyManager(context.noBackupFilesDir)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DatabaseState>(DatabaseState.Opening)
    val state: StateFlow<DatabaseState> = _state.asStateFlow()

    suspend fun open() = mutex.withLock {
        if (_state.value is DatabaseState.Ready) return@withLock
        _state.value = DatabaseState.Opening
        _state.value = withContext(Dispatchers.IO) { openInternal() }
    }

    private fun openInternal(): DatabaseState {
        return try {
            System.loadLibrary("sqlcipher")
            val dbFile = context.getDatabasePath(SpendlyDatabase.FILE_NAME)
            val key = keyManager.getOrCreateKey(databaseExists = dbFile.exists())
            val db = Room.databaseBuilder(context, SpendlyDatabase::class.java, SpendlyDatabase.FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(key, null, true))
                .addMigrations(*SpendlyDatabase.MIGRATIONS)
                .addCallback(SpendlyDatabaseCallback())
                .build()
            try {
                // Force the open now so key, schema and migration problems surface here.
                db.openHelper.writableDatabase
            } catch (t: Throwable) {
                db.close()
                throw t
            }
            DatabaseState.Ready(db)
        } catch (_: DatabaseKeyUnavailableException) {
            DatabaseState.Locked
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            when {
                msg.contains("file is not a database", ignoreCase = true) -> DatabaseState.Locked
                msg.contains("migration", ignoreCase = true) -> DatabaseState.Failed("UPGRADE")
                t is UnsatisfiedLinkError -> DatabaseState.Failed("NATIVE")
                msg.contains("full", ignoreCase = true) -> DatabaseState.Failed("STORAGE")
                else -> DatabaseState.Failed("OPEN")
            }
        }
    }

    /** Test hook: use an already-open (in-memory, unencrypted) database. Never called by the app. */
    @androidx.annotation.VisibleForTesting
    internal fun useForTesting(database: SpendlyDatabase) {
        _state.value = DatabaseState.Ready(database)
    }

    /** Permanently deletes the local database and its key. Caller must have explicit user consent. */
    suspend fun eraseEverything() = mutex.withLock {
        withContext(Dispatchers.IO) {
            (_state.value as? DatabaseState.Ready)?.database?.close()
            context.deleteDatabase(SpendlyDatabase.FILE_NAME)
            keyManager.destroy()
        }
        _state.value = DatabaseState.Opening
        _state.value = withContext(Dispatchers.IO) { openInternal() }
    }
}
