package com.example.aiassistant

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
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
                addUserMessage(message)
                editTextMessage.text.clear()
                
                // Имитация ответа AI
                handler.postDelayed({
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
        addAiMessage("Привет! Я ваш AI помощник. Задавайте вопросы!")
    }
    
    private fun generateResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            message.contains("привет") -> "Привет! Как дела?"
            message.contains("как дела") -> "Отлично! Рад вас видеть!"
            message.contains("время") -> "Время: ${java.util.Date()}"
            message.contains("дата") -> "Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date())}"
            message.contains("шутка") -> "Что программист сказал перед смертью? Hello world!"
            else -> "Понял ваш вопрос: \"$userMessage\". Чем еще могу помочь?"
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
