package com.example.aiassistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(private val context: Context) {
    
    fun processCommand(message: String): CommandResult {
        val lowerMessage = message.lowercase()
        
        return when {
            // 🎯 СИСТЕМНЫЕ КОМАНДЫ
            lowerMessage.contains("очист") || lowerMessage.contains("удал") -> 
                CommandResult(CommandType.CLEAR_CHAT, "Чат очищен")
            
            lowerMessage.contains("помощь") || lowerMessage.contains("команды") -> 
                CommandResult(CommandType.HELP, getHelpMessage())
            
            lowerMessage.contains("настройки") -> 
                CommandResult(CommandType.SETTINGS, "Открываю настройки")
            
            // 🕐 ВРЕМЯ И ДАТА
            lowerMessage.contains("время") || lowerMessage.contains("который час") -> 
                CommandResult(CommandType.TIME, getCurrentTime())
            
            lowerMessage.contains("дата") || lowerMessage.contains("какое число") -> 
                CommandResult(CommandType.DATE, getCurrentDate())
            
            lowerMessage.contains("день недели") -> 
                CommandResult(CommandType.DAY_OF_WEEK, getCurrentDayOfWeek())
            
            // ⏰ БУДИЛЬНИКИ И ТАЙМЕРЫ
            lowerMessage.contains("будильник") && lowerMessage.contains("установ") -> 
                CommandResult(CommandType.ALARM, setAlarm(message))
            
            lowerMessage.contains("таймер") -> 
                CommandResult(CommandType.TIMER, setTimer(message))
            
            // 📞 ЗВОНКИ И СООБЩЕНИЯ
            lowerMessage.contains("позвони") -> 
                CommandResult(CommandType.CALL, makePhoneCall(message))
            
            lowerMessage.contains("смс") || lowerMessage.contains("сообщение") -> 
                CommandResult(CommandType.SMS, sendSMS(message))
            
            // 🎵 МУЗЫКА И МЕДИА
            lowerMessage.contains("включи музыку") -> 
                CommandResult(CommandType.MUSIC_PLAY, "Включаю музыку")
            
            lowerMessage.contains("пауза") || lowerMessage.contains("останови музыку") -> 
                CommandResult(CommandType.MUSIC_PAUSE, "Музыка на паузе")
            
            lowerMessage.contains("следующая песня") -> 
                CommandResult(CommandType.MUSIC_NEXT, "Переключаю на следующую песню")
            
            // 📍 МЕСТОПОЛОЖЕНИЕ
            lowerMessage.contains("где я") -> 
                CommandResult(CommandType.LOCATION, "Определяю ваше местоположение...")
            
            lowerMessage.contains("маршрут") -> 
                CommandResult(CommandType.NAVIGATION, "Строю маршрут...")
            
            // 📅 КАЛЕНДАРЬ И НАПОМИНАНИЯ
            lowerMessage.contains("создай событие") -> 
                CommandResult(CommandType.CALENDAR, createCalendarEvent(message))
            
            lowerMessage.contains("напомни") -> 
                CommandResult(CommandType.REMINDER, createReminder(message))
            
            // ⚙️ СИСТЕМНЫЕ НАСТРОЙКИ
            lowerMessage.contains("яркость") -> 
                CommandResult(CommandType.BRIGHTNESS, adjustBrightness(message))
            
            lowerMessage.contains("громкость") -> 
                CommandResult(CommandType.VOLUME, adjustVolume(message))
            
            // 🔍 ПОИСК
            lowerMessage.contains("найди") || lowerMessage.contains("поиск") -> 
                CommandResult(CommandType.SEARCH, "Выполняю поиск...")
            
            // 🎮 РАЗВЛЕЧЕНИЯ
            lowerMessage.contains("шутка") -> 
                CommandResult(CommandType.JOKE, "Рассказываю шутку...")
            
            lowerMessage.contains("факт") -> 
                CommandResult(CommandType.FACT, "Делюсь интересным фактом...")
            
            // 📚 ОБУЧЕНИЕ
            lowerMessage.contains("перевод") -> 
                CommandResult(CommandType.TRANSLATE, "Выполняю перевод...")
            
            lowerMessage.contains("объясни") -> 
                CommandResult(CommandType.EXPLAIN, "Объясняю концепцию...")
            
            else -> CommandResult(CommandType.UNKNOWN, "")
        }
    }
    
    fun executeSystemCommand(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        return try {
            when {
                lowerMessage.contains("будильник") && hasTimePattern(message) -> {
                    setSystemAlarm(message)
                    true
                }
                lowerMessage.contains("таймер") && hasTimePattern(message) -> {
                    setSystemTimer(message)
                    true
                }
                lowerMessage.contains("событие") -> {
                    createSystemCalendarEvent(message)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getHelpMessage(): String {
        return """
        🎯 **ДОСТУПНЫЕ КОМАНДЫ:**

        🕐 **Время и дата:**
        • "Который час?" - узнать время
        • "Какое число?" - узнать дату  
        • "Какой день недели?" - день недели

        ⏰ **Будильники и таймеры:**
        • "Установи будильник на 7:30"
        • "Поставь таймер на 10 минут"

        📞 **Звонки и сообщения:**
        • "Позвони маме"
        • "Отправь SMS Привет"

        🎵 **Музыка и медиа:**
        • "Включи музыку"
        • "Пауза", "Следующая песня"

        📍 **Местоположение:**
        • "Где я?"
        • "Построй маршрут до работы"

        📅 **Календарь:**
        • "Создай событие встреча в 15:00"
        • "Напомни купить молоко"

        ⚙️ **Система:**
        • "Увеличь яркость"
        • "Уменьши громкость"

        🔍 **Поиск и информация:**
        • "Найди рестораны рядом"
        • "Поиск кошки видео"

        🎮 **Развлечения:**
        • "Расскажи шутку"
        • "Интересный факт"

        📚 **Обучение:**
        • "Перевет hello на русский"
        • "Объясни что такое AI"

        💰 **Финансы:**
        • "Курс доллара"
        • "Конвертируй 100 долларов в рубли"

        🏥 **Здоровье:**
        • "Рассчитай калории"
        • "Рекомендуй тренировку"

        🍳 **Кулинария:**
        • "Рецепт пасты"
        • "Калорийность яблока"

        Просто скажите или напишите команду!
        """.trimIndent()
    }
    
    private fun getCurrentTime(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return "🕐 Сейчас $time"
    }
    
    private fun getCurrentDate(): String {
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        return "📅 Сегодня $date"
    }
    
    private fun getCurrentDayOfWeek(): String {
        val days = listOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        return "📆 Сегодня ${days[dayOfWeek]}"
    }
    
    private fun setAlarm(message: String): String {
        return "⏰ Будильник установлен! (Функция в разработке)"
    }
    
    private fun setTimer(message: String): String {
        return "⏱️ Таймер установлен! (Функция в разработке)"
    }
    
    private fun makePhoneCall(message: String): String {
        return "📞 Выполняю звонок... (Функция в разработке)"
    }
    
    private fun sendSMS(message: String): String {
        return "💬 Отправляю сообщение... (Функция в разработке)"
    }
    
    private fun createCalendarEvent(message: String): String {
        return "📅 Создаю событие в календаре... (Функция в разработке)"
    }
    
    private fun createReminder(message: String): String {
        return "⏰ Создаю напоминание... (Функция в разработке)"
    }
    
    private fun adjustBrightness(message: String): String {
        return "☀️ Настраиваю яркость... (Функция в разработке)"
    }
    
    private fun adjustVolume(message: String): String {
        return "🔊 Настраиваю громкость... (Функция в разработке)"
    }
    
    private fun hasTimePattern(message: String): Boolean {
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")
        return timePattern.containsMatchIn(message)
    }
    
    private fun setSystemAlarm(message: String): Boolean {
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
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
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
    
    private fun setSystemTimer(message: String): Boolean {
        return try {
            val durationPattern = Regex("""(\d+)\s*(минут|минуты|час|часа|часов)""")
            val match = durationPattern.find(message)
            
            if (match != null) {
                val duration = match.groupValues[1].toInt()
                val unit = match.groupValues[2]
                
                val seconds = when {
                    unit.contains("час") -> duration * 3600
                    else -> duration * 60
                }
                
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Таймер от AI помощника")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
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
    
    private fun createSystemCalendarEvent(message: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Событие от AI помощника")
                putExtra(CalendarContract.Events.DESCRIPTION, "Создано через AI помощник")
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
}

enum class CommandType {
    UNKNOWN, CLEAR_CHAT, HELP, SETTINGS, TIME, DATE, DAY_OF_WEEK, 
    ALARM, TIMER, CALL, SMS, MUSIC_PLAY, MUSIC_PAUSE, MUSIC_NEXT,
    LOCATION, NAVIGATION, CALENDAR, REMINDER, BRIGHTNESS, VOLUME,
    SEARCH, JOKE, FACT, TRANSLATE, EXPLAIN
}

data class CommandResult(
    val type: CommandType,
    val message: String,
    val data: Any? = null
)
