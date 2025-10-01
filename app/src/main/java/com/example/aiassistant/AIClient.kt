package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .build()
    
    suspend fun getAIResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            // Пока используем локальные ответы, но структура для API готова
            return@withContext getLocalAIResponse(userMessage)
        } catch (e: Exception) {
            return@withContext "Извините, произошла ошибка. Пожалуйста, попробуйте позже."
        }
    }
    
    private suspend fun getLocalAIResponse(userMessage: String): String {
        // Имитируем задержку сети
        kotlinx.coroutines.delay(1000)
        
        val message = userMessage.lowercase()
        
        return when {
            // Приветствия
            message.contains("привет") || message.contains("здравствуй") -> 
                "Привет! Я ваш AI помощник. Рад вас видеть! Чем могу помочь?"
            
            // Вопросы о состоянии
            message.contains("как дела") || message.contains("как ты") -> 
                "У меня всё отлично! Готов помогать вам с любыми вопросами. А как ваши дела?"
            
            // Благодарности
            message.contains("спасибо") || message.contains("благодарю") -> 
                "Пожалуйста! Всегда рад помочь. Обращайтесь, если понадобится помощь!"
            
            // Время
            message.contains("время") || message.contains("который час") -> {
                val time = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "Сейчас $time"
            }
            
            // Дата
            message.contains("дата") || message.contains("какое число") || message.contains("число") -> {
                val date = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                "Сегодня $date"
            }
            
            // Погода
            message.contains("погода") -> 
                "К сожалению, у меня нет доступа к актуальным данным о погоде. " +
                "Рекомендую использовать специализированные приложения для точного прогноза."
            
            // Помощь
            message.contains("помощь") || message.contains("команды") || message.contains("что ты умеешь") -> 
                """Я могу:
                • Отвечать на вопросы
                • Поддерживать беседу  
                • Сообщать время и дату
                • Работать с голосовым вводом
                • Обрабатывать базовые команды
                
                Просто напишите или скажите что-нибудь!"""
            
            // Поиск
            message.contains("найди") || message.contains("поиск") -> 
                "Для поиска в интернете рекомендую использовать браузер или специализированные приложения."
            
            // Настройки
            message.contains("настрой") || message.contains("параметр") -> 
                "Настройки приложения можно изменить в меню настроек вашего устройства."
            
            // Прощания
            message.contains("пока") || message.contains("до свидания") -> 
                "До свидания! Было приятно пообщаться. Возвращайтесь, если понадобится помощь!"
            
            // Сложные вопросы - пытаемся дать осмысленный ответ
            message.contains("зачем") || message.contains("почему") || message.contains("как") -> 
                "Это интересный вопрос! К сожалению, мои возможности ограничены. " +
                "Для сложных вопросов рекомендую обратиться к более продвинутым AI системам."
            
            // Любые другие сообщения
            else -> 
                "Я понял ваш вопрос: \"$userMessage\". " +
                "Это интересная тема! К сожалению, мои знания ограничены. " +
                "Для более точных ответов рекомендую настроить подключение к продвинутой AI модели."
        }
    }
    
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        // Заглушка для веб-поиска
        return@withContext "Поиск в интернете для запроса \"$query\" временно недоступен. " +
                          "Рекомендую использовать браузер для поиска информации."
    }
}
