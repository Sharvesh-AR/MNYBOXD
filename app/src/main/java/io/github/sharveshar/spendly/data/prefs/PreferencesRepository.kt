package io.github.sharveshar.spendly.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.shortcut.ButtonShortcut
import io.github.sharveshar.spendly.shortcut.ShortcutButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Non-sensitive UI preferences only. No financial data or keys are stored here. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val onboardingDone: Boolean = false,
    val voiceDisclosureAccepted: Boolean = false,
    val secureScreen: Boolean = false,
    val lastAccountId: Long? = null,
    val lastChannel: PaymentChannel? = null,
    val lastProvider: PaymentProvider? = null,
    /** Volume-button pattern that opens Quick Add (only acts once the user enables the service). */
    val buttonShortcut: ButtonShortcut = ButtonShortcut(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spendly_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val voiceDisclosure = booleanPreferencesKey("voice_disclosure_accepted")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val lastAccount = longPreferencesKey("last_account_id")
        val lastChannel = stringPreferencesKey("last_channel")
        val lastProvider = stringPreferencesKey("last_provider")
        val shortcutButton = stringPreferencesKey("shortcut_button")
        val shortcutPresses = intPreferencesKey("shortcut_presses")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                onboardingDone = p[Keys.onboarding] ?: false,
                voiceDisclosureAccepted = p[Keys.voiceDisclosure] ?: false,
                secureScreen = p[Keys.secureScreen] ?: false,
                lastAccountId = p[Keys.lastAccount],
                lastChannel = p[Keys.lastChannel]?.let { runCatching { PaymentChannel.valueOf(it) }.getOrNull() },
                lastProvider = p[Keys.lastProvider]?.let { runCatching { PaymentProvider.valueOf(it) }.getOrNull() },
                buttonShortcut = ButtonShortcut(
                    button = p[Keys.shortcutButton]?.let { runCatching { ShortcutButton.valueOf(it) }.getOrNull() } ?: ShortcutButton.VOLUME_DOWN,
                    presses = p[Keys.shortcutPresses]?.takeIf { it in ButtonShortcut.PRESS_CHOICES } ?: ButtonShortcut.DEFAULT_PRESSES,
                ),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setVoiceDisclosureAccepted() = context.dataStore.edit { it[Keys.voiceDisclosure] = true }
    suspend fun setSecureScreen(enabled: Boolean) = context.dataStore.edit { it[Keys.secureScreen] = enabled }
    suspend fun setButtonShortcut(shortcut: ButtonShortcut) = context.dataStore.edit {
        it[Keys.shortcutButton] = shortcut.button.name
        it[Keys.shortcutPresses] = shortcut.presses
    }

    suspend fun rememberPaymentChoice(accountId: Long, channel: PaymentChannel?, provider: PaymentProvider?) =
        context.dataStore.edit {
            it[Keys.lastAccount] = accountId
            if (channel != null) it[Keys.lastChannel] = channel.name else it.remove(Keys.lastChannel)
            if (provider != null) it[Keys.lastProvider] = provider.name else it.remove(Keys.lastProvider)
        }
}
