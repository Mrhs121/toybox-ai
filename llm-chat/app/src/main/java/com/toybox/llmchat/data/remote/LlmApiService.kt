package com.toybox.llmchat.data.remote

import android.util.Base64
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ContentPart
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.toybox.llmchat.data.model.ApiConfig
import com.toybox.llmchat.data.model.AttachmentType
import com.toybox.llmchat.data.model.ChatMessage
import com.toybox.llmchat.data.model.Role
import com.toybox.llmchat.data.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LlmApiService {

    private val clients = mutableMapOf<String, OpenAI>()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

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
        messages: List<ChatMessage>,
        context: android.content.Context? = null
    ): Flow<String> {
        val hasNonImageAttachments = messages.any { msg ->
            msg.attachments.isNotEmpty() && msg.role == Role.USER &&
                msg.attachments.any { it.type != AttachmentType.IMAGE }
        }
        return if (hasNonImageAttachments) {
            streamWithAttachments(config, messages, context)
        } else {
            streamViaSdk(config, messages)
        }
    }

    private fun streamViaSdk(config: ApiConfig, messages: List<ChatMessage>): Flow<String> {
        val client = getClient(config)
        val request = ChatCompletionRequest(
            model = ModelId(config.modelId),
            messages = messages.map { msg ->
                val role = when (msg.role) {
                    Role.USER -> ChatRole.User
                    Role.ASSISTANT -> ChatRole.Assistant
                    Role.SYSTEM -> ChatRole.System
                }
                val imageAttachments = msg.attachments.filter { it.type == AttachmentType.IMAGE }
                if (imageAttachments.isNotEmpty()) {
                    val parts = mutableListOf<ContentPart>()
                    if (msg.content.isNotBlank()) {
                        parts.add(TextPart(msg.content))
                    }
                    for (att in imageAttachments) {
                        val file = java.io.File(att.filePath)
                        if (file.exists()) {
                            val bytes = file.readBytes()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            parts.add(ImagePart(url = "data:${att.mimeType};base64,$base64"))
                        }
                    }
                    OpenAiChatMessage(role = role, content = parts)
                } else {
                    OpenAiChatMessage(role = role, content = msg.content)
                }
            }
        )
        return client.chatCompletions(request).map { chunk ->
            chunk.choices.firstOrNull()?.delta?.content ?: ""
        }
    }

    private fun streamWithAttachments(
        config: ApiConfig,
        messages: List<ChatMessage>,
        context: android.content.Context? = null
    ): Flow<String> = flow {
        val baseUrl = config.baseUrl.trimEnd('/')
        val url = "$baseUrl/chat/completions"
        val jsonMessages = buildJsonArray {
            for (msg in messages) {
                val role = when (msg.role) {
                    Role.USER -> "user"
                    Role.ASSISTANT -> "assistant"
                    Role.SYSTEM -> "system"
                }
                if (msg.attachments.isNotEmpty() && msg.role == Role.USER) {
                    val parts = buildJsonArray {
                        if (msg.content.isNotBlank()) {
                            add(buildJsonObject {
                                put("type", JsonPrimitive("text"))
                                put("text", JsonPrimitive(msg.content))
                            })
                        }
                        for (att in msg.attachments) {
                            val file = java.io.File(att.filePath)
                            if (!file.exists()) continue
                            when (att.type) {
                                AttachmentType.IMAGE -> {
                                    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    val dataUri = "data:${att.mimeType};base64,$base64"
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put("image_url", buildJsonObject {
                                            put("url", JsonPrimitive(dataUri))
                                        })
                                    })
                                }
                                AttachmentType.PDF -> {
                                    if (context != null) {
                                        val pdfResult = PdfHelper.parsePdf(context, android.net.Uri.fromFile(file))
                                        if (!pdfResult.text.isNullOrBlank()) {
                                            add(buildJsonObject {
                                                put("type", JsonPrimitive("text"))
                                                put("text", JsonPrimitive("[PDF: ${att.fileName}]\n${pdfResult.text}"))
                                            })
                                        } else if (pdfResult.images != null) {
                                            for (imgDataUri in pdfResult.images) {
                                                add(buildJsonObject {
                                                    put("type", JsonPrimitive("image_url"))
                                                    put("image_url", buildJsonObject {
                                                        put("url", JsonPrimitive(imgDataUri))
                                                    })
                                                })
                                            }
                                        }
                                    } else {
                                        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        val dataUri = "data:${att.mimeType};base64,$base64"
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive("file"))
                                            put("file", buildJsonObject {
                                                put("filename", JsonPrimitive(att.fileName))
                                                put("file_data", JsonPrimitive(dataUri))
                                            })
                                        })
                                    }
                                }
                                AttachmentType.FILE -> {
                                    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    val dataUri = "data:${att.mimeType};base64,$base64"
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("file"))
                                        put("file", buildJsonObject {
                                            put("filename", JsonPrimitive(att.fileName))
                                            put("file_data", JsonPrimitive(dataUri))
                                        })
                                    })
                                }
                            }
                        }
                    }
                    add(buildJsonObject {
                        put("role", JsonPrimitive(role))
                        put("content", parts)
                    })
                } else {
                    add(buildJsonObject {
                        put("role", JsonPrimitive(role))
                        put("content", JsonPrimitive(msg.content))
                    })
                }
            }
        }
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(config.modelId))
            put("stream", JsonPrimitive(true))
            put("messages", jsonMessages)
        }
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = withContext(Dispatchers.IO) { okHttpClient.newCall(httpRequest).execute() }
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("API request failed: ${response.code} - $errorBody")
        }
        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = Json.parseToJsonElement(data).jsonObject
                        val choices = chunk["choices"]?.jsonArray
                        val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                        val contentElement = delta?.get("content")
                        if (contentElement != null && contentElement !is JsonNull && contentElement is JsonPrimitive) {
                            val content = contentElement.content
                            if (content != null) emit(content)
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            reader.close()
            response.body?.close()
        }
    }.flowOn(Dispatchers.IO)

    fun invalidateClient(configId: String) {
        clients.remove(configId)
    }
}