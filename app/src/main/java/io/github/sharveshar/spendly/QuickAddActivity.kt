package io.github.sharveshar.spendly

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.sharveshar.spendly.data.db.DatabaseState
import io.github.sharveshar.spendly.ui.components.LocalReducedMotion
import io.github.sharveshar.spendly.ui.components.LocalSecureWindows
import io.github.sharveshar.spendly.ui.components.rememberReducedMotion
import io.github.sharveshar.spendly.ui.components.setSecure
import io.github.sharveshar.spendly.ui.navigation.EntryRoute
import io.github.sharveshar.spendly.ui.navigation.PopupBaseRoute
import io.github.sharveshar.spendly.ui.quickAddDestinations
import io.github.sharveshar.spendly.ui.theme.Palettes
import io.github.sharveshar.spendly.ui.theme.SpendlyTheme
import io.github.sharveshar.spendly.ui.theme.ThemeCache

/**
 * Quick Add as a pop-up over whatever is on screen, for entry points outside the app: the
 * volume-button shortcut, the Quick Settings tile, home-screen widgets, launcher shortcuts and the
 * "Add expense" icon. Transparent window in its own task, excluded from recents: when Quick Add
 * closes the activity finishes and the user is back exactly where they were.
 *
 * Like MainActivity it honours only two fixed actions and ignores extras and data. A second launch
 * while Quick Add is already showing is ignored, so a shortcut can never stack two pop-ups.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val startPalette = Palettes.of(ThemeCache.read(this).resolve(night))
        val bars = if (startPalette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val voice = intent?.action == MainActivity.ACTION_VOICE_ADD
        if (savedInstanceState == null) {
            runCatching { ShortcutManagerCompat.reportShortcutUsed(this, if (voice) MainActivity.SHORTCUT_VOICE else MainActivity.SHORTCUT_ADD) }
            // Spendly never shows over the lock screen: ask to unlock first.
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardLocked == true) keyguard.requestDismissKeyguard(this, null)
        }
        val container = appContainer

        setContent {
            val db by container.databaseManager.state.collectAsStateWithLifecycle()
            val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
            val palette = Palettes.of((prefs?.theme ?: ThemeCache.read(this)).resolve(isSystemInDarkTheme()))
            val secure = prefs?.secureScreen == true
            LaunchedEffect(secure) { window.setSecure(secure) }
            CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion(), LocalSecureWindows provides secure) {
                SpendlyTheme(palette) {
                    val p = prefs
                    when {
                        // Still opening the encrypted database: stay transparent for the moment it takes.
                        db is DatabaseState.Opening || p == null -> Unit
                        // First run, or the database needs attention: the full app handles those.
                        db !is DatabaseState.Ready || !p.onboardingDone -> LaunchedEffect(Unit) { openAppInstead() }
                        else -> QuickAddPopup(startVoice = voice, onFinished = ::finishQuietly)
                    }
                }
            }
        }
    }

    private fun openAppInstead() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishQuietly()
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        /** Opens Quick Add (or voice entry for [MainActivity.ACTION_VOICE_ADD]) as a pop-up. */
        fun launchIntent(context: Context, action: String = MainActivity.ACTION_QUICK_ADD): Intent =
            Intent(action).setClass(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun QuickAddPopup(startVoice: Boolean, onFinished: () -> Unit) {
    val nav = rememberNavController()
    val finished by rememberUpdatedState(onFinished)
    var opened by rememberSaveable { mutableStateOf(false) }
    NavHost(nav, startDestination = PopupBaseRoute) {
        // Nothing of its own: whatever app was on screen shows through behind the sheet.
        composable<PopupBaseRoute> { Box(Modifier.fillMaxSize()) }
        quickAddDestinations(nav)
    }
    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            nav.navigate(EntryRoute(startVoice = startVoice))
        }
    }
    // When Quick Add (and any account editor it opened) is gone, so is the pop-up: the empty
    // base screen is on top again.
    val top by nav.currentBackStackEntryAsState()
    LaunchedEffect(top, opened) {
        if (opened && top?.destination?.hasRoute(PopupBaseRoute::class) == true) finished()
    }
}
