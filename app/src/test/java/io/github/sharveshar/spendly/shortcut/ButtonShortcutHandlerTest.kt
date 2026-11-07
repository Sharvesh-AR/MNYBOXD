package io.github.sharveshar.spendly.shortcut

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioManager.STREAM_RING
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.data.prefs.PreferencesRepository
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ButtonShortcutHandlerTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val down = KeyEvent.KEYCODE_VOLUME_DOWN
    private val up = KeyEvent.KEYCODE_VOLUME_UP

    private lateinit var audio: AudioManager
    private lateinit var handler: ButtonShortcutHandler
    private var opened = 0
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    @Before fun setUp() {
        audio = context.getSystemService(AudioManager::class.java)!!
        audio.mode = AudioManager.MODE_NORMAL
        audio.setStreamVolume(STREAM_MUSIC, 8, 0)
        audio.setStreamVolume(STREAM_RING, 5, 0)
        handler = ButtonShortcutHandler(audio, openQuickAdd = { opened++ }, postDelayed = { delay, action -> scheduled += delay to action })
    }

    /** One press as the service sees it, followed by what Android itself does with the key. */
    private fun press(at: Long, key: Int = down, systemEffect: () -> Unit = {}) {
        handler.onKeyEvent(KeyEvent.ACTION_DOWN, key, 0, at)
        systemEffect()
        handler.onKeyEvent(KeyEvent.ACTION_UP, key, 0, at + 60)
    }

    private fun lower(stream: Int) = { audio.setStreamVolume(stream, audio.getStreamVolume(stream) - 1, 0) }

    private fun runScheduled() {
        scheduled.toList().forEach { it.second() }
        scheduled.clear()
    }

    @Test fun `triple press opens quick add once and puts the media volume back`() {
        press(1_000, systemEffect = lower(STREAM_MUSIC))
        press(1_200, systemEffect = lower(STREAM_MUSIC))
        assertEquals(0, opened)
        press(1_400, systemEffect = lower(STREAM_MUSIC))
        assertEquals(1, opened)
        assertEquals(5, audio.getStreamVolume(STREAM_MUSIC))

        assertEquals(ButtonShortcutHandler.RESTORE_DELAY_MS, scheduled.single().first)
        runScheduled()
        assertEquals(8, audio.getStreamVolume(STREAM_MUSIC))
        assertEquals(5, audio.getStreamVolume(STREAM_RING))
    }

    @Test fun `ringer volume is restored on phones where the buttons control the ringer`() {
        repeat(3) { press(1_000L + it * 150, systemEffect = lower(STREAM_RING)) }
        assertEquals(1, opened)
        runScheduled()
        assertEquals(5, audio.getStreamVolume(STREAM_RING))
        assertEquals(8, audio.getStreamVolume(STREAM_MUSIC))
    }

    @Test fun `ordinary volume changes are left alone`() {
        // Slow presses and a held button: normal volume adjustment, never a shortcut.
        press(1_000, systemEffect = lower(STREAM_MUSIC))
        press(2_000, systemEffect = lower(STREAM_MUSIC))
        handler.onKeyEvent(KeyEvent.ACTION_DOWN, down, 0, 3_000)
        handler.onKeyEvent(KeyEvent.ACTION_DOWN, down, 1, 3_400)
        handler.onKeyEvent(KeyEvent.ACTION_DOWN, down, 2, 3_450)
        handler.onKeyEvent(KeyEvent.ACTION_UP, down, 0, 3_500)
        assertEquals(0, opened)
        assertTrue(scheduled.isEmpty())
        assertEquals(6, audio.getStreamVolume(STREAM_MUSIC))
    }

    @Test fun `stays out of the way while ringing or in a call`() {
        for (mode in listOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
            audio.mode = mode
            repeat(3) { press(1_000L + it * 100) }
        }
        assertEquals(0, opened)
        assertTrue(scheduled.isEmpty())
    }

    @Test fun `follows the chosen button and number of presses`() {
        handler.shortcut = ButtonShortcut(ShortcutButton.VOLUME_UP, 2)
        repeat(3) { press(1_000L + it * 100, key = down) }
        assertEquals(0, opened)
        press(2_000, key = up)
        press(2_150, key = up)
        assertEquals(1, opened)
    }

    @Test fun `changing the pattern mid-way starts the count over`() {
        press(1_000)
        press(1_100)
        handler.shortcut = ButtonShortcut(ShortcutButton.VOLUME_DOWN, 2)
        press(1_200)
        assertEquals(0, opened)
        press(1_300)
        assertEquals(1, opened)
    }

    @Test fun `chosen pattern is saved in preferences`() = runBlocking {
        val prefs = PreferencesRepository(context)
        try {
            prefs.setShortcutButton(ShortcutButton.VOLUME_UP)
            prefs.setShortcutPresses(2)
            assertEquals(ButtonShortcut(ShortcutButton.VOLUME_UP, 2), prefs.preferences.first().buttonShortcut)
            prefs.setShortcutButton(ShortcutButton.VOLUME_DOWN)
            assertEquals(ButtonShortcut(ShortcutButton.VOLUME_DOWN, 2), prefs.preferences.first().buttonShortcut)
        } finally {
            // DataStore is one instance per JVM, shared with later test classes.
            prefs.setShortcutPresses(ButtonShortcut.DEFAULT_PRESSES)
        }
    }
}
