package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка инициализации TTS", e)
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                // Пробуем русский язык
                var result = tts?.setLanguage(Locale("ru", "RU"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Пробуем английский если русский не поддерживается
                    result = tts?.setLanguage(Locale.US)
                }
                
                if (result != TextToSpeech.LANG_MISSING_DATA && 
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                    Log.d("VoiceManager", "TTS успешно инициализирован")
                } else {
                    Log.e("VoiceManager", "Язык не поддерживается")
                }
            } catch (e: Exception) {
                Log.e("VoiceManager", "Ошибка настройки языка TTS", e)
            }
        } else {
            Log.e("VoiceManager", "Ошибка инициализации TTS: $status")
        }
    }
    
    fun speak(text: String) {
        if (isInitialized && text.isNotEmpty()) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            } catch (e: Exception) {
                Log.e("VoiceManager", "Ошибка воспроизведения речи", e)
            }
        }
    }
    
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка остановки TTS", e)
        }
    }
    
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка завершения TTS", e)
        }
    }
    
    fun isReady(): Boolean {
        return isInitialized
    }
}
