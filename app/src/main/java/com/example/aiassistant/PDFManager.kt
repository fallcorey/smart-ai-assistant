package com.example.aiassistant

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream

class PDFManager(private val context: Context, private val knowledgeManager: KnowledgeManager) {
    
    data class ProcessingProgress(
        val step: String,
        val progress: Int,
        val message: String
    )
    
    fun learnFromPDF(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Начало обработки", 0, "Открываю PDF файл..."))
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            // Все IO операции в withContext
            val inputStream = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(pdfUri)
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение файла", 20, "Читаю содержимое PDF..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        inputStream.close()
                    }
                }
                
                if (text.isNotEmpty()) {
                    emit(ProcessingProgress("Анализ текста", 40, "Анализирую текст (${text.length} символов)..."))
                    
                    val totalSentences = text.split('.', '!', '?', '\n').size
                    emit(ProcessingProgress("Извлечение знаний", 60, "Найдено $totalSentences предложений..."))
                    
                    // Обрабатываем текст и изучаем факты
                    val learnedCount = processAndLearnText(text) { current, total ->
                        val progress = 60 + (current * 30 / total)
                        emit(ProcessingProgress(
                            "Обучение", 
                            progress, 
                            "Изучаю предложение $current из $total..."
                        ))
                    }
                    
                    emit(ProcessingProgress("Завершение", 100, "✅ Успешно обработан PDF! Изучено $learnedCount фактов."))
                } else {
                    emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось извлечь текст из PDF файла."))
                }
            } else {
                emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось открыть PDF файл."))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("Ошибка", 100, "❌ Ошибка при обработке PDF: ${e.message}"))
        }
    }
    
    private fun processAndLearnText(text: String, onProgress: ((Int, Int) -> Unit)? = null): Int {
        // Разбиваем текст на предложения для обучения
        val sentences = text.split('.', '!', '?', '\n')
            .map { it.trim() }
            .filter { it.length > 20 && it.length < 500 }
        
        var learnedCount = 0
        val totalToProcess = sentences.size.coerceAtMost(50)
        
        sentences.forEachIndexed { index, sentence ->
            if (learnedCount < 50 && sentence.isNotEmpty()) {
                val words = sentence.split(' ').filter { it.length > 3 }
                if (words.size >= 3) {
                    val keyWords = words.take(3).joinToString(" ")
                    knowledgeManager.learn(keyWords, sentence)
                    learnedCount++
                    
                    onProgress?.invoke(index + 1, totalToProcess)
                }
            }
        }
        
        return learnedCount
    }
    
    fun extractKeyFactsFromPDF(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Начало анализа", 0, "Начинаю анализ PDF файла..."))
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            val inputStream = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(pdfUri)
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение файла", 30, "Читаю содержимое PDF..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        inputStream.close()
                    }
                }
                
                if (text.isNotEmpty()) {
                    emit(ProcessingProgress("Поиск фактов", 60, "Ищу ключевые факты в тексте..."))
                    
                    val facts = extractImportantFacts(text)
                    if (facts.isNotEmpty()) {
                        emit(ProcessingProgress("Форматирование", 90, "Форматирую найденные факты..."))
                        
                        val result = "📚 **Ключевые факты из PDF:**\n\n" + 
                                   facts.take(10).joinToString("\n\n") +
                                   "\n\n*Найдено ${facts.size} ключевых фактов*"
                        
                        emit(ProcessingProgress("Завершение", 100, result))
                    } else {
                        emit(ProcessingProgress("Завершение", 100, "❌ Не удалось извлечь ключевые факты из PDF."))
                    }
                } else {
                    emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось прочитать PDF файл."))
                }
            } else {
                emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось открыть PDF файл."))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("Ошибка", 100, "❌ Ошибка при анализе PDF: ${e.message}"))
        }
    }
    
    private fun extractImportantFacts(text: String): List<String> {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.length > 30 && it.length < 200 }
        
        val keyWords = listOf("определение", "это", "значит", "следовательно", "таким образом", 
                             "важно", "ключевой", "основной", "главный", "вывод", "результат")
        
        return sentences
            .filter { sentence ->
                keyWords.any { keyword -> 
                    sentence.contains(keyword, ignoreCase = true) 
                } || sentence.split(' ').size in 8..20
            }
            .take(15)
    }
    
    fun getPDFStats(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Анализ", 0, "Анализирую структуру PDF..."))
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            val inputStream = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(pdfUri)
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение", 50, "Читаю содержимое..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        inputStream.close()
                    }
                }
                
                if (text.isNotEmpty()) {
                    val charCount = text.length
                    val wordCount = text.split(Regex("\\s+")).size
                    val sentenceCount = text.split('.', '!', '?').size
                    val paragraphCount = text.split("\n\n").size
                    
                    val stats = "📊 **Статистика PDF:**\n\n" +
                               "• Символов: $charCount\n" +
                               "• Слов: $wordCount\n" +
                               "• Предложений: $sentenceCount\n" +
                               "• Абзацев: $paragraphCount\n" +
                               "• Плотность информации: ${"%.1f".format(wordCount.toDouble() / sentenceCount)} слов/предложение"
                    
                    emit(ProcessingProgress("Завершение", 100, stats))
                } else {
                    emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось прочитать PDF файл."))
                }
            } else {
                emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось открыть PDF файл."))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("Ошибка", 100, "❌ Ошибка при анализе: ${e.message}"))
        }
    }
}
