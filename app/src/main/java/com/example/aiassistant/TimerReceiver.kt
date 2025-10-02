package com.example.aiassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Таймер"
        Toast.makeText(context, "⏱️ $message - время вышло!", Toast.LENGTH_LONG).show()
    }
}
