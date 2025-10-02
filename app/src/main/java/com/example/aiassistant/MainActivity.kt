package com.example.aiassistant

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonClear: Button
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализация views
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonClear = findViewById(R.id.buttonClear)
        
        // Настройка RecyclerView
        chatAdapter = ChatAdapter(chatMessages)
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter
        
        // Обработчики кликов
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                addUserMessage(message)
                editTextMessage.text.clear()
                
                // Имитация ответа AI
                Handler().postDelayed({
                    addAiMessage(generateResponse(message))
                }, 1000)
            } else {
                Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show()
            }
        }
        
        buttonClear.setOnClickListener {
            chatMessages.clear()
            chatAdapter.notifyDataSetChanged()
            addWelcomeMessage()
        }
        
        // Приветственное сообщение
        addWelcomeMessage()
    }
    
    private fun addUserMessage(message: String) {
        chatMessages.add(ChatMessage(message, false))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun addAiMessage(message: String) {
        chatMessages.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun addWelcomeMessage() {
        addAiMessage("Привет! Я ваш AI помощник. Чем могу помочь?")
    }
    
    private fun generateResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            message.contains("привет") -> "Привет! Рад вас видеть!"
            message.contains("как дела") -> "Всё отлично! Готов помогать вам."
            message.contains("время") -> "Текущее время: ${java.util.Date()}"
            message.contains("дата") -> "Сегодня: ${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date())}"
            message.contains("погода") -> "Погоду лучше проверять в специализированном приложении"
            message.contains("шутка") -> "Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 == Dec 25!"
            message.contains("спасибо") -> "Пожалуйста! Обращайтесь ещё!"
            message.contains("пока") -> "До свидания! Буду рад помочь снова."
            else -> "Интересный вопрос! Я ещё учусь, но скоро смогу отвечать на такие вопросы лучше."
        }
    }
    
    private fun scrollToBottom() {
        recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
