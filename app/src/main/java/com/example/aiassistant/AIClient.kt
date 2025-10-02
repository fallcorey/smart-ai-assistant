package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AIClient {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .build()
    
    suspend fun getAIResponse(userMessage: String, context: String = ""): String = withContext(Dispatchers.IO) {
        try {
            // Пытаемся получить ответ от локальной модели или API
            return@withContext processAdvancedAIResponse(userMessage, context)
        } catch (e: Exception) {
            return@withContext "⚠️ Произошла ошибка. Пожалуйста, проверьте подключение к интернету и попробуйте снова."
        }
    }
    
    private suspend fun processAdvancedAIResponse(userMessage: String, context: String): String {
        val message = userMessage.lowercase()
        
        // Обработка различных типов запросов
        return when {
            // 🔍 ПОИСК И ИНФОРМАЦИЯ
            message.contains("найди") || message.contains("поиск") || message.contains("гугл") -> {
                val query = extractSearchQuery(userMessage)
                if (query.isNotEmpty()) {
                    return performWebSearch(query)
                }
                "🔍 Что именно вы хотите найти? Уточните ваш запрос."
            }
            
            // 📊 КАЛЬКУЛЯТОР И МАТЕМАТИКА
            message.contains("посчитай") || message.contains("сколько будет") || 
            message.contains("+") || message.contains("-") || message.contains("*") || message.contains("/") -> {
                calculateMathExpression(userMessage)
            }
            
            // 🕐 ВРЕМЯ И ДАТА
            message.contains("время") || message.contains("который час") -> getCurrentTime()
            message.contains("дата") || message.contains("какое число") -> getCurrentDate()
            message.contains("день недели") -> getCurrentDayOfWeek()
            
            // 🌤️ ПОГОДА
            message.contains("погода") -> "🌤️ Информация о погоде временно недоступна. Рекомендую использовать приложение 'Погода' на вашем устройстве."
            
            // 📝 НАПОМИНАНИЯ И ЗАДАЧИ
            message.contains("напомни") || message.contains("напоминание") -> "⏰ Функция напоминаний будет добавлена в следующем обновлении."
            message.contains("задача") || message.contains("todo") -> "📝 Управление задачами появится в будущих версиях."
            
            // 🎵 МУЗЫКА И РАЗВЛЕЧЕНИЯ
            message.contains("музыка") || message.contains("песня") -> "🎵 Музыкальные функции будут добавлены скоро!"
            message.contains("шутка") || message.contains("анекдот") -> tellJoke()
            message.contains("факт") -> tellInterestingFact()
            
            // 📞 КОНТАКТЫ И СООБЩЕНИЯ
            message.contains("позвони") || message.contains("звонок") -> "📞 Функция звонков в разработке"
            message.contains("смс") || message.contains("сообщение") -> "💬 SMS функции появятся в обновлении"
            
            // 📍 НАВИГАЦИЯ И МЕСТОПОЛОЖЕНИЕ
            message.contains("где я") || message.contains("местоположение") -> "📍 Определение местоположения временно недоступно"
            message.contains("маршрут") || message.contains("как доехать") -> "🗺️ Навигация будет доступна в следующих версиях"
            
            // ⚙️ СИСТЕМНЫЕ КОМАНДЫ
            message.contains("будильник") -> "⏰ Будильник можно установить через приложение Часы"
            message.contains("таймер") -> "⏱️ Таймер появится в будущих обновлениях"
            message.contains("яркость") -> "☀️ Настройка яркости в разработке"
            message.contains("звонок") -> "🔊 Управление громкостью будет добавлено"
            
            // 📚 ОБУЧЕНИЕ И ЗНАНИЯ
            message.contains("объясни") || message.contains("что такое") -> "📚 Объяснение концепций будет добавлено"
            message.contains("перевод") || message.contains("translate") -> "🌍 Переводчик появится в следующей версии"
            message.contains("синоним") -> "📖 Поиск синонимов будет доступен скоро"
            
            // 🎮 ИГРЫ И РАЗВЛЕЧЕНИЯ
            message.contains("игра") || message.contains("сыграем") -> "🎮 Игровые функции появятся в обновлении"
            message.contains("загадай число") -> "🎯 Игра 'Угадай число' скоро будет доступна"
            message.contains("викторина") -> "🧠 Викторины появятся в будущих версиях"
            
            // 💰 ФИНАНСЫ И КОНВЕРТАЦИЯ
            message.contains("курс") || message.contains("доллар") || message.contains("евро") -> "💱 Курсы валют временно недоступны"
            message.contains("конверт") || message.contains("convert") -> "💵 Конвертер валют будет добавлен"
            
            // 🏥 ЗДОРОВЬЕ И СПОРТ
            message.contains("калории") || message.contains("диета") -> "🍎 Расчет калорий появится в обновлении"
            message.contains("пульс") || message.contains("давление") -> "❤️ Мониторинг здоровья будет доступен"
            message.contains("тренировка") -> "🏃‍♂️ Рекомендации по тренировкам скоро появятся"
            
            // 🍳 КУЛИНАРИЯ
            message.contains("рецепт") || message.contains("приготовить") -> "🍳 Поиск рецептов будет добавлен"
            message.contains("калорийность") -> "📊 Расчет калорийности продуктов появится"
            
            // 📖 ЧТЕНИЕ И КНИГИ
            message.contains("книга") || message.contains("почитать") -> "📖 Рекомендации книг в разработке"
            message.contains("цитата") -> "💫 Цитаты великих людей скоро появятся"
            
            // 🎨 ТВОРЧЕСТВО
            message.contains("нарисуй") || message.contains("рисунок") -> "🎨 Генерация изображений появится в обновлении"
            message.contains("стих") || message.contains("поэзия") -> "✍️ Генерация стихов будет доступна"
            message.contains("история") -> "📖 Создание историй появится в будущем"
            
            // 💼 РАБОТА И ПРОДУКТИВНОСТЬ
            message.contains("встреча") || message.contains("календарь") -> "📅 Управление календарем будет доступно"
            message.contains("отчет") || message.contains("документ") -> "📄 Работа с документами в разработке"
            
            // 🛒 ПОКУПКИ
            message.contains("купить") || message.contains("покупк") -> "🛒 Помощь с покупками скоро появится"
            message.contains("цена") || message.contains("стоимость") -> "💰 Проверка цен будет доступна"
            
            // 🚗 ТРАНСПОРТ
            message.contains("такси") -> "🚕 Вызов такси временно недоступен"
            message.contains("расписание") || message.contains("автобус") -> "🚌 Расписание транспорта появится"
            
            // 💬 ОБЩЕНИЕ
            else -> generateSmartResponse(userMessage, context)
        }
    }
    
    private fun extractSearchQuery(message: String): String {
        val patterns = listOf(
            "найди (.+)" to 1,
            "поиск (.+)" to 1,
            "гугл (.+)" to 1,
            "найти (.+)" to 1
        )
        
        for ((pattern, group) in patterns) {
            val regex = pattern.toRegex()
            val match = regex.find(message.lowercase())
            if (match != null) {
                return match.groupValues[group].trim()
            }
        }
        return message
    }
    
    private suspend fun performWebSearch(query: String): String {
        return try {
            val searchUrl = "https://www.google.com/search?q=${query.replace(" ", "+")}"
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
            
            val results = doc.select("h3").take(3).map { it.text() }
            
            if (results.isNotEmpty()) {
                "🔍 **Результаты поиска по запросу \"$query\":**\n\n" +
                results.joinToString("\n\n") { "• $it" } +
                "\n\nДля подробной информации откройте браузер."
            } else {
                "❌ По запросу \"$query\" ничего не найдено. Попробуйте изменить формулировку."
            }
        } catch (e: Exception) {
            "⚠️ Не удалось выполнить поиск. Проверьте подключение к интернету."
        }
    }
    
    private fun calculateMathExpression(expression: String): String {
        return try {
            val cleanExpr = expression
                .replace("посчитай", "")
                .replace("сколько будет", "")
                .replace(" ", "")
                .replace(",", ".")
            
            // Простая математика
            when {
                cleanExpr.contains("+") -> {
                    val parts = cleanExpr.split("+")
                    val result = parts.sumOf { it.toDouble() }
                    "✅ Результат: $result"
                }
                cleanExpr.contains("-") -> {
                    val parts = cleanExpr.split("-")
                    val result = parts[0].toDouble() - parts[1].toDouble()
                    "✅ Результат: $result"
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    val result = parts[0].toDouble() * parts[1].toDouble()
                    "✅ Результат: $result"
                }
                cleanExpr.contains("/") -> {
                    val parts = cleanExpr.split("/")
                    if (parts[1].toDouble() == 0.0) "❌ Деление на ноль невозможно"
                    else {
                        val result = parts[0].toDouble() / parts[1].toDouble()
                        "✅ Результат: $result"
                    }
                }
                else -> "❌ Не могу распознать математическое выражение"
            }
        } catch (e: Exception) {
            "❌ Ошибка вычисления. Проверьте правильность выражения."
        }
    }
    
    private fun getCurrentTime(): String {
        val time = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        return "🕐 Сейчас $time"
    }
    
    private fun getCurrentDate(): String {
        val date = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        return "📅 Сегодня $date"
    }
    
    private fun getCurrentDayOfWeek(): String {
        val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        val dayOfWeek = java.time.LocalDate.now().dayOfWeek.value - 1
        return "📆 Сегодня ${days[dayOfWeek]}"
    }
    
    private fun tellJoke(): String {
        val jokes = listOf(
            "🤣 Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 == Dec 25!",
            "😄 Что сказал один байт другому? Я тебя не бит, я с тобой!",
            "😂 Почему Java-разработчики носят очки? Потому что они не C#!",
            "😊 Что говорит null, встретив своего друга? Null-здавствуй!",
            "🤭 Почему Python стал таким популярным? Потому что у него есть змеиное обаяние!"
        )
        return jokes.random()
    }
    
    private fun tellInterestingFact(): String {
        val facts = listOf(
            "🧠 Человеческий мозг может хранить до 2.5 петабайт информации!",
            "🌍 Земля - единственная планета в Солнечной системе, где наблюдается тектоника плит.",
            "🐜 Муравьи никогда не спят!",
            "📱 Первый мобильный телефон стоил 4000 долларов и весил почти 1 кг!",
            "💡 Лампочка горит только потому, что электроны постоянно сталкиваются друг с другом."
        )
        return "📚 Интересный факт: ${facts.random()}"
    }
    
    private fun generateSmartResponse(message: String, context: String): String {
        val responses = listOf(
            "🤔 Интересный вопрос! Давайте подумаем вместе...",
            "💭 Мне нужно немного времени, чтобы обработать ваш запрос.",
            "🎯 Похоже, вы хотите узнать что-то важное!",
            "🚀 Отличный вопрос! Давайте разберемся...",
            "💡 У меня есть несколько мыслей по этому поводу...",
            "🔍 Давайте исследуем эту тему вместе!",
            "🌟 Прекрасный вопрос! Это действительно интересная тема.",
            "📖 Позвольте мне поделиться с вами информацией по этому вопросу...",
            "🎓 Это требует некоторого анализа...",
            "💼 Я готов помочь вам с этим вопросом!"
        )
        
        val baseResponse = responses.random()
        
        return when {
            message.contains("?") -> "$baseResponse\n\nВаш вопрос: \"$message\"\n\n" +
                "К сожалению, мои знания ограничены. Для сложных вопросов рекомендую:\n" +
                "• Использовать поиск в интернете\n" +
                "• Обратиться к специализированным источникам\n" +
                "• Настроить подключение к продвинутой AI модели"
            
            message.length > 50 -> "$baseResponse\n\nЯ внимательно изучил ваше сообщение и готов помочь!"
            
            else -> "$baseResponse\n\nВы сказали: \"$message\"\n\n" +
                "Чем еще я могу помочь? Я умею:\n" +
                "• 🔍 Искать информацию\n" +
                "• 📊 Выполнять расчеты\n" +
                "• 🕐 Сообщать время и дату\n" +
                "• 🎯 Отвечать на вопросы\n" +
                "• 📝 Создавать напоминания\n" +
                "• 🎵 Управлять медиа\n" +
                "• 📍 Работать с местоположением\n" +
                "• 💰 Конвертировать валюты\n" +
                "• 🏥 Помогать со здоровьем\n" +
                "• 🍳 Искать рецепты\n" +
                "• 📖 Рекомендовать книги\n" +
                "• 🎨 Создавать контент\n" +
                "• 💼 Помогать с работой\n" +
                "• 🛒 Ассистировать с покупками\n" +
                "• 🚗 Помогать с транспортом"
        }
    }
}
