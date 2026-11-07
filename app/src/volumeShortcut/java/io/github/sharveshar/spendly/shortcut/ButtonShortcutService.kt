package io.github.sharveshar.spendly.shortcut

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.sharveshar.spendly.MainActivity
import io.github.sharveshar.spendly.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Opens Quick Add when the user presses their chosen volume-button pattern, from any screen.
 *
 * Off until the user turns it on in Android's Accessibility settings. Its configuration
 * (res/xml/button_shortcut_service.xml) asks for one capability only, key-event filtering; it
 * receives no accessibility events and cannot read window content. It looks at the key code and
 * timing of each key press, stores nothing, and never consumes an event, so every button keeps
 * doing exactly what it normally does in every app.
 */
class ButtonShortcutService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var handler: ButtonShortcutHandler? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val audio = getSystemService(AudioManager::class.java) ?: return
        val shortcutHandler = ButtonShortcutHandler(
            audio = audio,
            openQuickAdd = ::openQuickAdd,
            postDelayed = { delayMs, action -> mainHandler.postDelayed({ action() }, delayMs) },
        )
        handler = shortcutHandler
        scope?.cancel()
        scope = MainScope().apply {
            launch {
                appContainer.preferences.preferences
                    .map { it.buttonShortcut }
                    .distinctUntilChanged()
                    .collect { shortcutHandler.shortcut = it }
            }
        }
    }

    /** Observes only: returning false passes every key on untouched. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        handler?.onKeyEvent(event)
        return false
    }

    private fun openQuickAdd() {
        try {
            // Allowed from the background: Android exempts bound accessibility services from
            // background activity-start limits. MainActivity asks to unlock if the phone is locked.
            startActivity(MainActivity.launchIntent(this, MainActivity.ACTION_QUICK_ADD))
        } catch (_: RuntimeException) {
            // The system refused the launch; there is nothing useful to do from a key filter.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        handler = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
