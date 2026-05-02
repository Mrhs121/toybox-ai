package com.toybox.llmchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.toybox.llmchat.data.model.ChatMessage
import com.toybox.llmchat.data.model.Role

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER

    if (isUser) {
        // User message: blue bubble, right aligned
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 80.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content.ifEmpty { "..." },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    } else {
        // AI message: no bubble, plain text with action bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.content.ifEmpty { "..." },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action bar
            MessageActionBar(message = message)
        }
    }
}

@Composable
private fun MessageActionBar(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(message.content)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Like
        IconButton(
            onClick = { },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ThumbUp,
                contentDescription = "赞",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Dislike
        IconButton(
            onClick = { },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ThumbDown,
                contentDescription = "踩",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
