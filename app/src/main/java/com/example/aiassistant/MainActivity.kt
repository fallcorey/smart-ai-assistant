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
            showToast("Ошибка выбора файла")
        }
    }
    
    private fun handleFileSelection(uri: Uri) {
        isProcessingPDF = true
        buttonFile.isEnabled = false
        showProgress("Подготовка к обработке PDF...")
        
        addAIResponse("Загружаю и анализирую книгу...", false)
        
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
                            addAIResponse("Ошибка обработки: ${e.message}", false)
                        }
                    }
                    .collect { progress ->
                        runOnUiThread {
                            if (progress.progress == 100) {
                                hideProgress()
                                buttonFile.isEnabled = true
                                isProcessingPDF = false
                                addAIResponse(progress.message, false)
                                
                                // Добавляем подсказки по книге
                                addBookHelpMessage()
                            }
                        }
                    }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    buttonFile.isEnabled = true
                    isProcessingPDF = false
                    addAIResponse("Ошибка: ${e.message}", false)
                }
            }
        }
    }
    
    private fun addBookHelpMessage() {
        val helpMessage = """
            ТЕПЕРЬ ВЫ МОЖЕТЕ ЗАДАВАТЬ ВОПРОСЫ ПО КНИГЕ!
            
            Примеры вопросов:
            - "О чем эта книга?"
            - "Какие основные идеи?"
            - "Расскажи о главе 1"
            - "Что такое [концепция]?"
            - "Кто главные герои?"
            - "В чем основная мысль?"
            - "Объясни [тему]"
            
            Задавайте любые вопросы по содержанию!
        """.trimIndent()
        
        addAIResponse(helpMessage, false)
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
                if (chatMessages.isNotEmpty() && chatMessages.last().isAI && 
                    chatMessages.last().message.contains("Обработка")) {
                    chatMessages.removeAt(chatMessages.size - 1)
                    chatAdapter.notifyItemRemoved(chatMessages.size)
                }
                addAIResponse("${progress.step}: ${progress.progress}%\n${progress.message}", false)
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
        
        showProgress("Анализирую вопрос...")
        
        // Проверяем, относится ли вопрос к книге
        if (pdfManager.hasBook() && isBookRelatedQuestion(message)) {
            handler.postDelayed({
                hideProgress()
                val response = pdfManager.askAboutBook(message)
                lastAIResponse = response
                addAIResponse(response, true)
            }, 1500)
        } else if (message.contains("найди") || message.contains("поиск") || 
                  message.contains("что такое") || message.contains("кто такой") || 
                  message.contains("новости") || message.contains("погода")) {
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
    
    private fun isBookRelatedQuestion(question: String): Boolean {
        val bookKeywords = listOf(
            "книга", "глава", "герой", "персонаж", "сюжет", "автор", 
            "про что", "о чем", "тема", "идея", "концепция", "определение",
            "объясни", "расскажи", "содержание"
        )
        
        return bookKeywords.any { question.contains(it, ignoreCase = true) } ||
               pdfManager.getCurrentBookTitle() != "Книга не загружена"
    }
    
    private fun handleFeedback(message: String) {
        when (message.toLowerCase()) {
            "да", "👍" -> {
                knowledgeManager.learn(lastUserMessage, lastAIResponse)
                addAIResponse("Спасибо! Запомнил этот ответ.", false)
            }
            "нет", "👎" -> {
                addAIResponse("Понял, учту в будущем.", false)
            }
            "не важно", "🤷" -> {
                addAIResponse("Хорошо, продолжаем!", false)
            }
            else -> {
                lastUserMessage = message
                val userMessage = ChatMessage(message, false)
                chatMessages.add(userMessage)
                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                
                showProgress("Анализирую вопрос...")
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
                    message.contains("погода") -> webSearchManager.getWeather()
                    message.contains("новости") -> webSearchManager.getNews()
                    message.contains("что такое") || message.contains("кто такой") -> 
                        webSearchManager.getQuickAnswer(message)
                    else -> webSearchManager.searchWeb(message)
                }
                
                runOnUiThread {
                    hideProgress()
                    lastAIResponse = response
                    addAIResponse(response, true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    addAIResponse("Ошибка поиска: ${e.message}", true)
                }
            }
        }
    }
    
    private fun addAIResponse(response: String, showFeedback: Boolean) {
        runOnUiThread {
            val aiMessage = ChatMessage(response, true)
            chatMessages.add(aiMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            
            if (showFeedback && !response.contains("Ошибка") && 
                lastUserMessage.isNotEmpty() && !lastUserMessage.contains("запомни")) {
                addFeedbackButtons()
                waitingForFeedback = true
            }
            
            if (isVoiceResponseEnabled && voiceManager.isReady()) {
                try {
                    val speechText = cleanTextForSpeech(response)
                    if (speechText.length > 5) {
                        voiceManager.speak(speechText)
                    }
                } catch (e: Exception) {
                    // Ignore TTS errors
                }
            }
            
            scrollToBottom()
        }
    }
    
    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("[*\\n\\-•]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun addFeedbackButtons() {
        runOnUiThread {
            val feedbackMessage = ChatMessage("Был ли ответ полезен?\nДа / Нет / Не важно", true)
            chatMessages.add(feedbackMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            scrollToBottom()
        }
    }
    
    private fun generateAIResponse(userMessage: String): String {
        return try {
            val message = userMessage.toLowerCase()
            
            val learnedAnswer = knowledgeManager.findAnswer(message)
            if (learnedAnswer != null) {
                return "Из памяти: ${learnedAnswer.answer}"
            }
            
            when {
                message.contains("привет") -> "Привет! Я ваш умный помощник. Могу помочь с вопросами, поиском информации и анализом книг!"
                message.contains("как дела") -> "Отлично! Готов помочь вам. Чем могу быть полезен?"
                message.contains("спасибо") -> "Пожалуйста! Всегда рад помочь."
                message.contains("запомни") && message.contains("что") -> handleLearningCommand(userMessage)
                message.contains("что ты знаешь") -> knowledgeManager.getKnowledgeStats()
                message.contains("забудь") -> handleForgetCommand(userMessage)
                message.contains("время") -> "Сейчас времени: " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                message.contains("дата") -> "Сегодня: " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                message.contains("шутка") -> "Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 равно Dec 25!"
                message.contains("посчитай") -> calculateMathExpression(message)
                message.contains("помощь") -> getHelpMessage()
                pdfManager.hasBook() && message.contains("книга") -> 
                    "Сейчас загружена книга: \"${pdfManager.getCurrentBookTitle()}\". Задавайте вопросы по её содержанию!"
                else -> "Я пока не знаю ответ на этот вопрос. Вы можете:\n- Научить меня: 'Запомни, что [вопрос] - это [ответ]'\n- Загрузить книгу в PDF и задавать вопросы по ней\n- Использовать поиск в интернете"
            }
        } catch (e: Exception) {
            "Ошибка обработки запроса"
        }
    }
    
    private fun getHelpMessage(): String {
        val baseHelp = """
            УМНЫЙ AI ПОМОЩНИК - что я умею:
            
            РАБОТА С КНИГАМИ:
            - Загружайте PDF книги (кнопка Файл)
            - Задавайте любые вопросы по содержанию
            - Получайте анализ глав и концепций
            
            ОБУЧЕНИЕ:
            - "Запомни, что кошки - это животные"
            - "Что ты знаешь?" - статистика знаний
            - "Забудь кошки" - удаление информации
            
            ПОИСК:
            - "Найди информацию о кошках"
            - "Что такое искусственный интеллект"
            - "Новости технологии"
            - "Погода в Москве"
            
            УТИЛИТЫ:
            - "Который час?" - текущее время
            - "Какая дата?" - сегодняшняя дата
            - "Расскажи шутку" - развлечения
            - "Посчитай 2+2" - математика
            
            ГОЛОС:
            - Нажмите микрофон для голосового ввода
            - Ответы озвучиваются автоматически
        """.trimIndent()
        
        return if (pdfManager.hasBook()) {
            baseHelp + "\n\nСейчас активна книга: \"${pdfManager.getCurrentBookTitle()}\""
        } else {
            baseHelp
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
                    "Запомнил: '$question' - это '$answer'"
                } else {
                    "Укажите вопрос и ответ"
                }
            } else {
                "Формат: 'Запомни, что кошки - это животные'"
            }
        } catch (e: Exception) {
            "Ошибка обучения"
        }
    }
    
    private fun handleForgetCommand(message: String): String {
        return try {
            val question = message.replace("забудь", "").trim()
            if (question.isNotEmpty()) {
                knowledgeManager.forget(question)
                "Забыл: '$question'"
            } else {
                "Укажите что забыть"
            }
        } catch (e: Exception) {
            "Ошибка удаления"
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
                    "Результат: $result"
                }
                cleanExpr.contains("-") -> {
                    val parts = cleanExpr.split("-")
                    val result = parts[0].toDouble() - parts[1].toDouble()
                    "Результат: $result"
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    val result = parts[0].toDouble() * parts[1].toDouble()
                    "Результат: $result"
                }
                cleanExpr.contains("/") -> {
                    val parts = cleanExpr.split("/")
                    if (parts[1].toDouble() == 0.0) "Деление на ноль невозможно"
                    else {
                        val result = parts[0].toDouble() / parts[1].toDouble()
                        "Результат: $result"
                    }
                }
                else -> "Не понимаю математическое выражение"
            }
        } catch (e: Exception) {
            "Ошибка вычисления"
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
            """
            ДОБРО ПОЖАЛОВАТЬ В УМНЫЙ AI ПОМОЩНИК!
            
            ТЕПЕРЬ Я ПОНИМАЮ КНИГИ! Загрузите PDF и задавайте любые вопросы:
            - "О чем эта книга?"
            - "Объясни главу 1" 
            - "Кто главные герои?"
            - "В чем основная идея?"
            - "Что такое [любая концепция]?"
            
            ТАКЖЕ Я УМЕЮ:
            - Самообучаться на ваших ответах
            - Искать информацию в интернете
            - Работать с будильниками и напоминаниями
            - Отвечать на голосовые команды
            
            Нажмите кнопку Файл чтобы загрузить книгу или задайте вопрос!
            """.trimIndent(), true
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
            // Ignore
        }
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
