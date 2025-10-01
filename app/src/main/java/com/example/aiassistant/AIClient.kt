// ВСТАВЬТЕ ЭТОТ КОД В AIClient.kt
package com.example.aiassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AIClient {
    
    companion object {
        // Для локальной модели (измените на ваш URL)
        private const val LOCAL_AI_URL = "http://localhost:8080"
        
        // Для онлайн моделей (резервный вариант)
        private const val ONLINE_AI_URL = "https://api.openai.com/v1"
        
        // Для веб-поиска
        private const val SEARCH_URL = "https://www.google.com/search?q="
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    suspend fun getAIResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            // Сначала пробуем локальную модель
            try {
                val localResponse = getLocalAIResponse(userMessage)
                if (localResponse.isNotEmpty()) {
                    return@withContext localResponse
                }
            } catch (e: Exception) {
                // Если локальная модель не доступна, используем простые правила
                return@withContext getFallbackResponse(userMessage)
            }
            
            return@withContext getFallbackResponse(userMessage)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Извините, произошла ошибка. Пожалуйста, проверьте подключение к интернету."
        }
    }
    
    private suspend fun getLocalAIResponse(userMessage: String): String {
        val jsonBody = JSONObject().apply {
            put("model", "local-model")
            put("prompt", userMessage)
            put("max_tokens", 500)
            put("temperature", 0.7)
        }
        
        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url("$LOCAL_AI_URL/v1/completions")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            return parseAIResponse(responseBody ?: "")
        }
        
        return ""
    }
    
    private fun parseAIResponse(jsonResponse: String): String {
        return try {
            val jsonObject = JSONObject(jsonResponse)
            val choicesArray = jsonObject.getJSONArray("choices")
            if (choicesArray.length() > 0) {
                val firstChoice = choicesArray.getJSONObject(0)
                firstChoice.getString("text").trim()
            } else {
                "Извините, не могу обработать запрос."
            }
        } catch (e: Exception) {
            "Ошибка при обработке ответа AI."
        }
    }
    
    private fun getFallbackResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            message.contains("привет") || message.contains("здравствуй") -> 
                "Привет! Я ваш AI помощник. Чем могу помочь?"
            
            message.contains("как дела") -> 
                "У меня всё отлично! Готов помочь вам с любыми вопросами."
            
            message.contains("спасибо") -> 
                "Пожалуйста! Обращайтесь, если понадобится помощь."
            
            message.contains("погода") -> 
                "Для информации о погоде рекомендую использовать специализированные приложения или поиск в интернете."
            
            message.contains("время") -> 
                "Текущее время: ${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
            
            message.contains("помощь") || message.contains("команды") -> 
                "Доступные команды:\n• Задать вопрос\n• Поиск в интернете\n• Голосовой ввод\n• Очистить чат"
            
            else -> 
                "Я понял ваш запрос: \"$userMessage\". " +
                "Для более точных ответов рекомендую настроить локальную AI модель или использовать поиск в интернете."
        }
    }
    
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        try {
            val searchQuery = query.replace(" ", "+")
            val request = Request.Builder()
                .url("$SEARCH_URL$searchQuery")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string()
                return@withContext parseSearchResults(html ?: "", query)
            }
            
            return@withContext "Не удалось выполнить поиск. Проверьте подключение к интернету."
        } catch (e: Exception) {
            return@withContext "Ошибка при поиске: ${e.message}"
        }
    }
    
    private fun parseSearchResults(html: String, query: String): String {
        return try {
            val doc = Jsoup.parse(html)
            val results = doc.select("h3") // Google заголовки результатов
            
            if (results.isNotEmpty()) {
                val topResults = results.take(3).joinToString("\n\n") { result ->
                    result.text()
                }
                "Топ результаты по запросу \"$query\":\n\n$topResults"
            } else {
                "По запросу \"$query\" ничего не найдено."
            }
        } catch (e: Exception) {
            "Найдены результаты по запросу \"$query\". Рекомендую открыть браузер для просмотра."
        }
    }
}
