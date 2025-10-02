package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class WebSearchManager {
    
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuery&num=5"
            
            val doc: org.jsoup.nodes.Document = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()
            
            val results = doc.select("h3") // Заголовки результатов поиска
            val descriptions = doc.select("div.VwiC3b") // Описания
            
            if (results.isNotEmpty()) {
                val topResults = results.take(3).mapIndexed { index, element ->
                    val title = element.text()
                    val desc = if (descriptions.size > index) descriptions[index].text() else "Описание недоступно"
                    "• $title\n  $desc"
                }
                
                "🔍 **Результаты поиска по запросу \"$query\":**\n\n${topResults.joinToString("\n\n")}"
            } else {
                "❌ По запросу \"$query\" ничего не найдено. Попробуйте изменить формулировку."
            }
        } catch (e: Exception) {
            "⚠️ Не удалось выполнить поиск. Проверьте подключение к интернету.\n\nОшибка: ${e.message}"
        }
    }
    
    suspend fun getQuickAnswer(question: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedQuestion = URLEncoder.encode(question, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuestion"
            
            val doc: org.jsoup.nodes.Document = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(8000)
                .get()
            
            // Пытаемся найти быстрый ответ (featured snippet)
            val quickAnswer = doc.select("div.ILfuVd, div.XcVN5d, div.zCubwf") // Селекторы для быстрых ответов Google
            
            if (quickAnswer.isNotEmpty()) {
                val answer = quickAnswer.first().text()
                if (answer.length > 50) { // Проверяем что ответ достаточно информативный
                    "🎯 **Быстрый ответ на ваш вопрос:**\n\n$answer"
                } else {
                    "ℹ️ Нашел информацию по вашему вопросу. Вот что удалось найти:\n\n${getSearchSummary(question)}"
                }
            } else {
                "ℹ️ По вашему вопросу найдена информация. ${getSearchSummary(question)}"
            }
        } catch (e: Exception) {
            "❌ Не удалось найти ответ. ${e.message}"
        }
    }
    
    private suspend fun getSearchSummary(query: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val results = searchWeb(query)
            if (results.contains("❌") || results.contains("⚠️")) {
                "Попробуйте уточнить запрос или проверьте подключение к интернету."
            } else {
                // Берем только первый результат для краткости
                results.split("\n\n").take(2).joinToString("\n\n")
            }
        } catch (e: Exception) {
            "Не удалось получить информацию."
        }
    }
    
    suspend fun getNews(topic: String = "новости"): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedTopic = URLEncoder.encode(topic, "UTF-8")
            val newsUrl = "https://news.google.com/search?q=$encodedTopic&hl=ru&gl=RU&ceid=RU:ru"
            
            val doc: org.jsoup.nodes.Document = Jsoup.connect(newsUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            val articles = doc.select("article") // Новостные статьи
            
            if (articles.isNotEmpty()) {
                val newsItems = articles.take(5).map { article ->
                    val titleElement = article.select("h3 a")
                    val title = titleElement.text()
                    val source = article.select("div.SVJrMe a").text()
                    "• $title\n  📰 $source"
                }
                
                "📰 **Последние новости по теме \"$topic\":**\n\n${newsItems.joinToString("\n\n")}"
            } else {
                "❌ Не удалось загрузить новости. Попробуйте позже."
            }
        } catch (e: Exception) {
            "⚠️ Ошибка загрузки новостей: ${e.message}"
        }
    }
}
