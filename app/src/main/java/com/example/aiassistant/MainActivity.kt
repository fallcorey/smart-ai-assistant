package com.example.aiassistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonVoice: ImageButton
    private lateinit var buttonClear: Button
    private lateinit var progressBar: ProgressBar
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    
    // Регистрация для распознавания речи
    private val speechRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                editTextMessage.setText(spokenText)
                sendMessage(spokenText)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка распознавания речи", Toast.LENGTH_SHORT).show()
        }
    }
    
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
        buttonVoice = findViewById(R.id.buttonVoice)
        buttonClear = findViewById(R.id.buttonClear)
        progressBar = findViewById(R.id.progressBar)
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
        
        buttonVoice.setOnClickListener {
            startVoiceInput()
        }
        
        buttonClear.setOnClickListener {
            clearChat()
            addWelcomeMessage()
        }
    }
    
    private fun sendMessage(message: String) {
        try {
            // Сообщение пользователя
            val userMessage = ChatMessage(message, false)
            chatMessages.add(userMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            
            // Ответ AI
            progressBar.visibility = View.VISIBLE
            
            // Имитируем задержку ответа
            recyclerViewChat.postDelayed({
                progressBar.visibility = View.GONE
                
                val response = generateAIResponse(message)
                
                val aiMessage = ChatMessage(response, true)
                chatMessages.add(aiMessage)
                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                scrollToBottom()
            }, 1000)
            
            scrollToBottom()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun generateAIResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            message.contains("привет") -> "Привет! Как я могу вам помочь?"
            message.contains("как дела") -> "У меня всё отлично! Спасибо, что спросили. А у вас?"
            message.contains("спасибо") -> "Пожалуйста! Обращайтесь, если нужна помощь."
            message.contains("время") -> "Сейчас: ${Date()}"
            message.contains("дата") -> "Сегодня: ${java.text.SimpleDateFormat("dd.MM.yyyy").format(Date())}"
            message.contains("погода") -> "Для информации о погоде рекомендую использовать специализированные приложения."
            message.contains("помощь") -> 
                "Я могу:\n• Отвечать на вопросы\n• Поддерживать беседу\n• Сообщать время и дату\n• Распознавать голос"
            message.contains("шутка") -> {
                val jokes = listOf(
                    "Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 == Dec 25!",
                    "Что сказал один байт другому? Я тебя не бит, я с тобой!",
                    "Почему Java-разработчики носят очки? Потому что они не C#!"
                )
                jokes.random()
            }
            message.contains("калькулятор") || message.contains("посчитай") -> {
                try {
                    val expr = message.replace("посчитай", "").replace("калькулятор", "").trim()
                    if (expr.contains("+")) {
                        val parts = expr.split("+")
                        val result = parts[0].trim().toDouble() + parts[1].trim().toDouble()
                        "Результат: $result"
                    } else if (expr.contains("-")) {
                        val parts = expr.split("-")
                        val result = parts[0].trim().toDouble() - parts[1].trim().toDouble()
                        "Результат: $result"
                    } else if (expr.contains("*")) {
                        val parts = expr.split("*")
                        val result = parts[0].trim().toDouble() * parts[1].trim().toDouble()
                        "Результат: $result"
                    } else if (expr.contains("/")) {
                        val parts = expr.split("/")
                        val result = parts[0].trim().toDouble() / parts[1].trim().toDouble()
                        "Результат: $result"
                    } else {
                        "Введите математическое выражение (например: 2+2)"
                    }
                } catch (e: Exception) {
                    "Не могу вычислить выражение. Проверьте правильность ввода."
                }
            }
            else -> "Я понял ваш вопрос: \"$userMessage\". Это интересно! Чем еще могу помочь?"
        }
    }
    
    private fun startVoiceInput() {
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED) {
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
                }
                
                speechRecognizer.launch(intent)
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Голосовой ввод не поддерживается", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "Добро пожаловать! Я ваш AI помощник.\n\n" +
            "Напишите сообщение или нажмите кнопку микрофона для голосового ввода.\n\n" +
            "Я могу:\n" +
            "• 💬 Отвечать на вопросы\n" +
            "• 🎤 Распознавать голос\n" +
            "• 🕐 Сообщать время и дату\n" +
            "• 😂 Рассказывать шутки\n" +
            "• 📊 Выполнять расчеты",
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
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Микрофон разрешен", Toast.LENGTH_SHORT).show()
        }
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
