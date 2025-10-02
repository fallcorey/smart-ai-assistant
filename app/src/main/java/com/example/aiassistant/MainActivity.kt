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
            Toast.makeText(this, "Ошибка распознавания речи", Toast.LENGTH_SHORT).show()
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
        
        voiceManager = VoiceManager(this)
        alarmManager = AlarmManager(this)
        alarmManager.loadAlarms()
        webSearchManager = WebSearchManager()
        knowledgeManager = KnowledgeManager(this)
        pdfManager = PDFManager(this, knowledgeManager)
        
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
        
        buttonFile.setOnClickListener {
            if (!isProcessingPDF) {
                openFilePicker()
            } else {
                Toast.makeText(this, "Дождитесь завершения обработки", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openFilePicker() {
        filePicker.launch("application/pdf")
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
                    addAIResponse("❌ Ошибка при обработке файла: ${e.message}", false)
                }
            }
        }
    }
    
    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = message
    }
    
    private fun hideProgress() {
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
    }
    
    private fun updateProgress(progress: PDFManager.ProcessingProgress) {
        progressBar.progress = progress.progress
        progressText.text = "${progress.step}: ${progress.progress}% - ${progress.message}"
        
        if (progress.progress < 100) {
            if (chatMessages.isNotEmpty() && chatMessages.last().isAI) {
                chatMessages.removeAt(chatMessages.size - 1)
            }
            addAIResponse("🔄 ${progress.step}: ${progress.progress}%\n${progress.message}", false)
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
                addAIResponse("✅ Спасибо за обратную связь! Запомнил этот ответ. 🧠", false)
            }
            "нет", "👎" -> {
                knowledgeManager.improveAnswer(lastUserMessage, false)
                addAIResponse("❌ Понял, что ответ был не полезен. Учту это в будущем.", false)
            }
            "не важно", "🤷" -> {
                addAIResponse("Хорошо, продолжаем общение! 😊", false)
            }
            else -> {
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
                        val city = extractSearchQuery(message, "погода")
                        if (city.isNotEmpty() && city != message) {
                            webSearchManager.getWeather(city)
                        } else {
                            webSearchManager.getWeather()
                        }
                    }
                    message.contains("новости") -> {
                        val topic = extractSearchQuery(message, "новости")
                        webSearchManager.getNews(topic)
                    }
                    message.contains("что такое") || message.contains("кто такой") -> {
                        val query = extractSearchQuery(message, listOf("что такое", "кто такой"))
                        webSearchManager.getQuickAnswer(query)
                    }
                    else -> {
                        val query = extractSearchQuery(message, listOf("найди", "поиск", "найти"))
                        webSearchManager.searchWeb(query)
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
                    lastAIResponse = "❌ Ошибка при поиске информации: ${e.message}"
                    addAIResponse(lastAIResponse, true)
                }
            }
        }
    }
    
    private fun extractSearchQuery(message: String, keywords: Any): String {
        return when (keywords) {
            is String -> message.replace(keywords, "").trim()
            is List<*> -> {
                var result = message
                (keywords as List<String>).forEach { keyword ->
                    result = result.replace(keyword, "")
                }
                result.trim()
            }
            else -> message
        }
    }
    
    private fun addAIResponse(response: String, showFeedback: Boolean) {
        val aiMessage = ChatMessage(response, true)
        chatMessages.add(aiMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        
        if (showFeedback && !response.contains("❌") && !response.contains("⚠️") && 
            lastUserMessage.isNotEmpty() && !lastUserMessage.contains("запомни")) {
            addFeedbackButtons()
            waitingForFeedback = true
        }
        
        if (isVoiceResponseEnabled && voiceManager.isReady()) {
            val speechText = response
                .replace(Regex("[🎯🕐📅📆⏰💬🎵📍⚙️🔊☀️🎮📚💰🏥🍳😂🤣😄😊🤭👋🤔🎉🎤🌤️ℹ️✅❌🔍⏰⏱️🔔📋📰🔍🎯⚠️❌ℹ️🌧️❄️🌥️🌤️🤔👍👎🤷📊🧠📖📚🔍🔄📊]"), "")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                .trim()
            voiceManager.speak(speechText)
        }
        
        scrollToBottom()
    }
    
    private fun addFeedbackButtons() {
        val feedbackMessage = ChatMessage(
            "🤔 **Помогите мне учиться!** Был ли этот ответ полезен?\n\n" +
            "👍 Да  |  👎 Нет  |  🤷 Не важно",
            true
        )
        chatMessages.add(feedbackMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun generateAIResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        val learnedAnswer = knowledgeManager.findAnswer(message)
        if (learnedAnswer != null) {
            return "🧠 " + learnedAnswer.answer + "\n\n*[Это ответ из моей обучаемой базы знаний]*"
        }
        
        return when {
            message.contains("привет") || message.contains("здравствуй") -> 
                "Привет! Я ваш AI помощник. Рад вас видеть! Чем могу помочь?"
            
            message.contains("как дела") || message.contains("как ты") -> 
                "У меня всё отлично! Готов помогать вам с любыми вопросами. А как ваши дела?"
            
            message.contains("спасибо") || message.contains("благодарю") -> 
                "Пожалуйста! Всегда рад помочь. Обращайтесь, если понадобится помощь!"
            
            message.contains("запомни") && message.contains("что") -> {
                handleLearningCommand(userMessage)
            }
            
            message.contains("что ты знаешь") || message.contains("твои знания") -> {
                knowledgeManager.getKnowledgeStats()
            }
            
            message.contains("забудь") -> {
                handleForgetCommand(userMessage)
            }
            
            message.contains("анализируй pdf") || message.contains("проанализируй pdf") -> {
                "Нажмите кнопку '📁 Файл' для выбора PDF файла для анализа"
            }
            
            message.contains("время") || message.contains("который час") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                "Сейчас $time"
            }
            
            message.contains("дата") || message.contains("какое число") -> {
                val date = SimpleDateFormat("dd MMMM yyyy", Locale("ru", "RU")).format(Date())
                "Сегодня $date"
            }
            
            message.contains("день недели") -> {
                val days = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
                val calendar = Calendar.getInstance()
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                "Сегодня ${days[dayOfWeek]}"
            }
            
            message.contains("погода") -> {
                "Для получения прогноза погоды используйте поиск: 'Погода Москва' или 'Найди погода в Санкт-Петербурге'"
            }
            
            message.contains("будильник") || message.contains("разбуди") -> {
                handleAlarmCommand(message)
            }
            
            message.contains("таймер") || message.contains("засеки") -> {
                handleTimerCommand(message)
            }
            
            message.contains("мои будильники") || message.contains("покажи будильники") -> {
                showAlarms()
            }
            
            message.contains("напомни") || message.contains("напоминание") -> {
                handleReminderCommand(message)
            }
            
            message.contains("мои напоминания") || message.contains("покажи напоминания") -> {
                showReminders()
            }
            
            message.contains("очисти напоминания") || message.contains("удали напоминания") -> {
                reminders.clear()
                "✅ Все напоминания удалены"
            }
            
            message.contains("помощь") || message.contains("команды") || message.contains("что ты умеешь") -> 
                """Доступные команды:

• 💬 **Общение:** Привет, Как дела, Спасибо
• 🧠 **Обучение:** Запомни что..., Что ты знаешь, Забудь...
• 📖 **PDF файлы:** Нажмите кнопку 📁 для загрузки
• 🕐 **Время и дата:** Время, Дата, День недели  
• 😂 **Развлечения:** Расскажи шутку
• 📊 **Расчеты:** Посчитай 2+2
• 🔍 **Поиск:** Найди кошки, Что такое AI
• 📰 **Новости:** Новости технологии
• 🌤️ **Погода:** Погода Москва
• ⏰ **Будильники:** Поставь будильник на 7:30
• ⏱️ **Таймеры:** Поставь таймер на 5 минут
• 📋 **Напоминания:** Напомни купить молоко

Просто напишите или скажите команду!"""
            
            message.contains("шутка") || message.contains("анекдот") -> {
                val jokes = listOf(
                    "Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 равно Dec 25!",
                    "Что сказал один байт другому? Я тебя не бит, я с тобой!",
                    "Почему Java разработчики носят очки? Потому что они не C sharp!"
                )
                jokes.random()
            }
            
            message.contains("посчитай") || message.contains("сколько будет") || 
            message.contains("+") || message.contains("-") || message.contains("*") || message.contains("/") -> {
                calculateMathExpression(message)
            }
            
            message.contains("голос") && message.contains("выключи") -> {
                isVoiceResponseEnabled = false
                voiceManager.stop()
                "🔇 Голосовые ответы выключены"
            }
            
            message.contains("голос") && message.contains("включи") -> {
                isVoiceResponseEnabled = true
                "🔊 Голосовые ответы включены"
            }
            
            message.contains("пока") || message.contains("до свидания") -> 
                "👋 До свидания! Было приятно пообщаться. Возвращайтесь, если понадобится помощь!"
            
            else -> {
                "🤔 Я пока не знаю ответ на этот вопрос. " +
                "Вы можете научить меня! Скажите: 'Запомни, что $userMessage - это [ваш ответ]' " +
                "или загрузите PDF файл с информацией."
            }
        }
    }
    
    private fun handleLearningCommand(message: String): String {
        return try {
            val pattern = "запомни\\s*,\\s*что\\s*(.+)\\s*-\\s*это\\s*(.+)".toRegex(RegexOption.IGNORE_CASE)
            val match = pattern.find(message)
            
            if (match != null) {
                val question = match.groupValues[1].trim()
                val answer = match.groupValues[2].trim()
                
                if (question.isNotEmpty() && answer.isNotEmpty()) {
                    knowledgeManager.learn(question, answer)
                    "✅ Запомнил! Теперь я знаю, что '$question' - это '$answer'"
                } else {
                    "❌ Не понял, что именно запомнить. Формат: 'Запомни, что кошки - это животные'"
                }
            } else {
                "❌ Не понял команду. Формат: 'Запомни, что кошки - это животные'"
            }
        } catch (e: Exception) {
            "❌ Ошибка при обучении: ${e.message}"
        }
    }
    
    private fun handleForgetCommand(message: String): String {
        return try {
            val question = message.replace("забудь", "").trim()
            if (question.isNotEmpty()) {
                knowledgeManager.forget(question)
                "✅ Забыл информацию по вопросу: '$question'"
            } else {
                "❌ Укажите, что именно забыть. Формат: 'Забудь кошки'"
            }
        } catch (e: Exception) {
            "❌ Ошибка при удалении: ${e.message}"
        }
    }
    
    private fun handleAlarmCommand(message: String): String {
        val time = alarmManager.parseTimeFromText(message)
        return if (time != null) {
            val (hours, minutes) = time
            alarmManager.setAlarm(hours, minutes, "Будильник установлен через AI помощник")
        } else {
            "❌ Не понял время для будильника. Скажите например: 'Поставь будильник на 7:30' или 'Разбуди меня в 8 часов'"
        }
    }
    
    private fun handleTimerCommand(message: String): String {
        val duration = alarmManager.parseDurationFromText(message)
        return if (duration != null) {
            alarmManager.setTimer(duration, "Таймер установлен через AI помощник")
        } else {
            "❌ Не понял длительность таймера. Скажите например: 'Поставь таймер на 5 минут' или 'Таймер на 10 секунд'"
        }
    }
    
    private fun showAlarms(): String {
        val alarms = alarmManager.getAlarms()
        return if (alarms.isNotEmpty()) {
            val alarmsText = alarms.joinToString("\n• ", "✅ Ваши будильники:\n• ") { "${it.time} - ${it.message}" }
            alarmsText
        } else {
            "ℹ️ У вас нет установленных будильников"
        }
    }
    
    private fun handleReminderCommand(message: String): String {
        val reminderText = message
            .replace("напомни", "")
            .replace("напоминание", "")
            .replace("мне", "")
            .trim()
        
        return if (reminderText.isNotEmpty() && reminderText != message) {
            reminders.add(reminderText)
            "✅ Запомнил: $reminderText"
        } else {
            "❌ Что именно вам напомнить? Скажите например: 'Напомни купить молоко'"
        }
    }
    
    private fun showReminders(): String {
        return if (reminders.isNotEmpty()) {
            val remindersText = reminders.joinToString("\n• ", "✅ Ваши напоминания:\n• ")
            remindersText
        } else {
            "ℹ️ У вас нет напоминаний"
        }
    }
    
    private fun calculateMathExpression(expression: String): String {
        return try {
            val cleanExpr = expression
                .replace("посчитай", "")
                .replace("сколько будет", "")
                .replace(" ", "")
                .replace(",", ".")
            
            when {
                cleanExpr.contains("+") -> {
                    val parts = cleanExpr.split("+")
                    val result = parts[0].toDouble() + parts[1].toDouble()
                    "✅ Результат: $result"
                }
                cleanExpr.contains("-") -> {
                    val parts = cleanExpr.split("-")
                    val result = parts[0].toDouble() - parts[1].toDouble()
                    "✅ Результат: $result"
                }
                cleanExpr.contains("*") -> {
                    val parts = cleanExpr.split("*")
                    val result = parts[0].toDouble() * parts[1].toDouble()
                    "✅ Результат: $result"
                }
                cleanExpr.contains("/") -> {
                    val parts = cleanExpr.split("/")
                    if (parts[1].toDouble() == 0.0) "❌ Деление на ноль невозможно"
                    else {
                        val result = parts[0].toDouble() / parts[1].toDouble()
                        "✅ Результат: $result"
                    }
                }
                else -> "❌ Не могу распознать математическое выражение"
            }
        } catch (e: Exception) {
            "❌ Ошибка вычисления. Проверьте правильность выражения (например: 2+2, 10-5, 3*4, 15/3)"
        }
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
            }
            
            try {
                speechRecognizer.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Голосовой ввод не поддерживается", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "🎉 Добро пожаловать в Умный AI Помощник!\n\n" +
            "Теперь я могу **читать PDF файлы с прогрессом**! 📖🔄\n\n" +
            "**Как использовать PDF:**\n" +
            "1. Нажмите кнопку '📁 Файл'\n" +
            "2. Выберите PDF файл\n" +
            "3. **Следите за прогрессом в реальном времени!**\n\n" +
            "**Этапы обработки:**\n" +
            "• 📖 Чтение файла\n" +
            "• 🔍 Анализ текста  \n" +
            "• 🧠 Извлечение знаний\n" +
            "• ✅ Завершение\n\n" +
            "Нажмите 📁 чтобы попробовать!",
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
    
    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
