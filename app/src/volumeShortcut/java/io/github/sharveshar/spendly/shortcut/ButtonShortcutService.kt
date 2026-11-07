package io.github.sharveshar.spendly.shortcut

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.VisibleForTesting
import io.github.sharveshar.spendly.QuickAddActivity
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

    /** Switches the service off; replaced in tests, which can't observe [disableSelf]. */
    @VisibleForTesting
    internal var switchOff: () -> Unit = { disableSelf() }

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
        val prefs = appContainer.preferences
        scope = MainScope().apply {
            launch {
                // Being connected means the user just switched the service on in Android's
                // settings. Record that first, so the watcher below never sees a stale "off".
                prefs.setShortcutActive(true)
                launch {
                    prefs.preferences
                        .map { it.buttonShortcut }
                        .distinctUntilChanged()
                        .collect { shortcutHandler.shortcut = it }
                }
                // "Turn off" in Spendly's settings: switch this service off entirely. Android then
                // unbinds it, so it stops receiving key events until it's switched on again.
                prefs.preferences
                    .map { it.buttonShortcutActive }
                    .distinctUntilChanged()
                    .collect { active -> if (!active) switchOff() }
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
            // background activity-start limits. Quick Add opens as a pop-up over the current app
            // (QuickAddActivity), asks to unlock first if the phone is locked, and ignores a second
            // launch while it's already showing.
            startActivity(QuickAddActivity.launchIntent(this))
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
