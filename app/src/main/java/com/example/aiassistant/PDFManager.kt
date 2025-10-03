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

class PDFManager(private val context: Context, private val knowledgeManager: KnowledgeManager) {
    
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
    
    fun learnFromPDF(pdfUri: Uri): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress("Начало обработки", 0, "Открываю PDF файл..."))
        
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
                emit(ProcessingProgress("Чтение файла", 20, "Извлекаю текст из PDF..."))
                
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
                    emit(ProcessingProgress("Анализ структуры", 40, "Анализирую структуру книги..."))
                    
                    // Анализируем книгу
                    val book = analyzeBookStructure(text)
                    currentBook = book
                    
                    emit(ProcessingProgress("Извлечение знаний", 60, "Извлекаю ключевые концепции..."))
                    
                    // Извлекаем ключевые знания
                    val concepts = extractKeyConcepts(book)
                    
                    emit(ProcessingProgress("Сохранение", 80, "Сохраняю знания в базу..."))
                    
                    // Сохраняем в базу знаний
                    saveBookToKnowledge(book, concepts)
                    
                    val stats = """
                        КНИГА УСПЕШНО ОБРАБОТАНА!
                        
                        Статистика:
                        Название: ${book.title}
                        Глав: ${book.chapters.size}
                        Концепций: ${concepts.size}
                        Символов: ${text.length}
                        
                        Теперь вы можете задавать вопросы по содержанию книги!
                        Примеры вопросов:
                        - "О чем эта книга?"
                        - "Какие основные идеи в главе 1?"
                        - "Объясни концепцию [название концепции]"
                        - "Кто главные герои?"
                        - "В чем основная мысль книги?"
                    """.trimIndent()
                    
                    emit(ProcessingProgress("Завершение", 100, stats))
                } else {
                    emit(ProcessingProgress("Ошибка", 100, "Не удалось извлечь достаточное количество текста из PDF"))
                }
            } else {
                emit(ProcessingProgress("Ошибка", 100, "Не удалось открыть PDF файл"))
            }
        } catch (e: Exception) {
            emit(ProcessingProgress("Ошибка", 100, "Ошибка обработки: ${e.message}"))
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
        
        return chapters.take(10) // Ограничиваем количество глав
    }
    
    private fun createChapter(title: String, content: String): Chapter {
        val paragraphs = content.split(Regex("\\n\\s*\\n"))
            .filter { it.trim().length > 20 }
            .take(30)
        
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
            .filter { it.length in 20..150 }
        
        val keyWords = listOf("важно", "ключевой", "основной", "главный", "следовательно", 
                             "таким образом", "вывод", "результат", "заключение")
        
        return sentences
            .filter { sentence ->
                keyWords.any { keyword -> 
                    sentence.contains(keyword, ignoreCase = true) 
                } || sentence.split(' ').size in 5..20
            }
            .take(5)
    }
    
    private fun generateSummary(text: String): String {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.length > 15 }
        
        val importantSentences = sentences
            .filter { 
                it.split(' ').size in 8..25 &&
                it.any { char -> char.isUpperCase() } &&
                !it.contains("http") 
            }
            .take(3)
        
        return if (importantSentences.size >= 2) {
            importantSentences.joinToString(". ") + "."
        } else {
            "Эта книга содержит важную информацию по выбранной теме. Основное содержание посвящено ключевым аспектам предмета обсуждения."
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
            .filter { it.length > 3 && it[0].isUpperCase() }
            .map { it.toLowerCase(Locale.getDefault()) }
            .distinct()
        
        return words.take(10)
    }
    
    private fun saveBookToKnowledge(book: BookContent, concepts: Map<String, List<String>>) {
        // Сохраняем основную информацию о книге
        knowledgeManager.learn("о чем книга ${book.title}", book.summary)
        knowledgeManager.learn("тема книги ${book.title}", book.summary)
        knowledgeManager.learn("основная идея ${book.title}", book.summary)
        
        // Сохраняем информацию о главах
        book.chapters.forEachIndexed { index, chapter ->
            if (index < 5) { // Ограничиваем количество сохраняемых глав
                knowledgeManager.learn("глава ${index + 1} ${book.title}", chapter.title)
                knowledgeManager.learn("содержание главы ${index + 1} ${book.title}", chapter.content.take(300))
            }
        }
        
        // Сохраняем ключевые концепции
        concepts.forEach { (chapter, conceptList) ->
            conceptList.forEach { concept ->
                if (concept.length > 3) {
                    val explanation = findConceptExplanation(concept, book.rawText)
                    if (explanation.isNotEmpty()) {
                        knowledgeManager.learn("что такое $concept", explanation)
                        knowledgeManager.learn("определение $concept", explanation)
                    }
                }
            }
        }
    }
    
    private fun findConceptExplanation(concept: String, text: String): String {
        val sentences = text.split('.', '!', '?')
            .map { it.trim() }
            .filter { it.contains(concept, ignoreCase = true) && it.length in 20..200 }
        
        return if (sentences.isNotEmpty()) {
            sentences.take(1).joinToString(". ")
        } else {
            ""
        }
    }
    
    fun askAboutBook(question: String): String {
        val book = currentBook ?: return "Сначала загрузите PDF файл с книгой."
        
        val lowerQuestion = question.toLowerCase(Locale.getDefault())
        
        return when {
            lowerQuestion.contains("о чем") || 
            lowerQuestion.contains("тема") -> {
                "КНИГА: ${book.title}\n\nКРАТКОЕ СОДЕРЖАНИЕ:\n${book.summary}"
            }
            
            lowerQuestion.contains("глава") -> {
                handleChapterQuestion(question, book)
            }
            
            lowerQuestion.contains("что такое") ||
            lowerQuestion.contains("определение") ||
            lowerQuestion.contains("объясни") -> {
                handleConceptQuestion(question, book)
            }
            
            lowerQuestion.contains("основн") -> {
                "ОСНОВНЫЕ ИДЕИ КНИГИ \"${book.title}\":\n" +
                book.chapters.flatMap { it.keyPoints }.take(3).joinToString("\n- ") { "- $it" }
            }
            
            lowerQuestion.contains("персонаж") ||
            lowerQuestion.contains("герой") -> {
                "ПЕРСОНАЖИ КНИГИ:\n" +
                extractCharacters(book.rawText).joinToString("\n- ") { "- $it" }
            }
            
            else -> {
                // Простой поиск по содержанию
                simpleSearch(question, book)
            }
        }
    }
    
    private fun handleChapterQuestion(question: String, book: BookContent): String {
        val chapterPattern = Regex("глава\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val match = chapterPattern.find(question.toLowerCase(Locale.getDefault()))
        
        return if (match != null) {
            val chapterNum = match.groupValues[1].toIntOrNull()
            if (chapterNum != null && chapterNum in 1..book.chapters.size) {
                val chapter = book.chapters[chapterNum - 1]
                "ГЛАВА $chapterNum: ${chapter.title}\n\n" +
                "Ключевые моменты:\n" +
                chapter.keyPoints.take(3).joinToString("\n- ") { "- $it" } +
                "\n\nСодержание: ${chapter.content.take(250)}..."
            } else {
                "Глава $chapterNum не найдена. В книге ${book.chapters.size} глав."
            }
        } else {
            "СПИСОК ГЛАВ:\n" +
            book.chapters.take(5).mapIndexed { index, chapter -> 
                "${index + 1}. ${chapter.title.take(40)}..."
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
        
        if (concept.length < 2) {
            return "Уточните, о какой концепции вы хотите узнать?"
        }
        
        // Ищем объяснение в тексте
        val explanation = findConceptExplanation(concept, book.rawText)
        
        return if (explanation.isNotEmpty()) {
            "КОНЦЕПЦИЯ \"$concept\" в книге \"${book.title}\":\n\n$explanation"
        } else {
            "Информация о \"$concept\" не найдена в книге. Попробуйте задать вопрос по-другому."
        }
    }
    
    private fun extractCharacters(text: String): List<String> {
        val lines = text.lines()
        val potentialCharacters = mutableSetOf<String>()
        
        lines.take(100).forEach { line ->
            val words = line.split(' ')
            words.forEach { word ->
                if (word.length in 3..15 && word[0].isUpperCase() && !word[0].isLowerCase()) {
                    if (word !in listOf("Глава", "Chapter", "Книга", "Book", "Введение", "Заключение")) {
                        potentialCharacters.add(word)
                    }
                }
            }
        }
        
        return potentialCharacters.take(8).toList()
    }
    
    private fun simpleSearch(question: String, book: BookContent): String {
        val questionWords = question.toLowerCase(Locale.getDefault()).split(' ').filter { it.length > 2 }
        
        if (questionWords.isEmpty()) {
            return "Задайте конкретный вопрос о книге."
        }
        
        // Ищем релевантные фрагменты
        val relevantParagraphs = findRelevantParagraphs(questionWords, book.rawText)
        
        return if (relevantParagraphs.isNotEmpty()) {
            "ПО ВАШЕМУ ВОПРОСУ В КНИГЕ НАЙДЕНО:\n\n" +
            relevantParagraphs.take(2).joinToString("\n\n") { "• $it" }
        } else {
            "Информация по вашему вопросу не найдена в книге. Попробуйте спросить о конкретной теме или концепции из книги."
        }
    }
    
    private fun findRelevantParagraphs(questionWords: List<String>, text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .filter { it.length in 30..400 }
        
        return paragraphs.filter { paragraph ->
            val paragraphLower = paragraph.toLowerCase(Locale.getDefault())
            questionWords.any { word -> paragraphLower.contains(word) }
        }.take(3)
    }
    
    fun getCurrentBookTitle(): String {
        return currentBook?.title ?: "Книга не загружена"
    }
    
    fun hasBook(): Boolean {
        return currentBook != null
    }
}
