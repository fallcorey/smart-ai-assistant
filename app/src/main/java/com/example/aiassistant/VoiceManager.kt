package com.example.aiassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    // Настройки голоса
    private val speechRate = 0.85f  // Скорость речи (0.5-2.0)
    private val pitch = 1.1f        // Высота тона (0.5-2.0)
    private val volume = 1.0f       // Громкость (0.0-1.0)
    
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
                    
                    // Настраиваем параметры голоса
                    tts?.setSpeechRate(speechRate)
                    tts?.setPitch(pitch)
                    
                    // Устанавливаем слушатель прогресса
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d("VoiceManager", "Речь началась: $utteranceId")
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            Log.d("VoiceManager", "Речь завершена: $utteranceId")
                        }
                        
                        override fun onError(utteranceId: String?) {
                            Log.e("VoiceManager", "Ошибка воспроизведения: $utteranceId")
                        }
                    })
                    
                    isInitialized = true
                    Log.d("VoiceManager", "TTS успешно инициализирован с настройками: rate=$speechRate, pitch=$pitch")
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
                // Очищаем очередь перед новым сообщением
                tts?.stop()
                
                // Разбиваем длинный текст на предложения для более естественной речи
                val sentences = splitIntoSentences(text)
                
                if (sentences.size == 1) {
                    // Одно предложение - говорим сразу
                    tts?.speak(sentences[0], TextToSpeech.QUEUE_FLUSH, null, "tts_1")
                } else {
                    // Несколько предложений - добавляем в очередь с паузами
                    sentences.forEachIndexed { index, sentence ->
                        if (index == 0) {
                            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "tts_${index + 1}")
                        } else {
                            // Добавляем небольшую паузу между предложениями
                            tts?.speak("... $sentence", TextToSpeech.QUEUE_ADD, null, "tts_${index + 1}")
                        }
                    }
                }
                
                Log.d("VoiceManager", "Произношу: ${text.take(50)}...")
            } catch (e: Exception) {
                Log.e("VoiceManager", "Ошибка воспроизведения речи", e)
            }
        }
    }
    
    private fun splitIntoSentences(text: String): List<String> {
        // Разбиваем текст на предложения по знакам препинания
        return text.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }
    
    fun stop() {
        try {
            tts?.stop()
            Log.d("VoiceManager", "Речь остановлена")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка остановки TTS", e)
        }
    }
    
    fun setSpeechRate(rate: Float) {
        try {
            tts?.setSpeechRate(rate)
            Log.d("VoiceManager", "Скорость речи установлена: $rate")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка установки скорости речи", e)
        }
    }
    
    fun setPitch(pitch: Float) {
        try {
            tts?.setPitch(pitch)
            Log.d("VoiceManager", "Высота тона установлена: $pitch")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка установки высоты тона", e)
        }
    }
    
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            Log.d("VoiceManager", "TTS завершен")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Ошибка завершения TTS", e)
        }
    }
    
    fun isReady(): Boolean {
        return isInitialized
    }
    
    // Методы для настройки голоса
    fun getAvailableVoices(): List<String> {
        return tts?.voices?.map { it.name } ?: emptyList()
    }
    
    fun setVoice(voiceName: String): Boolean {
        return try {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
