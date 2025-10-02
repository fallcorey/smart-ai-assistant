package com.example.aiassistant

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var buttonVoice: ImageButton
    private lateinit var buttonClear: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var buttonFile: Button
    private lateinit var progressText: TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var voiceManager: VoiceManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var webSearchManager: WebSearchManager
    private lateinit var knowledgeManager: KnowledgeManager
    private lateinit var pdfManager: PDFManager
    private var isVoiceResponseEnabled = true
    
    private val reminders = mutableListOf<String>()
    private var lastUserMessage = ""
    private var lastAIResponse = ""
    private var waitingForFeedback = false
    private var isProcessingPDF = false

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
            showToast("Ошибка распознавания речи")
        }
    }
    
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleFileSelection(uri)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализация с обработкой ошибок
        try {
            voiceManager = VoiceManager(this)
            alarmManager = AlarmManager(this)
            alarmManager.loadAlarms()
            webSearchManager = WebSearchManager()
            knowledgeManager = KnowledgeManager(this)
            pdfManager = PDFManager(this, knowledgeManager)
        } catch (e: Exception) {
            showToast("Ошибка инициализации: ${e.message}")
        }
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        
        addWelcomeMessage()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonVoice = findViewById(R.id.buttonVoice)
        buttonClear = findViewById(R.id.buttonClear)
        progressBar = findViewById(R.id.progressBar)
        buttonFile = findViewById(R.id.buttonFile)
        progressText = findViewById(R.id.progressText)
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
                showToast("Введите сообщение")
            }
        }
        
        buttonVoice.setOnClickListener {
            startVoiceInput()
        }
        
        buttonClear.setOnClickListener {
            clearChat()
            addWelcomeMessage()
        }
        
        buttonFile.setOnClickListener {
            if (!isProcessingPDF) {
                openFilePicker()
            } else {
                showToast("Дождитесь завершения обработки")
            }
        }
    }
    
    private fun openFilePicker() {
        try {
            filePicker.launch("application/pdf")
        } catch (e: Exception) {
            showToast("Ошибка выбора файла: ${e.message}")
        }
    }
    
    private fun handleFileSelection(uri: Uri) {
        isProcessingPDF = true
        buttonFile.isEnabled = false
        showProgress("Подготовка к обработке PDF...")
        
        addAIResponse("📖 Начинаю обработку PDF файла...", false)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pdfManager.learnFromPDF(uri)
                    .onEach { progress ->
                        runOnUiThread {
                            updateProgress(progress)
                        }
                    }
                    .catch { e ->
                        runOnUiThread {
                            hideProgress()
                            buttonFile.isEnabled = true
                            isProcessingPDF = false
                            addAIResponse("❌ Ошибка обработки: ${e.message}", false)
                        }
                    }
                    .collect { progress ->
                        runOnUiThread {
                            if (progress.progress == 100) {
                                hideProgress()
                                buttonFile.isEnabled = true
                                isProcessingPDF = false
                                addAIResponse(progress.message, false)
                            }
                        }
                    }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    buttonFile.isEnabled = true
                    isProcessingPDF = false
                    addAIResponse("❌ Ошибка: ${e.message}", false)
                }
            }
        }
    }
    
    private fun showProgress(message: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            progressText.text = message
            progressBar.progress = 0
        }
    }
    
    private fun hideProgress() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }
    }
    
    private fun updateProgress(progress: PDFManager.ProcessingProgress) {
        runOnUiThread {
            progressBar.progress = progress.progress
            progressText.text = "${progress.step}: ${progress.progress}%"
            
            if (progress.progress < 100) {
                // Обновляем последнее сообщение с прогрессом
                if (chatMessages.isNotEmpty() && chatMessages.last().isAI && 
                    chatMessages.last().message.contains("🔄")) {
                    chatMessages.removeAt(chatMessages.size - 1)
                    chatAdapter.notifyItemRemoved(chatMessages.size)
                }
                addAIResponse("🔄 ${progress.step}: ${progress.progress}%\n${progress.message}", false)
            }
        }
    }
    
    private fun sendMessage(message: String) {
        if (waitingForFeedback) {
            handleFeedback(message)
            return
        }
        
        lastUserMessage = message
        val userMessage = ChatMessage(message, false)
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        
        showProgress("Обработка запроса...")
        
        if (message.contains("найди") || message.contains("поиск") || message.contains("что такое") || 
            message.contains("кто такой") || message.contains("новости") || message.contains("погода")) {
            handleSearchQuery(message)
        } else {
            handler.postDelayed({
                hideProgress()
                val response = generateAIResponse(message)
                lastAIResponse = response
                addAIResponse(response, true)
            }, 1000)
        }
        
        scrollToBottom()
    }
    
    private fun handleFeedback(message: String) {
        when (message.lowercase()) {
            "да", "👍" -> {
                knowledgeManager.learn(lastUserMessage, lastAIResponse)
                addAIResponse("✅ Спасибо! Запомнил ответ. 🧠", false)
            }
            "нет", "👎" -> {
                addAIResponse("❌ Понял, учту в будущем.", false)
            }
            "не важно", "🤷" -> {
                addAIResponse("Хорошо, продолжаем! 😊", false)
            }
            else -> {
                // Если это не обратная связь, обрабатываем как обычное сообщение
                lastUserMessage = message
                val userMessage = ChatMessage(message, false)
                chatMessages.add(userMessage)
                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                
                showProgress("Обработка запроса...")
                handler.postDelayed({
                    hideProgress()
                    val response = generateAIResponse(message)
                    lastAIResponse = response
                    addAIResponse(response, true)
                }, 1000)
            }
        }
        waitingForFeedback = false
        scrollToBottom()
    }
    
    private fun handleSearchQuery(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = when {
                    message.contains("погода") -> {
                        webSearchManager.getWeather()
                    }
                    message.contains("новости") -> {
                        webSearchManager.getNews()
                    }
                    message.contains("что такое") || message.contains("кто такой") -> {
                        webSearchManager.getQuickAnswer(message)
                    }
                    else -> {
                        webSearchManager.searchWeb(message)
                    }
                }
                
                runOnUiThread {
                    hideProgress()
                    lastAIResponse = response
                    addAIResponse(response, true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    addAIResponse("❌ Ошибка поиска", true)
                }
            }
        }
    }
    
    private fun addAIResponse(response: String, showFeedback: Boolean) {
        runOnUiThread {
            val aiMessage = ChatMessage(response, true)
            chatMessages.add(aiMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            
            if (showFeedback && !response.contains("❌") && !response.contains("⚠️") && 
                lastUserMessage.isNotEmpty() && !lastUserMessage.contains("запомни")) {
                addFeedbackButtons()
                waitingForFeedback = true
            }
            
            if (isVoiceResponseEnabled && voiceManager.isReady()) {
                try {
                    val speechText = response
                        .replace(Regex("[^\\w\\sа-яА-Я.,!?]"), "")
                        .replace("**", "")
                        .trim()
                    if (speechText.length > 5) {
                        voiceManager.speak(speechText)
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки озвучки
                }
            }
            
            scrollToBottom()
        }
    }
    
    private fun addFeedbackButtons() {
        runOnUiThread {
            val feedbackMessage = ChatMessage("🤔 Был ли ответ полезен?\n👍 Да  👎 Нет  🤷 Не важно", true)
            chatMessages.add(feedbackMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            scrollToBottom()
        }
    }
    
    private fun generateAIResponse(userMessage: String): String {
        return try {
            val message = userMessage.lowercase()
            
            val learnedAnswer = knowledgeManager.findAnswer(message)
            if (learnedAnswer != null) {
                return "🧠 ${learnedAnswer.answer}"
            }
            
            when {
                message.contains("привет") -> "Привет! Чем могу помочь?"
                message.contains("как дела") -> "Отлично! А у вас?"
                message.contains("спасибо") -> "Пожалуйста!"
                message.contains("запомни") && message.contains("что") -> handleLearningCommand(userMessage)
                message.contains("что ты знаешь") -> knowledgeManager.getKnowledgeStats()
                message.contains("забудь") -> handleForgetCommand(userMessage)
                message.contains("время") -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                message.contains("дата") -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                message.contains("шутка") -> "Почему программисты путают Хэллоуин и Рождество? Oct 31 == Dec 25!"
                message.contains("посчитай") -> calculateMathExpression(message)
                message.contains("помощь") -> "Команды: привет, время, дата, шутка, посчитай, запомни, забудь, найди"
                else -> "Не знаю ответ. Научите: 'Запомни, что $userMessage - это [ответ]'"
            }
        } catch (e: Exception) {
            "❌ Ошибка обработки запроса"
        }
    }
    
    private fun handleLearningCommand(message: String): String {
        return try {
            val pattern = "запомни, что (.+) - это (.+)".toRegex(RegexOption.IGNORE_CASE)
            val match = pattern.find(message)
            if (match != null) {
                val question = match.groupValues[1].trim()
                val answer = match.groupValues[2].trim()
                if (question.isNotEmpty() && answer.isNotEmpty()) {
                    knowledgeManager.learn(question, answer)
                    "✅ Запомнил: '$question' - это '$answer'"
                } else {
                    "❌ Укажите вопрос и ответ"
                }
            } else {
                "❌ Формат: 'Запомни, что кошки - это животные'"
            }
        } catch (e: Exception) {
            "❌ Ошибка обучения"
        }
    }
    
    private fun handleForgetCommand(message: String): String {
        return try {
            val question = message.replace("забудь", "").trim()
            if (question.isNotEmpty()) {
                knowledgeManager.forget(question)
                "✅ Забыл: '$question'"
            } else {
                "❌ Укажите что забыть"
            }
        } catch (e: Exception) {
            "❌ Ошибка удаления"
        }
    }
    
    private fun calculateMathExpression(expression: String): String {
        return try {
            val cleanExpr = expression
                .replace("посчитай", "")
                .replace("сколько будет", "")
                .replace(" ", "")
            
            when {
                cleanExpr.contains("+") -> {
                    val parts = cleanExpr.split("+")
                    val result = parts[0].toDouble() + parts[1].toDouble()
                    "✅ $result"
                }
                cleanExpr.contains("-") -> {
                    val parts = cleanExpr.split("-")
                    val result = parts[0].toDouble() - parts[1].toDouble()
                    "✅ $result"
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    val result = parts[0].toDouble() * parts[1].toDouble()
                    "✅ $result"
                }
                else -> "❌ Не понимаю выражение"
            }
        } catch (e: Exception) {
            "❌ Ошибка вычисления"
        }
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
                }
                speechRecognizer.launch(intent)
            } catch (e: Exception) {
                showToast("Голосовой ввод не поддерживается")
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "🎉 Добро пожаловать!\n\n" +
            "Я могу:\n" +
            "• 📖 Читать PDF с прогрессом\n" + 
            "• 🧠 Самообучаться\n" +
            "• 🎤 Распознавать голос\n" +
            "• 🔊 Озвучивать ответы\n\n" +
            "Нажмите 📁 для загрузки PDF!",
            true
        )
        chatMessages.add(welcomeMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun clearChat() {
        chatMessages.clear()
        chatAdapter.notifyDataSetChanged()
        waitingForFeedback = false
    }
    
    private fun scrollToBottom() {
        recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
    
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showToast("Микрофон разрешен")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            voiceManager.shutdown()
        } catch (e: Exception) {
            // Игнорируем ошибки завершения
        }
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
