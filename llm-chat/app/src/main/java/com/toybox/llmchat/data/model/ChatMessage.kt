package com.toybox.llmchat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

@Serializable
enum class Role {
    USER, ASSISTANT, SYSTEM
}

@Serializable
data class Attachment(
    val fileName: String,
    val mimeType: String,
    val filePath: String,  // internal storage path, not base64
    val type: AttachmentType
)

@Serializable
enum class AttachmentType {
    IMAGE, FILE, PDF
}
