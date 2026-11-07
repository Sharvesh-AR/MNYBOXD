package io.github.sharveshar.spendly

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.ui.SpendlyApp
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.components.rememberReducedMotion
import io.github.sharveshar.spendly.ui.components.LocalSecureWindows
import io.github.sharveshar.spendly.ui.components.setSecure
import io.github.sharveshar.spendly.ui.theme.AnimatedSpendlyTheme
import io.github.sharveshar.spendly.ui.theme.Palettes
import io.github.sharveshar.spendly.ui.theme.SpendlyPalette
import io.github.sharveshar.spendly.ui.theme.ThemeCache
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Top-level screens an external entry point (a widget) may open. */
enum class AppTab { HOME, ACTIVITY, ACCOUNTS, BUDGETS, INSIGHTS }

/** External entry points (launcher shortcuts, widgets). Only these fixed requests are honoured. */
sealed interface LaunchRequest {
    data object QuickAdd : LaunchRequest
    data object Voice : LaunchRequest
    data class Open(val tab: AppTab) : LaunchRequest
    data class OpenAccount(val id: Long) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    private val launchRequests = Channel<LaunchRequest>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The cached theme is read synchronously so the very first frame (window background,
        // splash, system bar icons) already matches the user's theme.
        val cachedTheme = ThemeCache.read(this)
        val startPalette = Palettes.of(cachedTheme.resolve(isNightMode()))
        applyWindowTheme(startPalette)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        publishShortcuts()
        val container = appContainer

        setContent {
            val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
            val choice = prefs?.theme ?: cachedTheme
            val palette = Palettes.of(choice.resolve(isSystemInDarkTheme()))
            LaunchedEffect(palette) { applyWindowTheme(palette) }
            LaunchedEffect(prefs?.theme) { prefs?.theme?.let { ThemeCache.write(this@MainActivity, it) } }
            val secure = prefs?.secureScreen == true
            LaunchedEffect(secure) { window.setSecure(secure) }
            val reduced = rememberReducedMotion()
            CompositionLocalProvider(LocalReducedMotion provides reduced, LocalSecureWindows provides secure) {
                AnimatedSpendlyTheme(palette, reduced) {
                    SpendlyApp(container, launchRequests.receiveAsFlow())
                }
            }
        }
    }

    private fun isNightMode() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun applyWindowTheme(palette: SpendlyPalette) {
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
        val style = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Accepts only our own fixed actions. Data is ignored, and the single extra read (an account
     * id for ACTION_OPEN_ACCOUNT) must be a positive number; anything else falls back to the
     * Accounts tab. At most this opens one of Spendly's own screens.
     */
    private fun handleIntent(intent: Intent?) {
        val request = when (intent?.action) {
            ACTION_QUICK_ADD -> LaunchRequest.QuickAdd
            ACTION_VOICE_ADD -> LaunchRequest.Voice
            ACTION_OPEN_HOME -> LaunchRequest.Open(AppTab.HOME)
            ACTION_OPEN_BUDGETS -> LaunchRequest.Open(AppTab.BUDGETS)
            ACTION_OPEN_ACCOUNTS -> LaunchRequest.Open(AppTab.ACCOUNTS)
            ACTION_OPEN_INSIGHTS -> LaunchRequest.Open(AppTab.INSIGHTS)
            ACTION_OPEN_ACCOUNT -> runCatching { intent.getLongExtra(EXTRA_ACCOUNT_ID, 0L) }.getOrDefault(0L)
                .let { id -> if (id > 0) LaunchRequest.OpenAccount(id) else LaunchRequest.Open(AppTab.ACCOUNTS) }
            else -> null
        } ?: return
        launchRequests.trySend(request)
        if (request != LaunchRequest.QuickAdd && request != LaunchRequest.Voice) return
        // Lets the launcher rank shortcuts by actual use.
        runCatching { ShortcutManagerCompat.reportShortcutUsed(this, if (request == LaunchRequest.Voice) SHORTCUT_VOICE else SHORTCUT_ADD) }
        // The volume-button shortcut also works on the lock screen. Spendly never shows over the
        // keyguard, so ask the user to unlock; Quick Add is waiting behind it.
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) keyguard.requestDismissKeyguard(this, null)
    }

    private fun publishShortcuts() {
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    ShortcutInfoCompat.Builder(this, SHORTCUT_ADD)
                        .setShortLabel("Add expense")
                        .setLongLabel("Add an expense")
                        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_add))
                        .setIntent(QuickAddActivity.launchIntent(this, ACTION_QUICK_ADD))
                        .build(),
                    ShortcutInfoCompat.Builder(this, SHORTCUT_VOICE)
                        .setShortLabel("Voice expense")
                        .setLongLabel("Add an expense by voice")
                        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_mic))
                        .setIntent(QuickAddActivity.launchIntent(this, ACTION_VOICE_ADD))
                        .build(),
                ),
            )
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "io.github.sharveshar.spendly.action.QUICK_ADD"
        const val ACTION_VOICE_ADD = "io.github.sharveshar.spendly.action.VOICE_ADD"
        const val ACTION_OPEN_HOME = "io.github.sharveshar.spendly.action.OPEN_HOME"
        const val ACTION_OPEN_BUDGETS = "io.github.sharveshar.spendly.action.OPEN_BUDGETS"
        const val ACTION_OPEN_ACCOUNTS = "io.github.sharveshar.spendly.action.OPEN_ACCOUNTS"
        const val ACTION_OPEN_INSIGHTS = "io.github.sharveshar.spendly.action.OPEN_INSIGHTS"
        const val ACTION_OPEN_ACCOUNT = "io.github.sharveshar.spendly.action.OPEN_ACCOUNT"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val SHORTCUT_ADD = "quick_add"
        const val SHORTCUT_VOICE = "voice_add"

        fun launchIntent(context: Context, action: String): Intent =
            Intent(action).setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
