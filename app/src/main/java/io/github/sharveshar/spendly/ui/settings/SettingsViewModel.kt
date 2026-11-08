package io.github.sharveshar.spendly.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.DebugTools
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.backup.BackupCodec
import io.github.sharveshar.spendly.data.backup.BackupFormatException
import io.github.sharveshar.spendly.data.backup.BackupPasswordException
import io.github.sharveshar.spendly.data.backup.BackupValidation
import io.github.sharveshar.spendly.data.backup.BackupValidator
import io.github.sharveshar.spendly.data.prefs.PreferencesRepository
import io.github.sharveshar.spendly.data.prefs.UserPreferences
import io.github.sharveshar.spendly.domain.insights.Insights
import io.github.sharveshar.spendly.domain.insights.TimeRange
import io.github.sharveshar.spendly.shortcut.ShortcutButton
import io.github.sharveshar.spendly.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStreamWriter

sealed interface BackupFlow {
    data object Idle : BackupFlow
    data class Working(val label: String) : BackupFlow
    /** A decrypted, fully validated backup awaiting the user's explicit confirmation. */
    data class ConfirmRestore(val validation: BackupValidation.Valid) : BackupFlow
}

class SettingsViewModel(
    private val repos: Repositories,
    private val prefs: PreferencesRepository,
    private val resolver: ContentResolver,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _flow = MutableStateFlow<BackupFlow>(BackupFlow.Idle)
    val flow: StateFlow<BackupFlow> = _flow.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Password held only between the password prompt and the file picker result. */
    private var pendingPassword: CharArray? = null

    val debugToolsAvailable = DebugTools.available

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { prefs.setTheme(choice) }

    /** This month's real spending, shown in the theme previews (null until loaded). */
    val monthSpend: StateFlow<Long?> = repos.transactions
        .observeSpendRecords(TimeRange.month(YearMonth.now(), ZoneId.systemDefault()))
        .map { Insights.netSpend(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setSecureScreen(enabled: Boolean) = viewModelScope.launch { prefs.setSecureScreen(enabled) }
    fun setShortcutButton(button: ShortcutButton) = viewModelScope.launch { prefs.setShortcutButton(button) }
    fun setShortcutPresses(presses: Int) = viewModelScope.launch { prefs.setShortcutPresses(presses) }

    /** Asks the running volume-button service to switch itself off (it calls disableSelf). */
    fun turnOffButtonShortcut() = viewModelScope.launch { prefs.setShortcutActive(false) }
    fun consumeMessage() { _message.value = null }

    fun holdPassword(password: CharArray) {
        clearPassword()
        pendingPassword = password
    }

    fun clearPassword() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    fun exportTo(uri: Uri) {
        val password = pendingPassword ?: run { _message.value = "Export cancelled"; return }
        pendingPassword = null
        _flow.value = BackupFlow.Working("Encrypting backup…")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { BackupCodec.encrypt(repos.backup.snapshot(), password) }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: throw IOException("Could not open file")
                }
                _message.value = "Encrypted backup saved. Keep the password safe: without it the backup can't be opened."
            } catch (e: IOException) {
                _message.value = "Couldn't write the backup. Check there's enough free space and try again."
            } catch (e: Exception) {
                _message.value = "Backup failed. Nothing was changed."
            } finally {
                password.fill('\u0000')
                _flow.value = BackupFlow.Idle
            }
        }
    }

    fun readBackup(uri: Uri) {
        val password = pendingPassword ?: run { _message.value = "Restore cancelled"; return }
        pendingPassword = null
        _flow.value = BackupFlow.Working("Checking backup…")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { BackupCodec.readBounded(it) } ?: throw IOException("Could not open file")
                }
                val snapshot = withContext(Dispatchers.Default) { BackupCodec.decrypt(bytes, password) }
                when (val v = BackupValidator.validate(snapshot)) {
                    is BackupValidation.Valid -> _flow.value = BackupFlow.ConfirmRestore(v)
                    is BackupValidation.Invalid -> {
                        _flow.value = BackupFlow.Idle
                        _message.value = "This backup failed its integrity checks and wasn't restored: ${v.problems.first()}"
                    }
                }
            } catch (e: BackupPasswordException) {
                _flow.value = BackupFlow.Idle
                _message.value = "Wrong password, or the file has been changed. Nothing was restored."
            } catch (e: BackupFormatException) {
                _flow.value = BackupFlow.Idle
                _message.value = e.message ?: "That file isn't a MNYBOXD backup."
            } catch (e: Exception) {
                _flow.value = BackupFlow.Idle
                _message.value = "Couldn't read that file. Nothing was restored."
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun confirmRestore() {
        val pending = _flow.value as? BackupFlow.ConfirmRestore ?: return
        _flow.value = BackupFlow.Working("Restoring…")
        viewModelScope.launch {
            try {
                repos.backup.restore(pending.validation)
                _message.value = "Backup restored."
            } catch (e: Exception) {
                _message.value = "Restore failed and was rolled back. Your existing data is unchanged."
            } finally {
                _flow.value = BackupFlow.Idle
            }
        }
    }

    fun cancelRestore() { _flow.value = BackupFlow.Idle }

    fun exportCsv(uri: Uri) {
        _flow.value = BackupFlow.Working("Exporting CSV…")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open file")
                    OutputStreamWriter(out, Charsets.UTF_8).use { w -> repos.backup.exportCsv(w) }
                }
                _message.value = "CSV exported. This file is not encrypted; store it carefully."
            } catch (e: Exception) {
                _message.value = "CSV export failed. Check free space and try again."
            } finally {
                _flow.value = BackupFlow.Idle
            }
        }
    }

    fun loadSampleData() {
        viewModelScope.launch {
            _message.value = DebugTools.loadSampleData(repos)
        }
    }

    override fun onCleared() {
        clearPassword()
    }
}
