package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitListener: (() -> Unit)? = null
    private var onSpeechComplete: ((String) -> Unit)? = null
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Пробуем русский язык
            var result = tts?.setLanguage(Locale("ru", "RU"))
            
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Пробуем английский
                result = tts?.setLanguage(Locale.US)
            }
            
            if (result != TextToSpeech.LANG_MISSING_DATA && 
                result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                
                // Настройка параметров речи
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                
                // Установка слушателя прогресса
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Речь началась
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        onSpeechComplete?.invoke(utteranceId ?: "")
                    }
                    
                    override fun onError(utteranceId: String?) {
                        // Ошибка воспроизведения
                    }
                })
            }
        }
        onInitListener?.invoke()
    }
    
    fun speak(text: String, utteranceId: String = "tts_utterance") {
        if (isInitialized && text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }
    
    fun speakWithQueue(text: String) {
        if (isInitialized && text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "queue_${System.currentTimeMillis()}")
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
    
    fun setOnInitListener(listener: () -> Unit) {
        onInitListener = listener
    }
    
    fun setOnSpeechCompleteListener(listener: (String) -> Unit) {
        onSpeechComplete = listener
    }
    
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }
    
    fun getAvailableLanguages(): List<Locale> {
        return tts?.availableLanguages?.toList() ?: emptyList()
    }
    
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }
}
