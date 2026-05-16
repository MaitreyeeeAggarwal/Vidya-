package com.vidya.core.device

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Step 3: Multilingual Output (Text-to-Speech)
 * Broadcasts Gemma's synthesized regional text through the device speaker.
 */
class VidyaVoiceOutput(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Setup local offline engine capabilities
            tts.setEngineByPackage("com.google.android.tts")
        }
    }

    /**
     * Speaks the generated AI response aloud using regional language packs.
     */
    fun speakToClass(aiGeneratedText: String, languageCode: String) {
        // languageCode example: "hi" for Hindi, "ta" for Tamil, "te" for Telugu
        val locale = Locale(languageCode)
        
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
            tts.setSpeechRate(0.9f) // Slightly slower rate for classroom clarity
            
            // Broadcast aloud to the classroom
            tts.speak(aiGeneratedText, TextToSpeech.QUEUE_FLUSH, null, "VidyaLoopID")
        } else {
            // Fallback if language isn't downloaded yet
            tts.language = Locale("en", "IN")
            tts.speak(aiGeneratedText, TextToSpeech.QUEUE_FLUSH, null, "VidyaLoopID")
        }
    }
    
    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
