package com.example.aiassistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore

class CommandProcessor(private val context: Context) {
    
    fun processCommand(message: String): String? {
        val lowerMessage = message.lowercase()
        
        return when {
            // Команды приветствия
            lowerMessage.contains("привет") || lowerMessage.contains("здравствуй") -> 
                "Привет! Я ваш умный помощник. Чем могу помочь?"
            
            // Команды времени
            lowerMessage.contains("который час") || lowerMessage.contains("время") -> {
                val time = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "Сейчас $time"
            }
            
            // Команды даты
            lowerMessage.contains("какое число") || lowerMessage.contains("дата") -> {
                val date = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                "Сегодня $date"
            }
            
            // Команды помощи
            lowerMessage.contains("помощь") || lowerMessage.contains("команды") -> 
                """Доступные команды:
                • Приветствие
                • "Который час?" - узнать время
                • "Какое число?" - узнать дату
                • "Поиск [запрос]" - поиск в интернете
                • "Очистить чат" - очистить историю
                
                Также вы можете просто задавать вопросы!"""
            
            // Команды поиска
            lowerMessage.contains("поиск") && lowerMessage.length > 6 -> {
                val query = message.substringAfter("поиск").trim()
                if (query.isNotEmpty()) {
                    null // Отправляем в основной поиск
                } else {
                    "Пожалуйста, укажите что искать. Например: 'поиск погода в Москве'"
                }
            }
            
            // Простые ответы
            lowerMessage.contains("как дела") -> 
                "Всё отлично! Готов помогать вам. А у вас как дела?"
            
            lowerMessage.contains("спасибо") -> 
                "Пожалуйста! Обращайтесь, если нужна помощь."
            
            // Неизвестные команды
            else -> null
        }
    }
    
    private fun setAlarm(message: String): String {
        return try {
            // Простая реализация будильника
            val timePattern = Regex("""(\d{1,2}):(\d{2})""")
            val match = timePattern.find(message)
            
            if (match != null) {
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hours)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Будильник")
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    "Будильник установлен на $hours:$minutes"
                } else {
                    "Приложение будильника не найдено"
                }
            } else {
                "Пожалуйста, укажите время в формате ЧЧ:MM"
            }
        } catch (e: Exception) {
            "Ошибка при установке будильника: ${e.message}"
        }
    }
}
