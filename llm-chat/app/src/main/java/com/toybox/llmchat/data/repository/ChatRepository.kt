package com.toybox.llmchat.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toybox.llmchat.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.chatStore: DataStore<Preferences> by preferencesDataStore("conversations")

class ChatRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val conversationsKey = stringPreferencesKey("conversations")

    val conversations: Flow<List<Conversation>> = context.chatStore.data.map { prefs ->
        val raw = prefs[conversationsKey] ?: "[]"
        json.decodeFromString<List<Conversation>>(raw).sortedByDescending { it.updatedAt }
    }

    suspend fun save(conversations: List<Conversation>) {
        context.chatStore.edit { prefs ->
            prefs[conversationsKey] = json.encodeToString(conversations)
        }
    }

    suspend fun upsert(conversation: Conversation) {
        context.chatStore.edit { prefs ->
            val list = json.decodeFromString<List<Conversation>>(prefs[conversationsKey] ?: "[]")
            val updated = list.map { if (it.id == conversation.id) conversation else it }
            prefs[conversationsKey] = json.encodeToString(
                if (updated.any { it.id == conversation.id }) updated else updated + conversation
            )
        }
    }

    suspend fun delete(id: String) {
        context.chatStore.edit { prefs ->
            val list = json.decodeFromString<List<Conversation>>(prefs[conversationsKey] ?: "[]")
            prefs[conversationsKey] = json.encodeToString(list.filter { it.id != id })
        }
    }
}
