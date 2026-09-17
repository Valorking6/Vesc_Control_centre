package com.example.vesccontrolcentre.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VescVoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VescVoiceAnnouncer"
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var pendingSpeech: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "US English TTS Language is not supported on this device.")
            } else {
                isInitialized = true
                Log.d(TAG, "TextToSpeech successfully initialized.")
                pendingSpeech?.let {
                    speak(it)
                    pendingSpeech = null
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        if (!isInitialized) {
            Log.d(TAG, "TTS not ready yet, queuing announcement: $text")
            pendingSpeech = text
            return
        }

        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vesc_announcement_${System.currentTimeMillis()}")
            Log.d(TAG, "Spoke announcement: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking TTS announcement: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
