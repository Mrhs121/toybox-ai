package com.toybox.llmchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toybox.llmchat.data.PdfHelper
import com.toybox.llmchat.ui.theme.LLMChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PdfHelper.init(this)
        enableEdgeToEdge()
        setContent {
            LLMChatTheme {
                LLMChatApp()
            }
        }
    }
}
