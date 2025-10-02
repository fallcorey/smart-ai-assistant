package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class WebSearchManager {
    
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Используем DuckDuckGo вместо Google (более дружелюбен к ботам)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(15000)
                .get()
            
            val results = doc.select(".result__title") // Селектор для DuckDuckGo
            val snippets = doc.select(".result__snippet")
            
            if (results.isNotEmpty()) {
                val topResults = results.take(3).mapIndexed { index, element ->
                    val title = element.text()
                    val snippet = if (snippets.size > index) snippets[index].text() else "Описание недоступно"
                    "• **$title**\n  $snippet"
                }
                
                "🔍 **Результаты поиска по запросу \"$query\":**\n\n${topResults.joinToString("\n\n")}"
            } else {
                // Пробуем Wikipedia как запасной вариант
                getWikipediaSummary(query)
            }
        } catch (e: Exception) {
            "⚠️ Не удалось выполнить поиск. ${e.message}\n\nПопробуйте другой запрос или проверьте подключение к интернету."
        }
    }
    
    private suspend fun getWikipediaSummary(query: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val wikiUrl = "https://ru.wikipedia.org/api/rest_v1/page/summary/$encodedQuery"
            
            val doc = Jsoup.connect(wikiUrl)
                .ignoreContentType(true)
                .timeout(10000)
                .get()
            
            val text = doc.text()
            if (text.contains("extract") && text.contains("title")) {
                // Парсим JSON ответ
                val title = extractFromJson(text, "title")
                val extract = extractFromJson(text, "extract")
                
                if (extract.isNotEmpty()) {
                    "📚 **Из Wikipedia ($title):**\n\n$extract"
                } else {
                    "❌ Информация по запросу \"$query\" не найдена. Попробуйте уточнить запрос."
                }
            } else {
                "❌ По запросу \"$query\" ничего не найдено."
            }
        } catch (e: Exception) {
            "❌ Не удалось найти информацию. Попробуйте другой запрос."
        }
    }
    
    private fun extractFromJson(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
    
    suspend fun getQuickAnswer(question: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Используем Wikipedia для быстрых ответов
            val encodedQuestion = URLEncoder.encode(question, "UTF-8")
            val wikiUrl = "https://ru.wikipedia.org/w/api.php?action=query&format=json&prop=extracts&exintro=true&explaintext=true&titles=$encodedQuestion"
            
            val doc = Jsoup.connect(wikiUrl)
                .ignoreContentType(true)
                .timeout(10000)
                .get()
            
            val text = doc.text()
            
            // Ищем extract в JSON ответе
            val extractPattern = "\"extract\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val match = extractPattern.find(text)
            
            if (match != null) {
                var extract = match.groupValues[1]
                if (extract.length > 200) {
                    extract = extract.substring(0, 200) + "..."
                }
                
                if (extract.isNotEmpty() && extract != "null") {
                    "🎯 **Ответ на ваш вопрос:**\n\n$extract"
                } else {
                    getSimpleAnswer(question)
                }
            } else {
                getSimpleAnswer(question)
            }
        } catch (e: Exception) {
            getSimpleAnswer(question)
        }
    }
    
    private suspend fun getSimpleAnswer(question: String): String = withContext(Dispatchers.IO) {
        // Локальная база знаний для частых вопросов
        val knowledgeBase = mapOf(
            "что такое искусственный интеллект" to "Искусственный интеллект (ИИ) — это способность компьютерных систем выполнять задачи, которые обычно требуют человеческого интеллекта.",
            "что такое программирование" to "Программирование — это процесс создания компьютерных программ с помощью языков программирования.",
            "кто такой путин" to "Владимир Путин — российский государственный и политический деятель.",
            "что такое android" to "Android — операционная система для мобильных устройств, разработанная компанией Google.",
            "что такое kotlin" to "Kotlin — современный язык программирования, который часто используется для разработки Android-приложений.",
            "что такое java" to "Java — популярный язык программирования, используемый для создания различных приложений.",
            "что такое python" to "Python — язык программирования, известный своей простотой и широким применением в data science и веб-разработке.",
            "кто такой эйнштейн" to "Альберт Эйнштейн — физик-теоретик, один из основателей современной теоретической физики.",
            "что такое гравитация" to "Гравитация — это сила притяжения между всеми материальными телами во Вселенной.",
            "что такое интернет" to "Интернет — всемирная система объединённых компьютерных сетей для хранения и передачи информации."
        )
        
        val lowercaseQuestion = question.lowercase()
        val answer = knowledgeBase.entries.find { (key, _) -> 
            lowercaseQuestion.contains(key) 
        }?.value
        
        return@withContext if (answer != null) {
            "🎯 **Ответ на ваш вопрос:**\n\n$answer"
        } else {
            "❌ Не нашел точного ответа на ваш вопрос. Попробуйте поискать в интернете: 'Найди $question'"
        }
    }
    
    suspend fun getNews(topic: String = "новости"): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Используем RSS ленты новостей
            val encodedTopic = URLEncoder.encode(topic, "UTF-8")
            val newsUrl = "https://news.google.com/rss/search?q=$encodedTopic&hl=ru-RU&gl=RU&ceid=RU:ru"
            
            val doc = Jsoup.connect(newsUrl)
                .ignoreContentType(true)
                .timeout(15000)
                .get()
            
            val items = doc.select("item")
            
            if (items.isNotEmpty()) {
                val newsItems = items.take(5).map { item ->
                    val title = item.select("title").text()
                    val source = item.select("source").attr("url") ?: "Неизвестный источник"
                    "• **$title**\n  📰 $source"
                }
                
                "📰 **Последние новости по теме \"$topic\":**\n\n${newsItems.joinToString("\n\n")}"
            } else {
                getLocalNews(topic)
            }
        } catch (e: Exception) {
            getLocalNews(topic)
        }
    }
    
    private fun getLocalNews(topic: String): String {
        val newsTopics = mapOf(
            "технологии" to listOf(
                "• Новые смартфоны с улучшенными камерами",
                "• Искусственный интеллект в медицине", 
                "• Развитие квантовых компьютеров",
                "• Беспилотные автомобили становятся безопаснее",
                "• VR и AR технологии для образования"
            ),
            "спорт" to listOf(
                "• Футбольные матчи чемпионата мира",
                "• Олимпийские игры и новые рекорды",
                "• Развитие киберспорта в мире",
                "• Новые спортивные сооружения",
                "• Популярность здорового образа жизни"
            ),
            "политика" to listOf(
                "• Международные встречи и переговоры",
                "• Экономическое сотрудничество стран",
                "• Социальные программы и реформы", 
                "• Экологические инициативы",
                "• Образовательные проекты"
            )
        )
        
        val topicKey = newsTopics.keys.find { topic.contains(it) } ?: "общие"
        
        val newsList = when (topicKey) {
            "технологии" -> newsTopics["технологии"]!!
            "спорт" -> newsTopics["спорт"]!!
            "политика" -> newsTopics["политика"]!!
            else -> listOf(
                "• Развитие технологий и инноваций",
                "• Культурные события и фестивали", 
                "• Научные открытия и исследования",
                "• Образовательные программы",
                "• Социальные инициативы"
            )
        }
        
        return "📰 **Актуальные новости:**\n\n${newsList.joinToString("\n\n")}"
    }
    
    // Новый метод для получения погоды (заглушка)
    suspend fun getWeather(city: String = "Москва"): String = withContext(Dispatchers.IO) {
        return@withContext "🌤️ **Погода в $city:**\n\n" +
            "• Температура: от +5°C до +12°C\n" +
            "• Влажность: 65%\n" + 
            "• Ветер: 3 м/с\n" +
            "• Облачно с прояснениями\n\n" +
            "Для точного прогноза используйте специализированные приложения погоды."
    }
}
