package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class KnowledgeManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("ai_knowledge", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val knowledgeBase = mutableMapOf<String, MutableList<KnowledgeEntry>>()
    
    data class KnowledgeEntry(
        val question: String,
        val answer: String,
        val confidence: Int = 1,
        val lastUsed: Long = System.currentTimeMillis(),
        val usageCount: Int = 1
    )
    
    init {
        loadKnowledge()
    }
    
    fun learn(question: String, answer: String) {
        val normalizedQuestion = normalizeQuestion(question)
        
        if (knowledgeBase.containsKey(normalizedQuestion)) {
            // Обновляем существующую запись
            val entries = knowledgeBase[normalizedQuestion]!!
            val existingEntry = entries.find { it.answer == answer }
            
            if (existingEntry != null) {
                // Увеличиваем счетчик использования
                entries.remove(existingEntry)
                entries.add(
                    existingEntry.copy(
                        confidence = (existingEntry.confidence + 1).coerceAtMost(10),
                        lastUsed = System.currentTimeMillis(),
                        usageCount = existingEntry.usageCount + 1
                    )
                )
            } else {
                // Добавляем новый вариант ответа
                entries.add(KnowledgeEntry(question, answer))
            }
        } else {
            // Создаем новую запись
            knowledgeBase[normalizedQuestion] = mutableListOf(KnowledgeEntry(question, answer))
        }
        
        saveKnowledge()
    }
    
    fun findAnswer(question: String): KnowledgeEntry? {
        val normalizedQuestion = normalizeQuestion(question)
        
        return knowledgeBase[normalizedQuestion]?.let { entries ->
            // Возвращаем самый уверенный и часто используемый ответ
            entries.maxByOrNull { it.confidence * it.usageCount }
        }
    }
    
    fun improveAnswer(question: String, userFeedback: Boolean) {
        val normalizedQuestion = normalizeQuestion(question)
        val entries = knowledgeBase[normalizedQuestion] ?: return
        
        val bestEntry = entries.maxByOrNull { it.confidence * it.usageCount }
        bestEntry?.let { entry ->
            entries.remove(entry)
            val newConfidence = if (userFeedback) {
                (entry.confidence + 1).coerceAtMost(10)
            } else {
                (entry.confidence - 1).coerceAtLeast(1)
            }
            entries.add(entry.copy(confidence = newConfidence))
            saveKnowledge()
        }
    }
    
    fun getAllKnowledge(): Map<String, List<KnowledgeEntry>> {
        return knowledgeBase.toMap()
    }
    
    fun forget(question: String) {
        val normalizedQuestion = normalizeQuestion(question)
        knowledgeBase.remove(normalizedQuestion)
        saveKnowledge()
    }
    
    fun clearAllKnowledge() {
        knowledgeBase.clear()
        saveKnowledge()
    }
    
    fun getKnowledgeStats(): String {
        val totalQuestions = knowledgeBase.size
        val totalAnswers = knowledgeBase.values.sumOf { it.size }
        val totalUsage = knowledgeBase.values.flatten().sumOf { it.usageCount }
        
        return "📊 **Статистика знаний:**\n\n" +
               "• Изучено вопросов: $totalQuestions\n" +
               "• Всего ответов: $totalAnswers\n" +
               "• Общее использование: $totalUsage раз\n" +
               "• Уровень обучения: ${calculateLearningLevel()}%"
    }
    
    private fun calculateLearningLevel(): Int {
        val totalEntries = knowledgeBase.values.flatten().size
        return (totalEntries * 100 / 50).coerceAtMost(100) // Максимум 100%
    }
    
    private fun normalizeQuestion(question: String): String {
        return question.lowercase(Locale.getDefault())
            .replace(Regex("[^а-яa-z0-9\\s]"), "") // Убираем спецсимволы
            .replace(Regex("\\s+"), " ") // Убираем лишние пробелы
            .trim()
    }
    
    private fun saveKnowledge() {
        val json = gson.toJson(knowledgeBase)
        sharedPreferences.edit().putString("knowledge_base", json).apply()
    }
    
    private fun loadKnowledge() {
        val json = sharedPreferences.getString("knowledge_base", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, MutableList<KnowledgeEntry>>>() {}.type
            val loaded = gson.fromJson<MutableMap<String, MutableList<KnowledgeEntry>>>(json, type)
            knowledgeBase.clear()
            knowledgeBase.putAll(loaded)
        }
    }
}
