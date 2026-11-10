// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceState {
    data object Idle : VoiceState
    /** [level] is a 0..1 loudness estimate for the listening indicator. */
    data class Listening(val level: Float, val partial: String, val onDevice: Boolean) : VoiceState
    data object Processing : VoiceState
    data class Heard(val text: String) : VoiceState
    data class Failed(val message: String, val retryable: Boolean) : VoiceState
}

/**
 * Thin wrapper over Android's [SpeechRecognizer]. Spendly never records, stores or uploads audio
 * itself: recognition is delegated to the device's speech service, and only the resulting text
 * is parsed locally. On Android 12+ the on-device recognizer is preferred when available.
 * Must be used from the main thread.
 */
class VoiceRecognizer(
    private val context: Context,
    /** Whether any recognition service exists; injectable so the unavailable path can be tested. */
    private val serviceAvailable: (Context) -> Boolean = ::anyRecognizerAvailable,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var triedFallback = false

    val isAvailable: Boolean
        get() = serviceAvailable(context)

    private fun onDeviceAvailable(): Boolean = onDeviceRecognizerAvailable(context)

    fun start() {
        destroyRecognizer()
        triedFallback = false
        if (!isAvailable) {
            _state.value = VoiceState.Failed("Speech recognition isn't available on this device. Use the keypad instead.", retryable = false)
            return
        }
        begin(preferOnDevice = true)
    }

    private fun begin(preferOnDevice: Boolean) {
        usingOnDevice = preferOnDevice && onDeviceAvailable()
        val r = try {
            if (usingOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (_: Exception) {
            _state.value = VoiceState.Failed("Couldn't start speech recognition. Use the keypad instead.", retryable = true)
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        _state.value = VoiceState.Listening(0f, "", usingOnDevice)
        try {
            r.startListening(intent)
        } catch (_: Exception) {
            _state.value = VoiceState.Failed("Couldn't start speech recognition. Use the keypad instead.", retryable = true)
        }
    }

    /** Stop listening and process what was heard so far. */
    fun stop() {
        recognizer?.stopListening()
        if (_state.value is VoiceState.Listening) _state.value = VoiceState.Processing
    }

    fun cancel() {
        recognizer?.cancel()
        destroyRecognizer()
        _state.value = VoiceState.Idle
    }

    fun reset() { _state.value = VoiceState.Idle }

    fun destroy() {
        destroyRecognizer()
        _state.value = VoiceState.Idle
    }

    private fun destroyRecognizer() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            val s = _state.value
            if (s is VoiceState.Listening) _state.value = s.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit // audio buffers are ignored, never stored
        override fun onEndOfSpeech() {
            if (_state.value is VoiceState.Listening) _state.value = VoiceState.Processing
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val s = _state.value
            if (s is VoiceState.Listening && text.isNotBlank()) _state.value = s.copy(partial = text)
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            destroyRecognizer()
            _state.value = if (text.isBlank()) {
                VoiceState.Failed("Didn't catch that. Try again, or use the keypad.", retryable = true)
            } else {
                VoiceState.Heard(text.take(300))
            }
        }
        override fun onError(error: Int) {
            // On-device model missing for this language: fall back once to the default service.
            if (usingOnDevice && !triedFallback && error in FALLBACK_ERRORS && SpeechRecognizer.isRecognitionAvailable(context)) {
                triedFallback = true
                destroyRecognizer()
                begin(preferOnDevice = false)
                return
            }
            destroyRecognizer()
            _state.value = VoiceState.Failed(messageFor(error), retryable = error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        fun onDeviceRecognizerAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

        fun anyRecognizerAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context) || onDeviceRecognizerAvailable(context)

        /** ERROR_LANGUAGE_NOT_SUPPORTED (12), ERROR_LANGUAGE_UNAVAILABLE (13), ERROR_SERVER_DISCONNECTED (11). */
        private val FALLBACK_ERRORS = setOf(11, 12, 13, SpeechRecognizer.ERROR_CLIENT)

        private fun messageFor(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again, or use the keypad."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                "Your speech service needs a connection right now. Try again or use the keypad."
            SpeechRecognizer.ERROR_AUDIO -> "The microphone couldn't be used. Use the keypad instead."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice entry."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again in a moment."
            12, 13 -> "Speech recognition isn't available for English (India) on this device. Use the keypad instead."
            else -> "Voice entry didn't work this time. Use the keypad instead."
        }
    }
}
