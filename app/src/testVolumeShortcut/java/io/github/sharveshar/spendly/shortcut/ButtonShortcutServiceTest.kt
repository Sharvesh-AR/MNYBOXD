// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.shortcut

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.QuickAddActivity
import io.github.sharveshar.spendly.SpendlyApplication
import io.github.sharveshar.spendly.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration as JavaDuration
import kotlin.time.Duration.Companion.minutes

/**
 * The real accessibility service, driven the way Android drives it: created, connected, fed key
 * events and destroyed. Complements ButtonShortcutHandlerTest, which covers the press logic alone.
 */
@RunWith(RobolectricTestRunner::class)
// The service reaches Spendly's settings through the real Application, as on a phone.
@Config(sdk = [35], application = SpendlyApplication::class)
class ButtonShortcutServiceTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.appContainer.preferences
    private val audio get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private lateinit var controller: ServiceController<ButtonShortcutService>
    private var switchedOff = 0
    private var clock = SystemClock.uptimeMillis()

    @Before fun setUp() {
        // The service must flip this back on when it connects.
        io { prefs.setShortcutActive(false) }
        audio.mode = AudioManager.MODE_NORMAL
        shadowOf(app).clearNextStartedActivities()
        controller = Robolectric.buildService(ButtonShortcutService::class.java).create()
        controller.get().switchOff = { switchedOff++ }
    }

    @After fun tearDown() {
        // DataStore is one instance per JVM, shared with later test classes.
        io {
            prefs.setShortcutButton(ShortcutButton.VOLUME_DOWN)
            prefs.setShortcutPresses(ButtonShortcut.DEFAULT_PRESSES)
            prefs.setShortcutActive(true)
        }
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(5_000) { block() } }

    /** Lets the service's main-thread coroutines and DataStore writes finish until [condition] holds. */
    private fun settleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met in time" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }

    private fun connect(): ButtonShortcutService {
        val service = controller.get()
        // Android calls this once the user switches the service on in Accessibility settings.
        ButtonShortcutService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        settleUntil { io { prefs.preferences.first().buttonShortcutActive } }
        return service
    }

    /** Calls the service's key filter (protected; Android calls it from the system side). */
    private fun deliver(service: ButtonShortcutService, event: KeyEvent): Boolean =
        ButtonShortcutService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java).apply { isAccessible = true }.invoke(service, event) as Boolean

    /** A key press as Android delivers it to the service; returns whether the service consumed it. */
    private fun press(service: ButtonShortcutService, keyCode: Int, gapMs: Long = 120): Boolean {
        clock += gapMs
        val down = deliver(service, KeyEvent(clock, clock, KeyEvent.ACTION_DOWN, keyCode, 0))
        val up = deliver(service, KeyEvent(clock, clock + 40, KeyEvent.ACTION_UP, keyCode, 0))
        return down || up
    }

    private fun launches(): Int {
        var n = 0
        while (true) {
            val intent = shadowOf(app).nextStartedActivity ?: return n
            assertEquals(QuickAddActivity::class.java.name, intent.component?.className)
            n++
        }
    }

    @Test fun `switching the service on marks the shortcut active again`() {
        assertFalse(io { prefs.preferences.first().buttonShortcutActive })
        connect()
        assertTrue(io { prefs.preferences.first().buttonShortcutActive })
        assertEquals(0, switchedOff)
    }

    @Test fun `the saved pattern opens Quick Add exactly once and every key still goes through`() {
        val service = connect()
        repeat(3) { assertFalse("the service must never swallow a key", press(service, KeyEvent.KEYCODE_VOLUME_DOWN)) }
        assertEquals(1, launches())
        // Other keys, and the next press after the pattern, open nothing and are never consumed.
        assertFalse(press(service, KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(press(service, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(0, launches())
    }

    @Test fun `a pattern changed in Spendly applies to the running service`() {
        val service = connect()
        io {
            prefs.setShortcutButton(ShortcutButton.VOLUME_UP)
            prefs.setShortcutPresses(2)
        }
        settleUntil { io { prefs.preferences.first().buttonShortcut } == ButtonShortcut(ShortcutButton.VOLUME_UP, 2) }
        // Give the service's preference watcher a moment on the main thread.
        repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        repeat(3) { press(service, KeyEvent.KEYCODE_VOLUME_DOWN) }
        assertEquals("the old pattern no longer opens Quick Add", 0, launches())
        repeat(2) { press(service, KeyEvent.KEYCODE_VOLUME_UP) }
        assertEquals(1, launches())
    }

    @Test fun `the volume is put back after the pattern`() {
        val service = connect()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 7, 0)
        repeat(3) { press(service, KeyEvent.KEYCODE_VOLUME_DOWN) }
        // Android applies the presses to the volume as usual (the service never blocks them)...
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 4, 0)
        // ...and the service restores it shortly afterwards.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(JavaDuration.ofMillis(ButtonShortcutHandler.RESTORE_DELAY_MS + 100))
        assertEquals(7, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(1, launches())
    }

    @Test fun `ringing and calls keep the volume buttons to themselves`() {
        val service = connect()
        for (mode in listOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
            audio.mode = mode
            repeat(3) { assertFalse(press(service, KeyEvent.KEYCODE_VOLUME_DOWN)) }
        }
        assertEquals(0, launches())
        audio.mode = AudioManager.MODE_NORMAL
        repeat(3) { press(service, KeyEvent.KEYCODE_VOLUME_DOWN) }
        assertEquals(1, launches())
    }

    @Test fun `turning the shortcut off in Spendly switches the service off`() {
        connect()
        io { prefs.setShortcutActive(false) }
        settleUntil { switchedOff > 0 }
        assertEquals(1, switchedOff)
    }

    @Test fun `once Android destroys the service, presses do nothing`() {
        val service = connect()
        controller.destroy()
        repeat(3) { assertFalse(press(service, KeyEvent.KEYCODE_VOLUME_DOWN)) }
        assertEquals(0, launches())
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
