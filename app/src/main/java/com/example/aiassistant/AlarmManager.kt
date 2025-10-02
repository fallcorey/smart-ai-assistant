package com.example.aiassistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.*

class AlarmManager(private val context: Context) {
    
    fun setAlarm(hours: Int, minutes: Int, message: String = "Будильник от AI помощника"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hours)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Пытаемся установить без интерфейса
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "Будильник установлен на ${formatTime(hours, minutes)}"
            } else {
                "Открываю приложение Часы для установки будильника на ${formatTime(hours, minutes)}"
            }
        } catch (e: Exception) {
            "Открываю приложение Часы. Установите будильник на ${formatTime(hours, minutes)}"
        }
    }
    
    fun setTimer(seconds: Int, message: String = "Таймер от AI помощника"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Пытаемся установить без интерфейса
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                if (minutes > 0) {
                    "Таймер установлен на $minutes минут${if (remainingSeconds > 0) " $remainingSeconds секунд" else ""}"
                } else {
                    "Таймер установлен на $seconds секунд"
                }
            } else {
                "Открываю приложение Часы для установки таймера"
            }
        } catch (e: Exception) {
            "Открываю приложение Часы. Установите таймер"
        }
    }
    
    fun showAlarms() {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // Игнорируем ошибку
        }
    }
    
    fun showTimers() {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // Игнорируем ошибку
        }
    }
    
    fun parseTimeFromText(text: String): Pair<Int, Int>? {
        val lowercaseText = text.lowercase()
        
        // Паттерны для распознавания времени
        val patterns = listOf(
            Regex("""(\d{1,2})[:.](\d{1,2})"""),  // 12:30 или 12.30
            Regex("""(\d{1,2})\s*часов?\s*(\d{1,2})?"""),  // 12 часов 30
            Regex("""в\s*(\d{1,2})"""),  // в 12
            Regex("""(\d{1,2})\s*час\s*(\d{1,2})?"""),  // 12 час 30
            Regex("""на\s*(\d{1,2})[.:]?(\d{1,2})?""")  // на 12.30
        )
        
        for (pattern in patterns) {
            val match = pattern.find(lowercaseText)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: continue
                var minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                
                // Корректируем время если нужно
                val correctedHours = if (hours in 0..23) hours else hours % 24
                val correctedMinutes = if (minutes in 0..59) minutes else minutes % 60
                
                if (correctedHours in 0..23 && correctedMinutes in 0..59) {
                    return Pair(correctedHours, correctedMinutes)
                }
            }
        }
        return null
    }
    
    fun parseDurationFromText(text: String): Int? {
        val lowercaseText = text.lowercase()
        
        val patterns = listOf(
            Regex("""(\d+)\s*минут"""),  // 5 минут
            Regex("""(\d+)\s*час"""),    // 1 час
            Regex("""(\d+)\s*часов"""),  // 2 часов
            Regex("""(\d+)\s*секунд"""), // 30 секунд
            Regex("""таймер\s*на\s*(\d+)"""),  // таймер на 5
            Regex("""(\d+)\s*мин"""),    // 5 мин
            Regex("""(\d+)\s*ч""")       // 2 ч
        )
        
        for (pattern in patterns) {
            val match = pattern.find(lowercaseText)
            if (match != null) {
                var duration = match.groupValues[1].toIntOrNull() ?: continue
                
                when {
                    lowercaseText.contains("час") || lowercaseText.contains("ч") -> duration *= 3600
                    lowercaseText.contains("минут") || lowercaseText.contains("мин") -> duration *= 60
                    // секунды остаются как есть
                }
                
                // Ограничиваем максимальное время 24 часами
                return duration.coerceAtMost(86400)
            }
        }
        return null
    }
    
    private fun formatTime(hours: Int, minutes: Int): String {
        return String.format("%02d:%02d", hours, minutes)
    }
    
    // Новый метод для создания напоминаний (простых текстовых)
    fun createReminder(message: String, delayMinutes: Int = 0): String {
        return if (delayMinutes > 0) {
            "Напоминание установлено: '$message' через $delayMinutes минут"
        } else {
            "Запомнил: $message"
        }
    }
}
