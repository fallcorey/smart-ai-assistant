package com.example.aiassistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var buttonSettings: ImageButton
    private lateinit var buttonHelp: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textVoiceStatus: TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val aiClient = AIClient()
    private val voiceManager = VoiceManager(this)
    private val commandProcessor = CommandProcessor(this)
    
    private var isVoiceResponseEnabled = true
    
    // Регистрация для распознавания речи
    private val speechRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                editTextMessage.setText(spokenText)
                processUserInput(spokenText, isVoiceInput = true)
            }
        } catch (e: Exception) {
            showError("Ошибка распознавания речи")
        } finally {
            textVoiceStatus.visibility = View.GONE
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        setupVoiceManager()
        checkPermissions()
        
        addWelcomeMessage()
    }
    
    private fun initViews() {
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
        buttonVoice = findViewById(R.id.buttonVoice)
        buttonClear = findViewById(R.id.buttonClear)
        buttonSettings = findViewById(R.id.buttonSettings)
        buttonHelp = findViewById(R.id.buttonHelp)
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
                processUserInput(message, isVoiceInput = false)
                editTextMessage.text.clear()
            } else {
                showToast("Введите сообщение")
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
        
        // Настройки
        buttonSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        // Помощь
        buttonHelp.setOnClickListener {
            onHelpClick(it)
        }
        
        // Длинное нажатие для быстрых команд
        buttonVoice.setOnLongClickListener {
            showQuickCommandsDialog()
            true
        }
    }
    
    private fun setupVoiceManager() {
        voiceManager.setOnInitListener {
            showToast("Голосовой помощник готов")
        }
        
        voiceManager.setOnSpeechCompleteListener { utteranceId ->
            // Речь завершена
        }
    }
    
    private fun processUserInput(userInput: String, isVoiceInput: Boolean) {
        // Добавляем сообщение пользователя
        addUserMessage(userInput)
        
        // Показываем прогресс
        progressBar.visibility = View.VISIBLE
        
        // Обрабатываем в фоновом потоке
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Сначала проверяем системные команды
                val commandResult = commandProcessor.processCommand(userInput)
                if (commandResult.type != CommandType.UNKNOWN) {
                    withContext(Dispatchers.Main) {
                        handleCommandResult(commandResult)
                    }
                    return@launch
                }
                
                // Проверяем системные команды выполнения
                if (commandProcessor.executeSystemCommand(userInput)) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        addAIMessage("✅ Команда выполнена успешно!")
                        speakResponse("Команда выполнена")
                    }
                    return@launch
                }
                
                // Если не команда, то обрабатываем AI
                val response = aiClient.getAIResponse(userInput)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    addAIMessage(response)
                    speakResponse(response)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showError("Ошибка: ${e.message}")
                }
            }
        }
    }
    
    private fun handleCommandResult(result: CommandResult) {
        progressBar.visibility = View.GONE
        
        when (result.type) {
            CommandType.CLEAR_CHAT -> {
                clearChat()
                addWelcomeMessage()
            }
            CommandType.HELP -> {
                addAIMessage(result.message)
                speakResponse("Вот список доступных команд")
            }
            else -> {
                if (result.message.isNotEmpty()) {
                    addAIMessage(result.message)
                    speakResponse(result.message)
                }
            }
        }
    }
    
    private fun addUserMessage(message: String) {
        val userMessage = ChatMessage(message, false, System.currentTimeMillis())
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun addAIMessage(message: String) {
        val aiMessage = ChatMessage(message, true, System.currentTimeMillis())
        chatMessages.add(aiMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun speakResponse(response: String) {
        if (isVoiceResponseEnabled && voiceManager.isReady()) {
            // Очищаем от эмодзи и форматирования для речи
            val cleanResponse = response.replace(Regex("[*🔍🎯🕐📅📆⏰📞💬🎵📍📅⏰⚙️🔊☀️🎮📚💰🏥🍳]"), "")
            voiceManager.speak(cleanResponse)
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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            try {
                speechRecognizer.launch(intent)
            } catch (e: Exception) {
                textVoiceStatus.visibility = View.GONE
                showError("Голосовой ввод не поддерживается")
            }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
    }
    
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            "🎉 **Добро пожаловать в Умный AI Помощник!**\n\n" +
            "Я ваш универсальный помощник с искусственным интеллектом. Вот что я умею:\n\n" +
            "🔍 **Поиск и информация**\n" +
            "📊 **Расчеты и математика**\n" +  
            "🕐 **Время, дата, будильники**\n" +
            "📞 **Звонки и сообщения**\n" +
            "🎵 **Управление медиа**\n" +
            "📍 **Навигация и геолокация**\n" +
            "📅 **Календарь и напоминания**\n" +
            "⚙️ **Системные настройки**\n" +
            "🎮 **Развлечения и игры**\n" +
            "📚 **Обучение и перевод**\n" +
            "💰 **Финансы и конвертация**\n" +
            "🏥 **Здоровье и спорт**\n" +
            "🍳 **Кулинария и рецепты**\n" +
            "🎨 **Творчество и контент**\n\n" +
            "💡 **Совет:** Нажмите и удерживайте кнопку микрофона для быстрых команд!",
            true,
            System.currentTimeMillis()
        )
        chatMessages.add(welcomeMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        scrollToBottom()
    }
    
    private fun showQuickCommandsDialog() {
        val commands = arrayOf(
            "Который час?",
            "Какое число?",
            "Расскажи шутку",
            "Интересный факт", 
            "Поставь таймер на 5 минут",
            "Найди погода",
            "Включи музыку"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🚀 Быстрые команды")
            .setItems(commands) { _, which ->
                processUserInput(commands[which], isVoiceInput = false)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showSettingsDialog() {
        val settings = arrayOf(
            if (isVoiceResponseEnabled) "🔇 Выключить голосовые ответы" else "🔊 Включить голосовые ответы",
            "🎤 Настройки голоса",
            "🌍 Язык и регион",
            "ℹ️ О приложении"
        )
        
        AlertDialog.Builder(this)
            .setTitle("⚙️ Настройки")
            .setItems(settings) { _, which ->
                when (which) {
                    0 -> {
                        isVoiceResponseEnabled = !isVoiceResponseEnabled
                        showToast(if (isVoiceResponseEnabled) "Голосовые ответы включены" else "Голосовые ответы выключены")
                    }
                    1 -> showVoiceSettings()
                    2 -> showLanguageSettings()
                    3 -> showAboutDialog()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
    
    private fun showVoiceSettings() {
        showToast("Настройки голоса в разработке")
    }
    
    private fun showLanguageSettings() {
        showToast("Настройки языка в разработке")
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("ℹ️ О приложении")
            .setMessage(
                "🤖 Умный AI Помощник\n\n" +
                "Версия: 1.0\n" +
                "Разработчик: AI Assistant Team\n\n" +
                "Универсальный помощник с искусственным интеллектом\n" +
                "© 2024 Все права защищены"
            )
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun clearChat() {
        chatMessages.clear()
        chatAdapter.notifyDataSetChanged()
    }
    
    private fun scrollToBottom() {
        recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
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
            showToast("Разрешения получены")
        } else if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Микрофон разрешен")
            } else {
                showError("Для голосового ввода нужен доступ к микрофону")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
    
    // === МЕТОДЫ ДЛЯ БЫСТРЫХ КОМАНД ===
    
    /**
     * Обработчик нажатия на быстрые команды внизу экрана
     */
    fun onQuickCommandClick(view: View) {
        val command = view.tag as? String ?: return
        processUserInput(command, isVoiceInput = false)
    }
    
    /**
     * Обработчик нажатия на кнопку помощи
     */
    fun onHelpClick(view: View) {
        val helpMessage = commandProcessor.processCommand("помощь")
        handleCommandResult(helpMessage)
    }
}

// Модель данных для сообщений
data class ChatMessage(
    val message: String,
    val isAI: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
