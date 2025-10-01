package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Попробуем английский как запасной вариант
                tts?.setLanguage(Locale.US)
            }
            
            isInitialized = true
        }
    }
    
    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
        }
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
    
    fun isReady(): Boolean {
        return isInitialized
    }
}
