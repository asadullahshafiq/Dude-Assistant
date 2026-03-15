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

    init { createRecognizer() }

    private fun createRecognizer() {
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListening()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isListening) startListening()
                    }, 1000)
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d("VoiceManager", "Result: $text")
                    if (text.isNotEmpty()) onResult(text)
                    else {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            startListening()
                        }, 500)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.length > 3) onResult(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }

    fun startListening() {
        if (isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // English use kar raha hai - Roman Urdu bhi samjhega
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            createRecognizer()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startListening()
            }, 500)
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
