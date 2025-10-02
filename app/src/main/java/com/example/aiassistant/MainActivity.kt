package com.example.aiassistant

import android.content.Intent
import android.content.pm.PackageManager
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
import java.text.SimpleDateFormat
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var voiceManager: VoiceManager
    private lateinit var alarmManager: AlarmManager
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
                sendMessage(spokenText)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка распознавания речи", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализируем менеджеры после setContentView
        voiceManager = VoiceManager(this)
        alarmManager = AlarmManager(this)
        
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
        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(message, false)
        chatMessages.add(userMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        
        // Показываем прогресс
        progressBar.visibility = View.VISIBLE
        
        // Имитируем обработку AI
        handler.postDelayed({
            progressBar.visibility = View.GONE
            
            val response = generateAIResponse(message)
            val aiMessage = ChatMessage(response, true)
            chatMessages.add(aiMessage)
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            
            // Озвучиваем ответ если включено
            if (isVoiceResponseEnabled && voiceManager.isReady()) {
                val speechText = prepareTextForSpeech(response)
                voiceManager.speak(speechText)
            }
            
            scrollToBottom()
        }, 1000)
        
        scrollToBottom()
    }
    
    private fun prepareTextForSpeech(text: String): String {
        return text
            .replace(Regex("[🎯🕐📅📆⏰💬🎵📍⚙️🔊☀️🎮📚💰🏥🍳😂🤣😄😊🤭👋🤔🎉🎤🌤️ℹ️✅❌🔍⏰⏱️🔔]"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace("•", " - ")
            .replace("...", ".")
            .replace("  ", " ")
            .trim()
    }
    
    private fun generateAIResponse(userMessage: String): String {
        val message = userMessage.lowercase()
        
        return when {
            // Приветствия
            message.contains("привет") || message.contains("здравствуй") -> 
                "Привет! Я ваш AI помощник. Рад вас видеть! Чем могу помочь?"
            
            // Вопросы о состоянии
            message.contains("как дела") || message.contains("как ты") -> 
                "У меня всё отлично! Готов помогать вам с любыми вопросами. А как ваши дела?"
            
            // Благодарности
            message.contains("спасибо") || message.contains("благодарю") -> 
                "Пожалуйста! Всегда рад помочь. Обращайтесь, если понадобится помощь!"
            
            // Время
            message.contains("время") || message.contains("который час") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                "Сейчас $time"
            }
            
            // Дата
            message.contains("дата") || message.contains("какое число") -> {
                val date = SimpleDateFormat("dd MMMM yyyy", Locale("ru", "RU")).format(Date())
                "Сегодня $date"
            }
            
            // День недели
            message.contains("день недели") -> {
                val days = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
                val calendar = Calendar.getInstance()
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                "Сегодня ${days[dayOfWeek]}"
            }
            
            // Погода
            message.contains("погода") -> 
                "К сожалению, у меня нет доступа к актуальным данным о погоде. " +
                "Рекомендую использовать специализированные приложения для точного прогноза."
            
            // Будильники
            message.contains("будильник") || message.contains("разбуди") -> {
                handleAlarmCommand(message)
            }
            
            // Таймеры
            message.contains("таймер") || message.contains("засеки") -> {
                handleTimerCommand(message)
            }
            
            // Помощь
            message.contains("помощь") || message.contains("команды") || message.contains("что ты умеешь") -> 
                """Доступные команды:

• Общение: Привет, Как дела, Спасибо
• Время и дата: Время, Дата, День недели  
• Развлечения: Расскажи шутку
• Расчеты: Посчитай 2+2
• Погода: Какая погода?
• Будильники: Поставь будильник на 7:30
• Таймеры: Поставь таймер на 5 минут
• Голос: Включи голос или Выключи голос
• Настройки: Быстрее, Медленнее, Выше, Ниже

Просто напишите или скажите команду!"""
            
            // Шутки
            message.contains("шутка") || message.contains("анекдот") -> {
                val jokes = listOf(
                    "Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 равно Dec 25!",
                    "Что сказал один байт другому? Я тебя не бит, я с тобой!",
                    "Почему Java разработчики носят очки? Потому что они не C sharp!",
                    "Что говорит null, встретив своего друга? Null здавствуй!",
                    "Почему Python стал таким популярным? Потому что у него есть змеиное обаяние!"
                )
                jokes.random()
            }
            
            // Математика
            message.contains("посчитай") || message.contains("сколько будет") || 
            message.contains("+") || message.contains("-") || message.contains("*") || message.contains("/") -> {
                calculateMathExpression(message)
            }
            
            // Управление голосом
            message.contains("голос") && message.contains("выключи") -> {
                isVoiceResponseEnabled = false
                voiceManager.stop()
                "Голосовые ответы выключены"
            }
            
            message.contains("голос") && message.contains("включи") -> {
                isVoiceResponseEnabled = true
                "Голосовые ответы включены"
            }
            
            // Настройки голоса
            message.contains("быстрее") || message.contains("ускор") -> {
                voiceManager.setSpeechRate(1.2f)
                "Скорость речи увеличена"
            }
            
            message.contains("медлен") || message.contains("замед") -> {
                voiceManager.setSpeechRate(0.7f)
                "Скорость речи уменьшена"
            }
            
            message.contains("выше") && message.contains("голос") -> {
                voiceManager.setPitch(1.4f)
                "Тон голоса повышен"
            }
            
            message.contains("ниже") && message.contains("голос") -> {
                voiceManager.setPitch(0.8f)
                "Тон голоса понижен"
            }
            
            // Прощания
            message.contains("пока") || message.contains("до свидания") -> 
                "До свидания! Было приятно пообщаться. Возвращайтесь, если понадобится помощь!"
            
            // Любые другие сообщения
            else -> 
                "Я понял ваш вопрос. Это интересная тема! Пока мои знания ограничены, но я постоянно учусь. " +
                "Могу помочь с другими вопросами - спросите о времени, дате или просто пообщаемся!"
        }
    }
    
    private fun handleAlarmCommand(message: String): String {
        val time = alarmManager.parseTimeFromText(message)
        return if (time != null) {
            val (hours, minutes) = time
            val success = alarmManager.setAlarm(hours, minutes)
            if (success) {
                "⏰ Будильник установлен на $hours:$minutes"
            } else {
                "❌ Не удалось установить будильник. Проверьте приложение Часы."
            }
        } else {
            "❌ Не понял время для будильника. Скажите например: 'Поставь будильник на 7:30'"
        }
    }
    
    private fun handleTimerCommand(message: String): String {
        val duration = alarmManager.parseDurationFromText(message)
        return if (duration != null) {
            val success = alarmManager.setTimer(duration)
            if (success) {
                val minutes = duration / 60
                val seconds = duration % 60
                if (minutes > 0) {
                    "⏱️ Таймер установлен на $minutes минут${if (seconds > 0) " $seconds секунд" else ""}"
                } else {
                    "⏱️ Таймер установлен на $seconds секунд"
                }
            } else {
                "❌ Не удалось установить таймер. Проверьте приложение Часы."
            }
        } else {
            "❌ Не понял длительность таймера. Скажите например: 'Поставь таймер на 5 минут'"
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
                else -> "Не могу распознать математическое выражение"
            }
        } catch (e: Exception) {
            "Ошибка вычисления. Проверьте правильность выражения (например: 2+2, 10-5, 3*4, 15/3)"
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
            "Добро пожаловать в Умный AI Помощник!\n\n" +
            "Я могу:\n" +
            "• Отвечать на вопросы\n" +
            "• Распознавать голос\n" +
            "• Озвучивать ответы\n" +
            "• Сообщать время и дату\n" +
            "• Рассказывать шутки\n" +
            "• Выполнять расчеты\n" +
            "• Устанавливать будильники\n" +
            "• Ставить таймеры\n\n" +
            "Просто напишите или нажмите микрофон!\n\n" +
            "Примеры команд:\n" +
            "• 'Поставь будильник на 7:30'\n" +
            "• 'Таймер на 5 минут'\n" +
            "• 'Включи голос'\n" +
            "• 'Быстрее'",
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
    
    override fun onDestroy() {
        super.onDestroy()
        voiceManager.shutdown()
    }
}

data class ChatMessage(
    val message: String,
    val isAI: Boolean
)
