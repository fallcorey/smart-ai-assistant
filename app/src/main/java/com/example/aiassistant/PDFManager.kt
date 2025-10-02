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
            
            // Открываем файл в IO потоке
            val inputStream = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(pdfUri)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение файла", 20, "Читаю содержимое PDF..."))
                
                // Читаем текст в IO потоке
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        "Ошибка чтения: ${e.message}"
                    }
                }
                
                // Закрываем поток
                withContext(Dispatchers.IO) {
                    try {
                        inputStream.close()
                    } catch (e: Exception) {
                        // Игнорируем ошибку закрытия
                    }
                }
                
                if (text.isNotEmpty() && !text.startsWith("Ошибка")) {
                    emit(ProcessingProgress("Анализ текста", 40, "Анализирую текст (${text.length} символов)..."))
                    
                    // Обрабатываем текст
                    val sentences = text.split('.', '!', '?', '\n')
                        .map { it.trim() }
                        .filter { it.length > 10 && it.length < 300 }
                    
                    val totalSentences = sentences.size
                    emit(ProcessingProgress("Извлечение знаний", 60, "Найдено $totalSentences предложений..."))
                    
                    var learnedCount = 0
                    val totalToProcess = sentences.size.coerceAtMost(30) // Ограничиваем для скорости
                    
                    // Изучаем предложения
                    sentences.forEachIndexed { index, sentence ->
                        if (learnedCount < 30 && sentence.isNotEmpty()) {
                            val words = sentence.split(' ').filter { it.length > 2 }
                            if (words.size >= 2) {
                                // Создаем простой вопрос из первых слов
                                val keyWords = words.take(2).joinToString(" ")
                                if (keyWords.length > 3) {
                                    knowledgeManager.learn(keyWords, sentence)
                                    learnedCount++
                                }
                            }
                            
                            // Обновляем прогресс каждые 5 предложений
                            if (index % 5 == 0) {
                                val progress = 60 + (index * 30 / totalToProcess)
                                emit(ProcessingProgress(
                                    "Обучение", 
                                    progress.coerceIn(60, 90), 
                                    "Обработано ${index + 1} из $totalToProcess предложений..."
                                ))
                            }
                        }
                    }
                    
                    emit(ProcessingProgress("Завершение", 100, "✅ Успешно обработан PDF! Изучено $learnedCount фактов."))
                } else {
                    emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось извлечь текст из PDF файла."))
                }
            } else {
                emit(ProcessingProgress("Ошибка", 100, "❌ Не удалось открыть PDF файл."))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("Ошибка", 100, "❌ Ошибка при обработке PDF: ${e.message ?: "Неизвестная ошибка"}"))
        }
    }
    
    fun extractKeyFactsFromPDF(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Начало анализа", 0, "Начинаю анализ PDF файла..."))
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            val inputStream = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(pdfUri)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение файла", 30, "Читаю содержимое PDF..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        try {
                            inputStream.close()
                        } catch (e: Exception) {
                            // Игнорируем
                        }
                    }
                }
                
                if (text.isNotEmpty()) {
                    emit(ProcessingProgress("Поиск фактов", 60, "Ищу ключевые факты в тексте..."))
                    
                    val facts = extractImportantFacts(text)
                    if (facts.isNotEmpty()) {
                        emit(ProcessingProgress("Форматирование", 90, "Форматирую найденные факты..."))
                        
                        val result = "📚 **Ключевые факты из PDF:**\n\n" + 
                                   facts.take(5).joinToString("\n\n") +
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
        return try {
            val sentences = text.split('.', '!', '?')
                .map { it.trim() }
                .filter { it.length > 20 && it.length < 150 }
            
            val keyWords = listOf("определение", "это", "значит", "следовательно", "таким образом", 
                                 "важно", "ключевой", "основной", "главный")
            
            sentences
                .filter { sentence ->
                    keyWords.any { keyword -> 
                        sentence.contains(keyword, ignoreCase = true) 
                    } || sentence.split(' ').size in 5..15
                }
                .take(10)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getPDFStats(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Анализ", 0, "Анализирую структуру PDF..."))
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            val inputStream = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(pdfUri)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (inputStream != null) {
                emit(ProcessingProgress("Чтение", 50, "Читаю содержимое..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        try {
                            inputStream.close()
                        } catch (e: Exception) {
                            // Игнорируем
                        }
                    }
                }
                
                if (text.isNotEmpty()) {
                    val charCount = text.length
                    val wordCount = text.split(Regex("\\s+")).count { it.isNotEmpty() }
                    val sentenceCount = text.split('.', '!', '?').count { it.trim().isNotEmpty() }
                    val paragraphCount = text.split("\n\n").count { it.trim().isNotEmpty() }
                    
                    val stats = "📊 **Статистика PDF:**\n\n" +
                               "• Символов: $charCount\n" +
                               "• Слов: $wordCount\n" +
                               "• Предложений: $sentenceCount\n" +
                               "• Абзацев: $paragraphCount"
                    
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
