package com.dude.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    init {
        createRecognizer()
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Voice recognition available nahi hai is device par.")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListening()
                Log.d("VoiceManager", "Ready for speech")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Kuch suna nahi. Dobara bolein."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waqt khatam. Dobara bolein."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error – offline mode."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                    else -> "Error: $error"
                }
                onError(msg)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Urdu first, fallback to Hindi and English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Listening start nahi ho saka: ${e.message}")
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        isListening = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
