package com.example.aiassistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.util.*

class AlarmManager(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val sharedPreferences = context.getSharedPreferences("ai_assistant_alarms", Context.MODE_PRIVATE)
    
    // Внутреннее хранилище для будильников
    private val alarms = mutableListOf<AlarmItem>()
    
    data class AlarmItem(
        val id: Int,
        val time: String,
        val message: String,
        val enabled: Boolean
    )
    
    fun setAlarm(hours: Int, minutes: Int, message: String = "Будильник от AI помощника"): String {
        return try {
            val timeString = String.format("%02d:%02d", hours, minutes)
            val id = System.currentTimeMillis().toInt()
            
            val alarmItem = AlarmItem(id, timeString, message, true)
            alarms.add(alarmItem)
            saveAlarms()
            
            // Создаем отложенный интент для уведомления
            scheduleNotification(hours, minutes, message, id)
            
            "✅ Будильник установлен на $timeString: $message"
        } catch (e: Exception) {
            "❌ Не удалось установить будильник"
        }
    }
    
    fun setTimer(minutes: Int, message: String = "Таймер от AI помощника"): String {
        return try {
            val id = System.currentTimeMillis().toInt()
            val triggerTime = SystemClock.elapsedRealtime() + minutes * 60 * 1000
            
            val intent = Intent(context, TimerReceiver::class.java).apply {
                putExtra("message", message)
                putExtra("id", id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            "✅ Таймер установлен на $minutes минут: $message"
        } catch (e: Exception) {
            "❌ Не удалось установить таймер"
        }
    }
    
    private fun scheduleNotification(hours: Int, minutes: Int, message: String, id: Int) {
        try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hours)
                set(Calendar.MINUTE, minutes)
                set(Calendar.SECOND, 0)
                
                // Если время уже прошло сегодня, устанавливаем на завтра
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("message", message)
                putExtra("id", id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getAlarms(): List<AlarmItem> {
        return alarms.toList()
    }
    
    fun cancelAlarm(id: Int) {
        alarms.removeAll { it.id == id }
        saveAlarms()
        
        // Отменяем pending intent
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    
    fun cancelTimer(id: Int) {
        val intent = Intent(context, TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    
    private fun saveAlarms() {
        val alarmsString = alarms.joinToString("|") { "${it.id},${it.time},${it.message},${it.enabled}" }
        sharedPreferences.edit().putString("alarms", alarmsString).apply()
    }
    
    fun loadAlarms() {
        val alarmsString = sharedPreferences.getString("alarms", "") ?: ""
        if (alarmsString.isNotEmpty()) {
            alarms.clear()
            alarmsString.split("|").forEach { alarmStr ->
                val parts = alarmStr.split(",")
                if (parts.size == 4) {
                    alarms.add(AlarmItem(parts[0].toInt(), parts[1], parts[2], parts[3].toBoolean()))
                }
            }
        }
    }
    
    fun parseTimeFromText(text: String): Pair<Int, Int>? {
        val lowercaseText = text.lowercase()
        
        val patterns = listOf(
            Regex("""(\d{1,2})[:.](\d{1,2})"""),  // 12:30 или 12.30
            Regex("""(\d{1,2})\s*часов?\s*(\d{1,2})?"""),  // 12 часов 30
            Regex("""в\s*(\d{1,2})"""),  // в 12
            Regex("""(\d{1,2})\s*час\s*(\d{1,2})?"""),  // 12 час 30
            Regex("""на\s*(\d{1,2})[.:]?(\d{1,2})?"""),  // на 12.30
            Regex("""(\d{1,2})\s*[.:]\s*(\d{1,2})""")  // 12.30
        )
        
        for (pattern in patterns) {
            val match = pattern.find(lowercaseText)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: continue
                var minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                
                // Корректируем время
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
            Regex("""(\d+)\s*ч"""),      // 2 ч
            Regex("""на\s*(\d+)\s*минут""")  // на 5 минут
        )
        
        for (pattern in patterns) {
            val match = pattern.find(lowercaseText)
            if (match != null) {
                var duration = match.groupValues[1].toIntOrNull() ?: continue
                
                when {
                    lowercaseText.contains("час") || lowercaseText.contains("ч") -> duration *= 60
                    lowercaseText.contains("секунд") -> duration /= 60
                    // минуты остаются как есть
                }
                
                // Ограничиваем разумными пределами
                return duration.coerceIn(1, 1440) // от 1 минуты до 24 часов
            }
        }
        return null
    }
}
