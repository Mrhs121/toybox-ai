package com.toybox.llmchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.llmchat.data.model.*
import com.toybox.llmchat.data.remote.LlmApiService
import com.toybox.llmchat.data.repository.ChatRepository
import com.toybox.llmchat.data.repository.ConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepo = ConfigRepository(application)
    private val chatRepo = ChatRepository(application)
    private val apiService = LlmApiService()

    val configs: StateFlow<List<ApiConfig>> = configRepo.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<Conversation>> = chatRepo.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId

    private val _selectedConfig = MutableStateFlow<ApiConfig?>(null)
    val selectedConfig: StateFlow<ApiConfig?> = _selectedConfig

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Cache current conversation to avoid linear search on every streaming chunk
    private var cachedConversation: Conversation? = null
    private var streamingJob: Job? = null
    private var pendingPersistJob: Job? = null

    val currentMessages: StateFlow<List<ChatMessage>> = combine(
        conversations, _currentConversationId
    ) { convos, id ->
        val convo = convos.find { it.id == id }
        cachedConversation = convo
        convo?.messages ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            configs.collect { list ->
                if (_selectedConfig.value == null && list.isNotEmpty()) {
                    _selectedConfig.value = list.first()
                }
                if (_selectedConfig.value != null && list.none { it.id == _selectedConfig.value!!.id }) {
                    _selectedConfig.value = list.firstOrNull()
                }
            }
        }
    }

    fun selectConfig(config: ApiConfig) {
        _selectedConfig.value = config
    }

    fun newConversation() {
        cancelStreaming()
        _currentConversationId.value = null
    }

    fun selectConversation(id: String) {
        cancelStreaming()
        _currentConversationId.value = id
    }

    fun deleteConversation(id: String) {
        cancelStreaming()
        viewModelScope.launch {
            chatRepo.delete(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
        }
    }

    fun sendMessage(content: String) {
        val config = _selectedConfig.value ?: run {
            _error.value = "请先在设置中添加 API 配置"
            return
        }
        if (content.isBlank() || _isGenerating.value) return

        cancelStreaming()

        streamingJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            val convId = _currentConversationId.value ?: UUID.randomUUID().toString().also {
                _currentConversationId.value = it
            }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = Role.USER,
                content = content
            )

            val currentConvo = cachedConversation
            val title = currentConvo?.title ?: content.take(50)
            val updatedMessages = (currentConvo?.messages ?: emptyList()) + userMsg

            val updatedConvo = Conversation(
                id = convId,
                title = title,
                messages = updatedMessages,
                createdAt = currentConvo?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            chatRepo.upsert(updatedConvo)
            cachedConversation = updatedConvo

            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                role = Role.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis()
            )
            val convoWithAssistant = updatedConvo.copy(
                messages = updatedMessages + assistantMsg,
                updatedAt = System.currentTimeMillis()
            )
            chatRepo.upsert(convoWithAssistant)
            cachedConversation = convoWithAssistant

            try {
                // Batch persist: accumulate chunks and write to DataStore every 500ms
                var pendingConversation = convoWithAssistant
                var hasPendingChanges = false

                apiService.streamChat(config, updatedMessages).collect { chunk ->
                    val msgs = pendingConversation.messages.toMutableList()
                    val idx = msgs.indexOfFirst { it.id == assistantMsgId }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(content = msgs[idx].content + chunk)
                        pendingConversation = pendingConversation.copy(
                            messages = msgs,
                            updatedAt = System.currentTimeMillis()
                        )
                        cachedConversation = pendingConversation
                        hasPendingChanges = true
                    }
                }

                // Final persist
                if (hasPendingChanges) {
                    chatRepo.upsert(pendingConversation)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "请求失败"
                val convo = cachedConversation
                if (convo != null) {
                    val msgs = convo.messages.filterNot { it.id == assistantMsgId && it.content.isEmpty() }
                    val cleaned = convo.copy(messages = msgs)
                    chatRepo.upsert(cleaned)
                    cachedConversation = cleaned
                }
            } finally {
                _isGenerating.value = false
                streamingJob = null
            }
        }
    }

    private fun cancelStreaming() {
        streamingJob?.cancel()
        streamingJob = null
    }

    fun clearError() {
        _error.value = null
    }
}
