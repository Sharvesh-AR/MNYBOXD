package io.github.sharveshar.spendly.shortcut

import android.media.AudioManager
import android.view.KeyEvent

/** Hardware button the user picked for the Quick Add shortcut. Stored by name in preferences. */
enum class ShortcutButton(val keyCode: Int) {
    VOLUME_DOWN(KeyEvent.KEYCODE_VOLUME_DOWN),
    VOLUME_UP(KeyEvent.KEYCODE_VOLUME_UP),
}

/** Pressing [button] [presses] times in quick succession opens Quick Add. */
data class ButtonShortcut(
    val button: ShortcutButton = ShortcutButton.VOLUME_DOWN,
    val presses: Int = DEFAULT_PRESSES,
) {
    init {
        require(presses in PRESS_CHOICES) { "presses must be one of $PRESS_CHOICES" }
    }

    companion object {
        /** Double or triple press. A single press would make the button useless for volume. */
        val PRESS_CHOICES = listOf(2, 3)
        const val DEFAULT_PRESSES = 3
    }
}

/** Where one key press leaves the pattern being entered. */
enum class PressStep { NOT_PART, FIRST, NEXT, COMPLETE }

/**
 * Recognises N quick presses of one button from key-down events alone. Holding the button
 * (auto-repeat, i.e. changing the volume steadily), pressing any other key, or pausing longer
 * than [maxGapMs] between presses starts the count over.
 */
class PressSequenceDetector(private val maxGapMs: Long = MAX_GAP_MS) {
    private var count = 0
    private var lastDownAt = 0L

    /** @param eventTime [KeyEvent.getEventTime], a monotonic uptime in milliseconds. */
    fun onKeyDown(keyCode: Int, repeatCount: Int, eventTime: Long, shortcut: ButtonShortcut): PressStep {
        if (keyCode != shortcut.button.keyCode || repeatCount > 0) {
            reset()
            return PressStep.NOT_PART
        }
        count = if (count > 0 && eventTime - lastDownAt in 0..maxGapMs) count + 1 else 1
        lastDownAt = eventTime
        return when {
            count >= shortcut.presses -> {
                reset()
                PressStep.COMPLETE
            }
            count == 1 -> PressStep.FIRST
            else -> PressStep.NEXT
        }
    }

    fun reset() {
        count = 0
    }

    companion object {
        /** Longest pause between two presses that still counts as one pattern. */
        const val MAX_GAP_MS = 350L
    }
}

/**
 * Volume levels from just before a pattern started. The shortcut never blocks the button, so its
 * presses change the volume as usual; restoring afterwards leaves the volume where it was.
 */
class VolumeSnapshot private constructor(private val levels: Map<Int, Int>) {
    fun restore(audio: AudioManager) {
        for ((stream, level) in levels) {
            if (audio.getStreamVolume(stream) == level) continue
            // Best effort: Android refuses volume changes that would switch Do Not Disturb on or off.
            runCatching { audio.setStreamVolume(stream, level, 0) }
        }
    }

    companion object {
        /** Outside calls, volume buttons adjust media (Android 9+ default) or, on some phones, the ringer. */
        private val STREAMS = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING)

        fun take(audio: AudioManager) = VolumeSnapshot(STREAMS.associateWith { audio.getStreamVolume(it) })
    }
}

/**
 * Everything [ButtonShortcutService] does with a key event, kept free of service plumbing so it
 * can be unit-tested. It only ever observes: the service always lets the event through unchanged.
 */
class ButtonShortcutHandler(
    private val audio: AudioManager,
    private val openQuickAdd: () -> Unit,
    private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
) {
    private val detector = PressSequenceDetector()
    private var snapshot: VolumeSnapshot? = null

    var shortcut: ButtonShortcut = ButtonShortcut()
        set(value) {
            if (value != field) {
                detector.reset()
                snapshot = null
            }
            field = value
        }

    fun onKeyEvent(event: KeyEvent) = onKeyEvent(event.action, event.keyCode, event.repeatCount, event.eventTime)

    fun onKeyEvent(action: Int, keyCode: Int, repeatCount: Int, eventTime: Long) {
        if (action != KeyEvent.ACTION_DOWN) return
        // Ringing and calls own the volume buttons (a press silences the ringtone); stay out of the way.
        if (audio.mode != AudioManager.MODE_NORMAL) {
            detector.reset()
            snapshot = null
            return
        }
        when (detector.onKeyDown(keyCode, repeatCount, eventTime, shortcut)) {
            PressStep.FIRST -> snapshot = VolumeSnapshot.take(audio)
            PressStep.NEXT -> Unit
            PressStep.NOT_PART -> snapshot = null
            PressStep.COMPLETE -> {
                val before = snapshot
                snapshot = null
                openQuickAdd()
                // The final press is applied only after this event is let through, so restore a moment later.
                if (before != null) postDelayed(RESTORE_DELAY_MS) { before.restore(audio) }
            }
        }
    }

    companion object {
        const val RESTORE_DELAY_MS = 500L
    }
}
