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
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        
        // Добавляем приветственное сообщение
        addWelcomeMessage()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonClear = findViewById(R.id.buttonClear)
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter
    }
    
    private fun setupClickListeners() {
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                editTextMessage.text.clear()
            } else {
                Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show()
            }
        }
        
        buttonClear.setOnClickListener {
            clearChat()
            addWelcomeMessage()
        }
    }
    
    private fun sendMessage(message: String) {
        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(message, false)
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        
        // Имитируем ответ AI
        simulateAIResponse(message)
        
        scrollToBottom()
    }
    
    private fun simulateAIResponse(userMessage: String) {
        // Простые ответы AI
        val response = when {
            userMessage.contains("привет", ignoreCase = true) -> "Привет! Как я могу вам помочь?"
            userMessage.contains("как дела", ignoreCase = true) -> "У меня всё отлично! Спасибо, что спросили. А у вас?"
            userMessage.contains("спасибо", ignoreCase = true) -> "Пожалуйста! Обращайтесь, если нужна помощь."
            userMessage.contains("погода", ignoreCase = true) -> "Для информации о погоде рекомендую использовать специализированные приложения."
            userMessage.contains("время", ignoreCase = true) -> "Текущее время: ${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
            userMessage.contains("помощь", ignoreCase = true) -> 
                "Я могу:\n• Отвечать на вопросы\n• Поддерживать беседу\n• Предоставлять базовую информацию"
            else -> "Я понял ваш вопрос: \"$userMessage\". Это интересно! Для более сложных запросов рекомендуется настроить локальную AI модель."
        }
        
        // Добавляем ответ AI с небольшой задержкой
        recyclerViewChat.postDelayed({
            val aiMessage = ChatMessage(response, true)
            chatMessages.add(aiMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            scrollToBottom()
        }, 1000)
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "Добро пожаловать! Я ваш AI помощник. Задавайте вопросы, и я постараюсь помочь.",
            true
        )
        chatMessages.add(welcomeMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun clearChat() {
        chatMessages.clear()
        chatAdapter.notifyDataSetChanged()
    }
    
    private fun scrollToBottom() {
        recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
