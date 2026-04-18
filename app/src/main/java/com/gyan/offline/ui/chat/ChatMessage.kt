package com.gyan.offline.ui.chat

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val isOutOfDomain: Boolean = false,
    val lang: String = "en",
)
