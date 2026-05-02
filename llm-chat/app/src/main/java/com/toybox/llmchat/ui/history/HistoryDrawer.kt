package com.toybox.llmchat.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toybox.llmchat.data.model.Conversation

@Composable
fun HistoryDrawer(
    conversations: List<Conversation>,
    currentConversationId: String?,
    onConversationClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "历史对话",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // New conversation button
        FilledTonalButton(
            onClick = onNewConversation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("新对话")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    val isSelected = conversation.id == currentConversationId

                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = conversation.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        badge = {
                            IconButton(
                                onClick = { showDeleteDialog = conversation.id },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        selected = isSelected,
                        onClick = { onConversationClick(conversation.id) },
                        modifier = Modifier.padding(vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { convId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(convId)
                    showDeleteDialog = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}
