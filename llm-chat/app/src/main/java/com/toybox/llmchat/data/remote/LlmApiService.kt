package com.toybox.llmchat.data.remote

import com.aallam.openai.api.chat.ChatChunk
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.toybox.llmchat.data.model.ApiConfig
import com.toybox.llmchat.data.model.ChatMessage
import com.toybox.llmchat.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LlmApiService {

    private val clients = mutableMapOf<String, OpenAI>()

    private fun getClient(config: ApiConfig): OpenAI {
        return clients.getOrPut(config.id) {
            val cfg = OpenAIConfig(
                token = config.apiKey,
                host = OpenAIHost(baseUrl = config.baseUrl.trimEnd('/') + "/")
            )
            OpenAI(cfg)
        }
    }

    fun streamChat(
        config: ApiConfig,
        messages: List<ChatMessage>
    ): Flow<String> {
        val client = getClient(config)

        val request = ChatCompletionRequest(
            model = ModelId(config.modelId),
            messages = messages.map { msg ->
                OpenAiChatMessage(
                    role = when (msg.role) {
                        Role.USER -> ChatRole.User
                        Role.ASSISTANT -> ChatRole.Assistant
                        Role.SYSTEM -> ChatRole.System
                    },
                    content = msg.content
                )
            }
        )

        return client.chatCompletions(request).map { chunk: ChatCompletionChunk ->
            chunk.choices.firstOrNull()?.delta?.content ?: ""
        }
    }

    fun invalidateClient(configId: String) {
        clients.remove(configId)
    }
}
