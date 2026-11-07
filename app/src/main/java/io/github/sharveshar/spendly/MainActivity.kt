package io.github.sharveshar.spendly

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sharveshar.spendly.data.prefs.ThemeMode
import io.github.sharveshar.spendly.ui.SpendlyApp
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.components.rememberReducedMotion
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** External entry points (Quick Settings tile, launcher shortcuts, button shortcut). Only these fixed actions are honoured. */
enum class LaunchRequest { QUICK_ADD, VOICE }

class MainActivity : ComponentActivity() {
    private val launchRequests = Channel<LaunchRequest>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        publishShortcuts()
        val container = appContainer

        setContent {
            val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (prefs?.themeMode ?: ThemeMode.DARK) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            LaunchedEffect(prefs?.secureScreen) {
                if (prefs?.secureScreen == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
                SpendlyTheme(dark = dark) {
                    SpendlyApp(container, launchRequests.receiveAsFlow())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Accepts only our own explicit actions; extras and data are ignored entirely. */
    private fun handleIntent(intent: Intent?) {
        val request = when (intent?.action) {
            ACTION_QUICK_ADD -> LaunchRequest.QUICK_ADD
            ACTION_VOICE_ADD -> LaunchRequest.VOICE
            else -> null
        } ?: return
        // Lets the launcher rank shortcuts by actual use.
        runCatching { ShortcutManagerCompat.reportShortcutUsed(this, if (request == LaunchRequest.VOICE) SHORTCUT_VOICE else SHORTCUT_ADD) }
        launchRequests.trySend(request)
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
                        .setIntent(launchIntent(this, ACTION_QUICK_ADD))
                        .build(),
                    ShortcutInfoCompat.Builder(this, SHORTCUT_VOICE)
                        .setShortLabel("Voice expense")
                        .setLongLabel("Add an expense by voice")
                        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_mic))
                        .setIntent(launchIntent(this, ACTION_VOICE_ADD))
                        .build(),
                ),
            )
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "io.github.sharveshar.spendly.action.QUICK_ADD"
        const val ACTION_VOICE_ADD = "io.github.sharveshar.spendly.action.VOICE_ADD"
        private const val SHORTCUT_ADD = "quick_add"
        private const val SHORTCUT_VOICE = "voice_add"

        fun launchIntent(context: Context, action: String): Intent =
            Intent(action).setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
