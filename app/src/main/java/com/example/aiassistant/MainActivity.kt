package com.example.aiassistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
    private lateinit var textVoiceStatus: TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    
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
        
        addWelcomeMessage()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonVoice = findViewById(R.id.buttonVoice)
        buttonClear = findViewById(R.id.buttonClear)
        progressBar = findViewById(R.id.progressBar)
        textVoiceStatus = findViewById(R.id.textVoiceStatus)
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
        // Сообщение пользователя
        val userMessage = ChatMessage(message, false)
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        
        // Ответ AI
        progressBar.visibility = View.VISIBLE
        
        // Имитируем задержку ответа
        recyclerViewChat.postDelayed({
            progressBar.visibility = View.GONE
            
            val response = when {
                message.contains("привет", ignoreCase = true) -> "Привет! Как дела?"
                message.contains("время", ignoreCase = true) -> "Сейчас: ${java.util.Date()}"
                message.contains("погода", ignoreCase = true) -> "Погода сегодня хорошая!"
                else -> "Я понял: \"$message\". Это интересно!"
            }
            
            val aiMessage = ChatMessage(response, true)
            chatMessages.add(aiMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            scrollToBottom()
        }, 1000)
        
        scrollToBottom()
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
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "Добро пожаловать! Говорите или пишите сообщения.",
            true
        )
        chatMessages.add(welcomeMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
    }
    
    private fun clearChat() {
        chatMessages.clear()
        chatAdapter.notifyDataSetChanged()
    }
    
    private fun scrollToBottom() {
        recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Микрофон разрешен", Toast.LENGTH_SHORT).show()
        }
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
