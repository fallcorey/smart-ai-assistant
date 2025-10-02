package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            message.contains("погода") -> getWeatherInfo(userMessage)
            
            // 📝 НАПОМИНАНИЯ И ЗАДАЧИ
            message.contains("напомни") || message.contains("напоминание") -> setupReminder(userMessage)
            message.contains("задача") || message.contains("todo") -> manageTasks(userMessage)
            
            // 🎵 МУЗЫКА И РАЗВЛЕЧЕНИЯ
            message.contains("музыка") || message.contains("песня") -> controlMusic(userMessage)
            message.contains("шутка") || message.contains("анекдот") -> tellJoke()
            message.contains("факт") -> tellInterestingFact()
            
            // 📞 КОНТАКТЫ И СООБЩЕНИЯ
            message.contains("позвони") || message.contains("звонок") -> makePhoneCall(userMessage)
            message.contains("смс") || message.contains("сообщение") -> sendSMS(userMessage)
            
            // 📍 НАВИГАЦИЯ И МЕСТОПОЛОЖЕНИЕ
            message.contains("где я") || message.contains("местоположение") -> getCurrentLocation()
            message.contains("маршрут") || message.contains("как доехать") -> getNavigation(userMessage)
            
            // ⚙️ СИСТЕМНЫЕ КОМАНДЫ
            message.contains("будильник") -> setAlarm(userMessage)
            message.contains("таймер") -> setTimer(userMessage)
            message.contains("яркость") -> adjustBrightness(userMessage)
            message.contains("звонок") -> adjustVolume(userMessage)
            
            // 📚 ОБУЧЕНИЕ И ЗНАНИЯ
            message.contains("объясни") || message.contains("что такое") -> explainConcept(userMessage)
            message.contains("перевод") || message.contains("translate") -> translateText(userMessage)
            message.contains("синоним") -> findSynonyms(userMessage)
            
            // 🎮 ИГРЫ И РАЗВЛЕЧЕНИЯ
            message.contains("игра") || message.contains("сыграем") -> startGame(userMessage)
            message.contains("загадай число") -> playNumberGame()
            message.contains("викторина") -> startQuiz()
            
            // 💰 ФИНАНСЫ И КОНВЕРТАЦИЯ
            message.contains("курс") || message.contains("доллар") || message.contains("евро") -> getExchangeRates()
            message.contains("конверт") || message.contains("convert") -> convertCurrency(userMessage)
            
            // 🏥 ЗДОРОВЬЕ И СПОРТ
            message.contains("калории") || message.contains("диета") -> calculateCalories(userMessage)
            message.contains("пульс") || message.contains("давление") -> healthMonitoring(userMessage)
            message.contains("тренировка") -> suggestWorkout()
            
            // 🍳 КУЛИНАРИЯ
            message.contains("рецепт") || message.contains("приготовить") -> getRecipe(userMessage)
            message.contains("калорийность") -> getFoodCalories(userMessage)
            
            // 📖 ЧТЕНИЕ И КНИГИ
            message.contains("книга") || message.contains("почитать") -> recommendBook(userMessage)
            message.contains("цитата") -> getQuote()
            
            // 🎨 ТВОРЧЕСТВО
            message.contains("нарисуй") || message.contains("рисунок") -> generateArt(userMessage)
            message.contains("стих") || message.contains("поэзия") -> generatePoem(userMessage)
            message.contains("история") -> generateStory(userMessage)
            
            // 💼 РАБОТА И ПРОДУКТИВНОСТЬ
            message.contains("встреча") || message.contains("календарь") -> manageCalendar(userMessage)
            message.contains("отчет") || message.contains("документ") -> manageDocuments(userMessage)
            
            // 🛒 ПОКУПКИ
            message.contains("купить") || message.contains("покупк") -> shoppingAssistance(userMessage)
            message.contains("цена") || message.contains("стоимость") -> priceCheck(userMessage)
            
            // 🚗 ТРАНСПОРТ
            message.contains("такси") -> callTaxi(userMessage)
            message.contains("расписание") || message.contains("автобус") -> getTransportSchedule(userMessage)
            
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
    
    private fun getWeatherInfo(location: String): String {
        return "🌤️ Информация о погоде временно недоступна. " +
               "Рекомендую использовать приложение \"Погода\" на вашем устройстве."
    }
    
    private fun setupReminder(message: String): String {
        return "⏰ Функция напоминаний будет добавлена в следующем обновлении. " +
               "Пока можете использовать стандартное приложение \"Часы\"."
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
    
    // Дополнительные функции будут реализованы в следующих шагах
    private fun controlMusic(message: String): String = "🎵 Музыкальные функции будут добавлены скоро!"
    private fun makePhoneCall(message: String): String = "📞 Функция звонков в разработке"
    private fun sendSMS(message: String): String = "💬 SMS функции появятся в обновлении"
    private fun getCurrentLocation(): String = "📍 Определение местоположения временно недоступно"
    private fun setAlarm(message: String): String = "⏰ Будильник можно установить через приложение Часы"
    private fun explainConcept(message: String): String = "📚 Объяснение концепций будет добавлено"
    private fun translateText(message: String): String = "🌍 Переводчик появится в следующей версии"
    private fun getExchangeRates(): String = "💱 Курсы валют временно недоступны"
    private fun suggestWorkout(): String = "🏃‍♂️ Рекомендации по тренировкам скоро появятся"
    private fun getRecipe(message: String): String = "🍳 Поиск рецептов будет добавлен"
    private fun recommendBook(message: String): String = "📖 Рекомендации книг в разработке"
    private fun generateArt(message: String): String = "🎨 Генерация изображений появится в обновлении"
    private fun manageCalendar(message: String): String = "📅 Управление календарем будет доступно"
    private fun shoppingAssistance(message: String): String = "🛒 Помощь с покупками скоро появится"
    private fun callTaxi(message: String): String = "🚕 Вызов такси временно недоступен"
}
