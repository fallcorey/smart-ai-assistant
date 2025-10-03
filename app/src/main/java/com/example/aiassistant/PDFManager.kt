package com.example.aiassistant

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream
import java.util.*
import kotlin.math.min

class AdvancedPDFManager(private val context: Context, private val knowledgeManager: KnowledgeManager) {
    
    data class ProcessingProgress(
        val step: String,
        val progress: Int,
        val message: String
    )
    
    data class BookContent(
        val title: String,
        val chapters: List<Chapter>,
        val keyConcepts: Map<String, List<String>>,
        val summary: String,
        val rawText: String
    )
    
    data class Chapter(
        val title: String,
        val content: String,
        val paragraphs: List<String>,
        val keyPoints: List<String>
    )
    
    private var currentBook: BookContent? = null
    private val bookMemory = mutableMapOf<String, BookContent>()
    
    fun learnFromPDF(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("📖 Начало обработки", 0, "Открываю PDF файл..."))
        
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
                emit(ProcessingProgress("📖 Чтение файла", 20, "Извлекаю текст из PDF..."))
                
                val text = withContext(Dispatchers.IO) {
                    try {
                        inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        ""
                    } finally {
                        try {
                            inputStream.close()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                
                if (text.isNotEmpty() && text.length > 100) {
                    emit(ProcessingProgress("🔍 Анализ структуры", 40, "Анализирую структуру книги..."))
                    
                    // Анализируем книгу
                    val book = analyzeBookStructure(text)
                    currentBook = book
                    bookMemory[book.title] = book
                    
                    emit(ProcessingProgress("🧠 Извлечение знаний", 60, "Извлекаю ключевые концепции..."))
                    
                    // Извлекаем ключевые знания
                    val concepts = extractKeyConcepts(book)
                    
                    emit(ProcessingProgress("💾 Сохранение", 80, "Сохраняю знания в базу..."))
                    
                    // Сохраняем в базу знаний
                    saveBookToKnowledge(book, concepts)
                    
                    val stats = """
                        ✅ Книга успешно обработана!
                        
                        📊 Статистика:
                        • Название: ${book.title}
                        • Глав: ${book.chapters.size}
                        • Концепций: ${concepts.size}
                        • Символов: ${text.length}
                        
                        Теперь вы можете задавать вопросы по содержанию книги!
                        Примеры вопросов:
                        • "О чем эта книга?"
                        • "Какие основные идеи в главе 1?"
                        • "Объясни концепцию [название концепции]"
                        • "Кто главные герои?"
                        • "В чем основная мысль книги?"
                    """.trimIndent()
                    
                    emit(ProcessingProgress("🎉 Завершение", 100, stats))
                } else {
                    emit(ProcessingProgress("❌ Ошибка", 100, "Не удалось извлечь достаточное количество текста из PDF"))
                }
            } else {
                emit(ProcessingProgress("❌ Ошибка", 100, "Не удалось открыть PDF файл"))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("❌ Ошибка", 100, "Ошибка обработки: ${e.message}"))
        }
    }
    
    private fun analyzeBookStructure(text: String): BookContent {
        val lines = text.lines()
        val title = extractTitle(lines) ?: "Неизвестная книга"
        
        // Разбиваем на главы
        val chapters = extractChapters(text)
        
        // Создаем summary
        val summary = generateSummary(text)
        
        return BookContent(
            title = title,
            chapters = chapters,
            keyConcepts = emptyMap(),
            summary = summary,
            rawText = text
        )
    }
    
    private fun extractTitle(lines: List<String>): String? {
        // Ищем заголовок в первых 10 строках
        for (i in 0 until min(10, lines.size)) {
            val line = lines[i].trim()
            if (line.length in 10..100 && 
                line.any { it.isUpperCase() } && 
                line.split(" ").size in 2..10) {
                return line
            }
        }
        return null
    }
    
