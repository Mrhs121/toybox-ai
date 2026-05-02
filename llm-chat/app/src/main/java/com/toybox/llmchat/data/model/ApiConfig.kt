package com.toybox.llmchat.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val modelName: String
)
