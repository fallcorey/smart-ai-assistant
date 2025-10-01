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
                """🎯 **Доступные команды:**
                
                🕐 **Время и дата:**
                • "Который час?" - узнать время
                • "Какое число?" - узнать дату
                
                🎤 **Голосовые команды:**
                • Нажмите кнопку микрофона для голосового ввода
                
                💬 **Общение:**
                • Просто задавайте вопросы
                • "Как дела?" - узнать мое состояние
                
                🧹 **Управление:**
                • "Очистить чат" - очистить историю
                
                Просто напишите или скажите команду!"""
            
            // Команды очистки
            lowerMessage.contains("очист") || lowerMessage.contains("удал") -> 
                null // Обрабатывается в MainActivity
            
            // Неизвестные команды
            else -> null
        }
    }
    
    fun executeSystemCommand(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("будильник") && lowerMessage.contains("установ") -> {
                setAlarm(message)
                true
            }
            else -> false
        }
    }
    
    private fun setAlarm(message: String): Boolean {
        return try {
            val timePattern = Regex("""(\d{1,2}):(\d{2})""")
            val match = timePattern.find(message)
            
            if (match != null) {
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hours)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Будильник от AI помощника")
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
