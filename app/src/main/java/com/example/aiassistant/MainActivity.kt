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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonVoice: ImageButton
    private lateinit var buttonClear: Button
    private lateinit var buttonSearch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textVoiceStatus: TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val aiClient = AIClient()
    private val voiceManager = VoiceManager(this)
    private val commandProcessor = CommandProcessor(this)
    
    // Регистрация для распознавания речи
    private val speechRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!results.isNullOrEmpty()) {
            val spokenText = results[0]
            editTextMessage.setText(spokenText)
            sendMessage(spokenText)
        }
        textVoiceStatus.visibility = View.GONE
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
        
        // Добавляем приветственное сообщение
        addWelcomeMessage()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonVoice = findViewById(R.id.buttonVoice)
        buttonClear = findViewById(R.id.buttonClear)
        buttonSearch = findViewById(R.id.buttonSearch)
        progressBar = findViewById(R.id.progressBar)
        textVoiceStatus = findViewById(R.id.textVoiceStatus)
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter
    }
    
    private fun setupClickListeners() {
        // Отправка текстового сообщения
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                editTextMessage.text.clear()
            } else {
                Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Голосовой ввод
        buttonVoice.setOnClickListener {
            startVoiceInput()
        }
        
        // Очистка чата
        buttonClear.setOnClickListener {
            clearChat()
            addWelcomeMessage()
        }
        
        // Поиск в интернете
        buttonSearch.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                searchWeb(message)
            } else {
                Toast.makeText(this, "Введите запрос для поиска", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendMessage(message: String) {
        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(message, false)
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
        
        // Показываем прогресс
        progressBar.visibility = View.VISIBLE
        
        // Обрабатываем команды или отправляем AI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Сначала проверяем команды
                val commandResponse = commandProcessor.processCommand(message)
                if (commandResponse != null) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        val aiMessage = ChatMessage(commandResponse, true)
                        chatMessages.add(aiMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)
                        scrollToBottom()
                        
                        // Озвучиваем ответ если голосовой режим активен
                        if (voiceManager.isReady()) {
                            voiceManager.speak(commandResponse)
                        }
                    }
                    return@launch
                }
                
                // Проверяем системные команды
                if (commandProcessor.executeSystemCommand(message)) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        val aiMessage = ChatMessage("Команда выполнена!", true)
                        chatMessages.add(aiMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)
                        scrollToBottom()
                    }
                    return@launch
                }
                
                // Если не команда, то AI ответ
                val response = aiClient.getAIResponse(message)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    if (response.isNotEmpty()) {
                        val aiMessage = ChatMessage(response, true)
                        chatMessages.add(aiMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)
                        scrollToBottom()
                        
                        // Озвучиваем ответ если голосовой режим активен
                        if (voiceManager.isReady()) {
                            voiceManager.speak(response)
                        }
                    } else {
                        showError("Ошибка получения ответа")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showError("Ошибка: ${e.message}")
                }
            }
        }
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            
            textVoiceStatus.visibility = View.VISIBLE
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
            }
            
            speechRecognizer.launch(intent)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
    }
    
    private fun searchWeb(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val searchResult = aiClient.searchWeb(query)
                withContext(Dispatchers.Main) {
                    if (searchResult.isNotEmpty()) {
                        val searchMessage = ChatMessage("🔍 $searchResult", true)
                        chatMessages.add(searchMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)
                        scrollToBottom()
                    } else {
                        showError("Не удалось выполнить поиск")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Ошибка поиска: ${e.message}")
                }
            }
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "🎉 Добро пожаловать в Умный AI Помощник!\n\n" +
            "Я могу:\n" +
            "• 💬 Отвечать на вопросы\n" +
            "• 🎤 Распознавать голос\n" +
            "• 🕐 Сообщать время и дату\n" +
            "• 🔍 Искать информацию\n" +
            "• ⏰ Устанавливать будильники\n\n" +
            "Просто напишите или нажмите микрофон!",
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
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.INTERNET
        )
        
        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 0)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == 0 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
