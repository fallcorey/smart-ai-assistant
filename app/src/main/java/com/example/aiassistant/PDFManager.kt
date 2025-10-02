package com.example.aiassistant

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PDFManager(private val context: Context, private val knowledgeManager: KnowledgeManager) {
    
    suspend fun learnFromPDF(pdfUri: Uri): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(pdfUri)
            
            if (inputStream != null) {
                // Для простоты будем читать как текстовый файл
                // В реальном приложении нужно использовать библиотеку для парсинга PDF
                val text = readTextFromStream(inputStream)
                inputStream.close()
                
                if (text.isNotEmpty()) {
                    processAndLearnText(text)
                    "✅ Успешно извлек знания из PDF! Обработано ${text.length} символов."
                } else {
                    "❌ Не удалось извлечь текст из PDF файла."
                }
            } else {
                "❌ Не удалось открыть PDF файл."
            }
        } catch (e: Exception) {
            "❌ Ошибка при обработке PDF: ${e.message}"
        }
    }
    
    private fun readTextFromStream(inputStream: InputStream): String {
        return try {
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun processAndLearnText(text: String) {
        // Разбиваем текст на предложения для обучения
        val sentences = text.split('.', '!', '?', '\n')
            .map { it.trim() }
            .filter { it.length > 20 && it.length < 500 } // Фильтруем по длине
        
        var learnedCount = 0
        
        sentences.forEach { sentence ->
            if (sentence.isNotEmpty()) {
                // Создаем вопрос-ответ пары из предложений
                val words = sentence.split(' ').filter { it.length > 3 }
                if (words.size >= 3) {
                    // Берем ключевые слова для вопроса
                    val keyWords = words.take(3).joinToString(" ")
                    knowledgeManager.learn(keyWords, sentence)
                    learnedCount++
                    
                    // Ограничиваем количество изучаемых фактов
                    if (learnedCount >= 50) return@forEach
                }
            }
        }
    }
    
    suspend fun extractKeyFactsFromPDF(pdfUri: Uri): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(pdfUri)
            
            if (inputStream != null) {
                val text = readTextFromStream(inputStream)
                inputStream.close()
                
                if (text.isNotEmpty()) {
                    val facts = extractImportantFacts(text)
                    if (facts.isNotEmpty()) {
                        "📚 **Ключевые факты из PDF:**\n\n" + facts.joinToString("\n\n")
                    } else {
                        "❌ Не удалось извлечь ключевые факты из PDF."
                    }
                } else {
                    "❌ Не удалось прочитать PDF файл."
                }
            } else {
                "❌ Не удалось открыть PDF файл."
            }
        } catch (e: Exception) {
            "❌ Ошибка при анализе PDF: ${e.message}"
        }
    }
    
    private fun extractImportantFacts(text: String): List<String> {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.length > 30 && it.length < 200 }
        
        // Ищем предложения с ключевыми словами (упрощенный подход)
        val keyWords = listOf("определение", "это", "значит", "следовательно", "таким образом", 
                             "важно", "ключевой", "основной", "главный")
        
        return sentences
            .filter { sentence ->
                keyWords.any { keyword -> 
                    sentence.contains(keyword, ignoreCase = true) 
                } || sentence.split(' ').size in 8..20 // Или предложения средней длины
            }
            .take(10) // Ограничиваем количество фактов
    }
}
