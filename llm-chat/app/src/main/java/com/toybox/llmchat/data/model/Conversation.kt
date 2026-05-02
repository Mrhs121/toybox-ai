package com.toybox.llmchat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
