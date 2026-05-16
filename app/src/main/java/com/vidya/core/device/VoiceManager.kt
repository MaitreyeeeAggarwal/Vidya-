package com.vidya.core.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles the offline Android Speech-to-Text (STT) and Text-to-Speech (TTS).
 * Integrates directly with the AiTeacherLoop.
 */
class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("en", "IN")) // Indian English (add Hindi later)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceManager", "Language not supported or missing data")
            } else {
                isTtsReady = true
            }
        }
    }

    /**
     * Speaks the generated AI response aloud.
     */
    fun speak(text: String, onSpeakComplete: () -> Unit) {
        if (!isTtsReady) return
        
        // Use a UtteranceProgressListener in production to trigger onSpeakComplete
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vidya_utterance_1")
        // For scaffold, immediately trigger complete (requires listener binding in reality)
        onSpeakComplete()
    }

    /**
     * Listens to the student's answer using Offline Language Packs.
     * Uses Coroutines to return the recognized text asynchronously.
     */
    suspend fun listenToStudent(): String = suspendCancellableCoroutine { continuation ->
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            // Crucial: Force offline recognition
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) 
        }

        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""
                if (continuation.isActive) continuation.resume(spokenText)
            }

            override fun onError(error: Int) {
                if (continuation.isActive) continuation.resumeWithException(Exception("STT Error Code: $error"))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(intent)

        continuation.invokeOnCancellation {
            speechRecognizer.stopListening()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer.destroy()
    }
}
