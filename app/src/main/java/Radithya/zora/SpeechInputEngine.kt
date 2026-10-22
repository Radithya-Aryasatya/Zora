package Radithya.zora

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Wrapper around Android's built-in [SpeechRecognizer].
 *
 * Designed for Zora's "speak your request" mode:
 *  - start()  : opens the mic, auto-stops when the user goes silent
 *  - stop()   : cancels recognition
 *  - The final transcript is delivered via [onFinalResult]; errors via [onError].
 *  - Nothing is ever echoed back to the user by this class — the caller decides
 *    what to speak (if anything).
 *
 * On Android 12+ (API 31) it prefers the fully on-device recognizer so no
 * network round-trip is needed for short commands. On older devices it falls
 * back to the standard recognizer (may use network on some OEMs).
 */
class SpeechInputEngine private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    /**
     * A recognizer together with the knowledge of *how* it was created.
     *
     *  - [isOnDevice] = true  -> fully offline, no network round-trip needed.
     *  - [isOnDevice] = false -> the standard recognizer, which may use the
     *                           network on some OEMs.
     */
    private class Recognizer(val instance: SpeechRecognizer, val isOnDevice: Boolean) {
        /** Releases the underlying recognizer (the box forwards to the thing inside). */
        fun destroy() = instance.destroy()
    }

    private var recognizer: Recognizer = createRecognizer(context)
    private var isListening = false
    private var onFinalResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /** True when the active recognizer runs entirely on the device. */
    val isOnDevice: Boolean get() = recognizer.isOnDevice

    fun start(onFinal: (String) -> Unit, onErr: (String) -> Unit) {
        if (isListening) return
        isListening = true
        onFinalResult = onFinal
        onError = onErr

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Prefer the offline model so short commands never need a network round-trip.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            // Auto-stop rules: once the user has spoken for at least MINIMUM_LENGTH and
            // then stays quiet for COMPLETE_SILENCE_LENGTH, the utterance is finished
            // and the final transcript is delivered.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500
            )
        }

        try {
            recognizer.instance.startListening(intent)
        } catch (e: Exception) {
            // e.g. the recognizer is already busy or the service is unavailable.
            isListening = false
            scope.launch { onError?.invoke("Could not start the microphone. Please try again.") }
        }
    }

    fun stop() {
        if (!isListening) return
        isListening = false
        // cancel() aborts the session and releases the microphone immediately.
        try {
            recognizer.instance.cancel()
        } catch (e: Exception) {
            // Ignore — the recognizer is already gone.
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private inner class Listener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsDb: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onEndOfSpeech() {
            // Silence detected — recognizer will deliver the final result shortly.
        }
        override fun onError(error: Int) {
            isListening = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Please try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice system is busy. Try again in a moment."
                SpeechRecognizer.ERROR_NETWORK -> "Network error during voice recognition."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice service timed out. Please try again."
                SpeechRecognizer.ERROR_SERVER -> "Voice service error. Try again."
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Voice service disconnected. Try again."
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                SpeechRecognizer.ERROR_CLIENT -> "Voice request was cancelled."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Speech language is not supported."
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Speech language is unavailable right now."
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Speech recognition is not available on this device."
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many voice requests. Please wait."
                else -> "Voice recognition error."
            }
            scope.launch { onError?.invoke(msg) }
        }
        override fun onResults(results: Bundle) {
            isListening = false
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
            val text = matches.firstOrNull() ?: ""
            scope.launch { onFinalResult?.invoke(text) }
        }
        override fun onPartialResults(results: Bundle) {}
        override fun onEvent(event: Int, params: Bundle) {}
    }

    init {
        // Note: SpeechRecognizer exposes setRecognitionListener() but no getter,
        // so Kotlin cannot synthesise a property here — call the setter directly.
        recognizer.instance.setRecognitionListener(Listener())
    }

    companion object {
        @Volatile private var instance: SpeechInputEngine? = null

        fun create(context: Context, scope: CoroutineScope): SpeechInputEngine {
            return instance ?: synchronized(this) {
                instance ?: run {
                    SpeechInputEngine(context, scope).also { instance = it }
                }
            }
        }

        /**
         * Builds the best available recognizer for this device.
         *
         *  - API 31+ : on-device recognizer (no network needed for short commands).
         *  - older   : the standard recognizer (may use the network on some OEMs).
         *
         * The on-device recognizer throws if the device has no on-device model,
         * so every step is guarded and we always fall back to the standard one.
         */
        private fun createRecognizer(context: Context): Recognizer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        return Recognizer(
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(context),
                            isOnDevice = true
                        )
                    }
                } catch (e: Exception) {
                    // Fall through to the standard recognizer below.
                }
            }
            return Recognizer(
                SpeechRecognizer.createSpeechRecognizer(context),
                isOnDevice = false
            )
        }

        fun release() {
            instance?.recognizer?.destroy()
            instance = null
        }
    }
}