    private fun extractChapters(text: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val chapterPatterns = listOf(
            Regex("Глава\\s+\\d+", RegexOption.IGNORE_CASE),
            Regex("Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            Regex("\\d+\\.\\s+[А-Я].+"),
            Regex("\\n[А-Я][А-ЯА-Я\\s]{10,}\\n")
        )
        
        val sections = text.split(Regex("\\n\\s*\\n"))
        var currentChapter = "Введение"
        var currentContent = StringBuilder()
        
        sections.forEach { section ->
            if (section.trim().length > 50) {
                val isChapterStart = chapterPatterns.any { it.containsMatchIn(section) }
                
                if (isChapterStart && currentContent.isNotEmpty()) {
                    // Сохраняем предыдущую главу
                    chapters.add(createChapter(currentChapter, currentContent.toString()))
                    currentChapter = section.trim()
                    currentContent = StringBuilder()
                }
                
                currentContent.append(section).append("\n\n")
            }
        }
        
        // Добавляем последнюю главу
        if (currentContent.isNotEmpty()) {
            chapters.add(createChapter(currentChapter, currentContent.toString()))
        }
        
        return chapters.take(20) // Ограничиваем количество глав
    }
    
    private fun createChapter(title: String, content: String): Chapter {
        val paragraphs = content.split(Regex("\\n\\s*\\n"))
            .filter { it.trim().length > 20 }
            .take(50)
        
        val keyPoints = extractKeyPoints(content)
        
        return Chapter(
            title = title,
            content = content,
            paragraphs = paragraphs,
            keyPoints = keyPoints
        )
    }
    
    private fun extractKeyPoints(text: String): List<String> {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.length in 30..200 }
        
        val keyWords = listOf("важно", "ключевой", "основной", "главный", "следовательно", 
                             "таким образом", "вывод", "результат", "заключение")
        
        return sentences
            .filter { sentence ->
                keyWords.any { keyword -> 
                    sentence.contains(keyword, ignoreCase = true) 
                } || sentence.split(' ').size in 8..25
            }
            .take(10)
    }
    
    private fun generateSummary(text: String): String {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.length > 20 }
        
        val importantSentences = sentences
            .filter { 
                it.split(' ').size in 10..30 &&
                it.any { char -> char.isUpperCase() } &&
                !it.contains("http") 
            }
            .take(5)
        
