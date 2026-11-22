// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.mnyboxd.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.sharveshar.mnyboxd.AppTab
import io.github.sharveshar.mnyboxd.domain.model.PaymentChannel
import io.github.sharveshar.mnyboxd.domain.model.PaymentProvider
import io.github.sharveshar.mnyboxd.ui.navigation.DockOrder
import io.github.sharveshar.mnyboxd.ui.theme.ThemeCache
import io.github.sharveshar.mnyboxd.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import io.github.sharveshar.mnyboxd.StableNames

/** Pre-2.0 theme setting, read only to migrate it to [ThemeChoice]. */
private enum class LegacyThemeMode { SYSTEM, DARK, LIGHT }

/** Non-sensitive UI preferences only. No financial data or keys are stored here. */
data class UserPreferences(
    val theme: ThemeChoice = ThemeChoice.DEFAULT,
    val onboardingDone: Boolean = false,
    val voiceDisclosureAccepted: Boolean = false,
    val secureScreen: Boolean = false,
    val lastAccountId: Long? = null,
    val lastChannel: PaymentChannel? = null,
    val lastProvider: PaymentProvider? = null,
    /** The bottom dock's tabs, in the order the user dragged them into. */
    val dockOrder: List<AppTab> = DockOrder.DEFAULT,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = StableNames.PREFERENCES_FILE)

/** Dark → the default (Mono), Light → Light, System stays System. Missing → the default. */
internal fun legacyTheme(name: String?): ThemeChoice =
    when (name?.let { n -> LegacyThemeMode.entries.firstOrNull { it.name == n } }) {
        LegacyThemeMode.LIGHT -> ThemeChoice.LIGHT
        LegacyThemeMode.SYSTEM -> ThemeChoice.SYSTEM
        LegacyThemeMode.DARK, null -> ThemeChoice.DEFAULT
    }

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val legacyTheme = stringPreferencesKey("theme_mode")
        // 2.2 replaced MNYBOXD's design: everyone starts on the new look (Mono) once, so themes
        // picked before then are read from the old key no longer. Pick a theme again to keep it.
        val theme = stringPreferencesKey("app_theme_v3")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val voiceDisclosure = booleanPreferencesKey("voice_disclosure_accepted")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val lastAccount = longPreferencesKey("last_account_id")
        val lastChannel = stringPreferencesKey("last_channel")
        val lastProvider = stringPreferencesKey("last_provider")
        val dockOrder = stringPreferencesKey("dock_order")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                theme = ThemeChoice.parse(p[Keys.theme]) ?: legacyTheme(p[Keys.legacyTheme]),
                onboardingDone = p[Keys.onboarding] ?: false,
                voiceDisclosureAccepted = p[Keys.voiceDisclosure] ?: false,
                secureScreen = p[Keys.secureScreen] ?: false,
                lastAccountId = p[Keys.lastAccount],
                lastChannel = p[Keys.lastChannel]?.let { runCatching { PaymentChannel.valueOf(it) }.getOrNull() },
                lastProvider = p[Keys.lastProvider]?.let { runCatching { PaymentProvider.valueOf(it) }.getOrNull() },
                dockOrder = DockOrder.parse(p[Keys.dockOrder]),
            )
        }

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit {
            it[Keys.theme] = choice.name
            it.remove(Keys.legacyTheme)
        }
        ThemeCache.write(context, choice)
    }

    /**
     * Resets every preference to its default (part of Settings → Reset MNYBOXD). The next read
     * shows onboarding again. Theme cache is cleared too so the next launch starts on the default.
     */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        ThemeCache.clear(context)
    }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setVoiceDisclosureAccepted() = context.dataStore.edit { it[Keys.voiceDisclosure] = true }
    suspend fun setSecureScreen(enabled: Boolean) = context.dataStore.edit { it[Keys.secureScreen] = enabled }

    /** Saves the dock's tab order, normalised so every tab is stored exactly once. */
    suspend fun setDockOrder(order: List<AppTab>) {
        val normalised = DockOrder.parse(DockOrder.serialize(order))
        context.dataStore.edit { it[Keys.dockOrder] = DockOrder.serialize(normalised) }
    }

    suspend fun rememberPaymentChoice(accountId: Long, channel: PaymentChannel?, provider: PaymentProvider?) =
        context.dataStore.edit {
            it[Keys.lastAccount] = accountId
            if (channel != null) it[Keys.lastChannel] = channel.name else it.remove(Keys.lastChannel)
            if (provider != null) it[Keys.lastProvider] = provider.name else it.remove(Keys.lastProvider)
        }
}
