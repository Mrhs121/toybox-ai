package com.toybox.llmchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.llmchat.data.model.*
import com.toybox.llmchat.data.remote.LlmApiService
import com.toybox.llmchat.data.repository.ChatRepository
import com.toybox.llmchat.data.repository.ConfigRepository
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

    val currentMessages: StateFlow<List<ChatMessage>> = combine(
        conversations, _currentConversationId
    ) { convos, id ->
        convos.find { it.id == id }?.messages ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            configs.collect { list ->
                if (_selectedConfig.value == null && list.isNotEmpty()) {
                    _selectedConfig.value = list.first()
                }
                // Remove selection if config was deleted
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
        _currentConversationId.value = null
    }

    fun selectConversation(id: String) {
        _currentConversationId.value = id
    }

    fun deleteConversation(id: String) {
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

        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            // Get or create conversation
            val convId = _currentConversationId.value ?: UUID.randomUUID().toString().also {
                _currentConversationId.value = it
            }

            // Add user message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = Role.USER,
                content = content
            )

            val currentConvo = conversations.value.find { it.id == convId }
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

            // Stream response
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                role = Role.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis()
            )
            chatRepo.upsert(updatedConvo.copy(
                messages = updatedMessages + assistantMsg,
                updatedAt = System.currentTimeMillis()
            ))

            try {
                apiService.streamChat(config, updatedMessages).collect { chunk ->
                    val convo = conversations.value.find { it.id == convId } ?: return@collect
                    val msgs = convo.messages.toMutableList()
                    val idx = msgs.indexOfFirst { it.id == assistantMsgId }
                    if (idx >= 0) {
                        msgs[idx] = msgs[idx].copy(content = msgs[idx].content + chunk)
                        chatRepo.upsert(convo.copy(messages = msgs, updatedAt = System.currentTimeMillis()))
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "请求失败"
                // Remove empty assistant message on error
                val convo = conversations.value.find { it.id == convId }
                if (convo != null) {
                    val msgs = convo.messages.filterNot { it.id == assistantMsgId && it.content.isEmpty() }
                    chatRepo.upsert(convo.copy(messages = msgs))
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
