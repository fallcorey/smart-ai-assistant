package com.example.aiassistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.*

class AlarmManager(private val context: Context) {
    
    fun setAlarm(hours: Int, minutes: Int, message: String = "Будильник от AI помощника"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hours)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun setTimer(seconds: Int, message: String = "Таймер от AI помощника"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun parseTimeFromText(text: String): Pair<Int, Int>? {
        val patterns = listOf(
            Regex("""(\d{1,2})[:.](\d{1,2})"""),  // 12:30 или 12.30
            Regex("""(\d{1,2})\s*часов?\s*(\d{1,2})?"""),  // 12 часов 30
            Regex("""в\s*(\d{1,2})""")  // в 12
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text.lowercase())
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: continue
                var minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                
                // Проверяем корректность времени
                if (hours in 0..23 && minutes in 0..59) {
                    return Pair(hours, minutes)
                }
            }
        }
        return null
    }
    
    fun parseDurationFromText(text: String): Int? {
        val patterns = listOf(
            Regex("""(\d+)\s*минут"""),  // 5 минут
            Regex("""(\d+)\s*час"""),    // 1 час
            Regex("""(\d+)\s*секунд"""), // 30 секунд
            Regex("""таймер\s*на\s*(\d+)""")  // таймер на 5
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text.lowercase())
            if (match != null) {
                var duration = match.groupValues[1].toIntOrNull() ?: continue
                
                when {
                    text.contains("час") -> duration *= 3600
                    text.contains("минут") -> duration *= 60
                    // секунды остаются как есть
                }
                
                return duration
            }
        }
        return null
    }
}
