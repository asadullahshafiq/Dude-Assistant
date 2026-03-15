package com.dude.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try Urdu first, fallback to Hindi, then English
                val result = tts?.setLanguage(Locale("ur", "PK"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val hindi = tts?.setLanguage(Locale("hi", "IN"))
                    if (hindi == TextToSpeech.LANG_MISSING_DATA || hindi == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                }
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                isReady = true
                Log.d("TTS", "TTS ready")
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dude_tts")
        }
    }

    fun speakAdd(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "dude_tts_add")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
