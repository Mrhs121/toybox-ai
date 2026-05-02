package com.toybox.llmchat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class Role {
    USER, ASSISTANT, SYSTEM
}