        return if (importantSentences.size >= 3) {
            importantSentences.joinToString(". ") + "."
        } else {
            "Эта книга содержит важную информацию по выбранной теме."
        }
    }
    
    private fun extractKeyConcepts(book: BookContent): Map<String, List<String>> {
        val concepts = mutableMapOf<String, List<String>>()
        
        // Извлекаем концепции из глав
        book.chapters.forEach { chapter ->
            val chapterConcepts = extractConceptsFromText(chapter.content)
            concepts[chapter.title] = chapterConcepts
        }
        
        return concepts
    }
    
    private fun extractConceptsFromText(text: String): List<String> {
        val words = text.split(' ', ',', '.', '!', '?')
            .filter { it.length > 4 }
            .map { it.toLowerCase(Locale.getDefault()) }
        
        val wordFrequency = mutableMapOf<String, Int>()
        words.forEach { word ->
            wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
        }
        
        return wordFrequency.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }
    }
    
    private fun saveBookToKnowledge(book: BookContent, concepts: Map<String, List<String>>) {
        // Сохраняем основную информацию о книге
        knowledgeManager.learn("о чем книга ${book.title}", book.summary)
        knowledgeManager.learn("тема книги ${book.title}", book.summary)
        knowledgeManager.learn("основная идея ${book.title}", book.summary)
        
        // Сохраняем информацию о главах
        book.chapters.forEachIndexed { index, chapter ->
            knowledgeManager.learn("глава ${index + 1} ${book.title}", chapter.title + ": " + chapter.keyPoints.take(3).joinToString())
            knowledgeManager.learn("содержание главы ${index + 1} ${book.title}", chapter.content.take(500))
        }
        
        // Сохраняем ключевые концепции
        concepts.forEach { (chapter, conceptList) ->
            conceptList.forEach { concept ->
                val explanation = findConceptExplanation(concept, book.rawText)
                if (explanation.isNotEmpty()) {
                    knowledgeManager.learn("что такое $concept", explanation)
                    knowledgeManager.learn("определение $concept", explanation)
                    knowledgeManager.learn("$concept в книге ${book.title}", explanation)
                }
            }
        }
    }
    
    private fun findConceptExplanation(concept: String, text: String): String {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.contains(concept, ignoreCase = true) && it.length in 30..300 }
        
        return sentences.take(2).joinToString(". ") + "."
    }
    
    fun askAboutBook(question: String): String {
        val book = currentBook ?: return "Сначала загрузите PDF файл с книгой."
        
        return when {
            question.contains("о чем", ignoreCase = true) || 
            question.contains("тема", ignoreCase = true) -> {
                "📖 ${book.title}\n\n${book.summary}"
            }
            
            question.contains("глава", ignoreCase = true) -> {
                handleChapterQuestion(question, book)
            }
            
            question.contains("что такое", ignoreCase = true) ||
            question.contains("определение", ignoreCase = true) ||
            question.contains("объясни", ignoreCase = true) -> {
                handleConceptQuestion(question, book)
            }
            
            question.contains("основн", ignoreCase = true) -> {
                "📚 Основные идеи книги \"${book.title}\":\n\n" +
                book.chapters.flatMap { it.keyPoints }.take(5).joinToString("\n• ") { "• $it" }
            }
            
            question.contains("персонаж", ignoreCase = true) ||
            question.contains("герой", ignoreCase = true) -> {
                "🎭 Персонажи книги:\n\n" +
                extractCharacters(book.rawText).joinToString("\n• ") { "• $it" }
            }
            
            else -> {
                // Семантический поиск по содержанию
                semanticSearch(question, book)
            }
        }
    }
    
    private fun handleChapterQuestion(question: String, book: BookContent): String {
        val chapterPattern = Regex("глава\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val match = chapterPattern.find(question)
        
        return if (match != null) {
            val chapterNum = match.groupValues[1].toIntOrNull()
            if (chapterNum != null && chapterNum in 1..book.chapters.size) {
                val chapter = book.chapters[chapterNum - 1]
                "📑 ${chapter.title}\n\n" +
                "Ключевые моменты:\n" +
                chapter.keyPoints.take(5).joinToString("\n• ") { "• $it" } +
                "\n\nСодержание: ${chapter.content.take(300)}..."
            } else {
                "Глава $chapterNum не найдена. В книге ${book.chapters.size} глав."
            }
        } else {
            "Список глав:\n" +
            book.chapters.take(10).mapIndexed { index, chapter -> 
                "${index + 1}. ${chapter.title.take(50)}..."
            }.joinToString("\n")
        }
    }
    
    private fun handleConceptQuestion(question: String, book: BookContent): String {
        val concept = question
            .replace("что такое", "", ignoreCase = true)
            .replace("определение", "", ignoreCase = true)
            .replace("объясни", "", ignoreCase = true)
            .replace("книг[аеи]", "", ignoreCase = true)
            .trim()
        
        if (concept.length < 3) {
            return "Уточните, о какой концепции вы хотите узнать?"
        }
        
        // Ищем объяснение в тексте
        val explanation = findConceptExplanation(concept, book.rawText)
        
        return if (explanation.isNotEmpty()) {
            "📚 Концепция \"$concept\" в книге \"${book.title}\":\n\n$explanation"
        } else {
            "Информация о \"$concept\" не найдена в книге. Попробуйте задать вопрос по-другому."
        }
    }
    
    private fun extractCharacters(text: String): List<String> {
        // Простой алгоритм извлечения возможных персонажей
        val lines = text.lines()
        val potentialCharacters = mutableSetOf<String>()
        
        lines.forEach { line ->
            val words = line.split(' ')
            words.forEach { word ->
                if (word.length > 3 && word[0].isUpperCase() && !word[0].isLowerCase()) {
                    if (word !in listOf("Глава", "Chapter", "Книга", "Book")) {
                        potentialCharacters.add(word)
                    }
                }
            }
        }
        
        return potentialCharacters.take(10).toList()
    }
    
    private fun semanticSearch(question: String, book: BookContent): String {
        val questionWords = question.toLowerCase().split(' ').filter { it.length > 3 }
        
        // Ищем релевантные фрагменты
        val relevantChunks = findRelevantTextChunks(questionWords, book.rawText)
        
        return if (relevantChunks.isNotEmpty()) {
            "🔍 По вашему вопросу в книге найдено:\n\n" +
            relevantChunks.take(3).joinToString("\n\n") { "• $it" } +
            "\n\nМожете уточнить вопрос для более точного ответа."
        } else {
            "Информация по вашему вопросу не найдена в книге. Попробуйте переформулировать вопрос или спросите о конкретной теме из книги."
        }
    }
    
    private fun findRelevantTextChunks(questionWords: List<String>, text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .filter { it.length in 50..500 }
        
        return paragraphs.filter { paragraph ->
            val paragraphLower = paragraph.toLowerCase()
            questionWords.count { word -> paragraphLower.contains(word) } >= 1
        }.take(5)
    }
    
    fun getCurrentBookTitle(): String {
        return currentBook?.title ?: "Книга не загружена"
    }
    
    fun hasBook(): Boolean {
        return currentBook != null
    }
}
