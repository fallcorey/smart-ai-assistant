package com.example.aiassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<ChatMessage>) : 
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    
    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isAI) TYPE_AI else TYPE_USER
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            TYPE_AI -> R.layout.item_message_ai
            else -> R.layout.item_message_user
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        
        fun bind(chatMessage: ChatMessage) {
            textMessage.text = chatMessage.message
            
            // Форматируем время
            val time = Date(chatMessage.timestamp)
            textTime.text = timeFormat.format(time)
            
            // Добавляем эмодзи в зависимости от содержания
            when {
                chatMessage.message.contains("❌") -> {
                    textMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_error, 0)
                }
                chatMessage.message.contains("✅") -> {
                    textMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_success, 0)
                }
                chatMessage.message.contains("⚠️") -> {
                    textMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_warning, 0)
                }
                else -> {
                    textMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
        }
    }
}
