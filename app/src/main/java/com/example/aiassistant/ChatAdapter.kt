package com.example.aiassistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) : 
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    
    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
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
        
        fun bind(chatMessage: ChatMessage) {
            textMessage.text = chatMessage.message
        }
    }
}
