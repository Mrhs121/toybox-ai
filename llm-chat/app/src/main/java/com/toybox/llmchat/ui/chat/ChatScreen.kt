package com.toybox.llmchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toybox.llmchat.ui.history.HistoryDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val configs by viewModel.configs.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(currentMessages.size, currentMessages.lastOrNull()?.content?.length) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && currentMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                conversations = conversations,
                currentConversationId = currentConvId,
                onConversationClick = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewConversation = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { viewModel.deleteConversation(it) }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "LLM Chat",
                                style = MaterialTheme.typography.titleMedium
                            )
                            ModelSelectorInTopBar(
                                configs = configs,
                                selectedConfig = selectedConfig,
                                onConfigSelected = { viewModel.selectConfig(it) }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "历史对话")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                error?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (currentMessages.isEmpty()) {
                        item {
                            EmptyStateView(configs = configs)
                        }
                    }
                    items(currentMessages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "语音",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        maxLines = 5,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        decorationBox = { innerTextField ->
                                            Box {
                                                if (inputText.isEmpty()) {
                                                    Text(
                                                        "尽管问，带图也行",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )

                                    FilledIconButton(
                                        onClick = {
                                            viewModel.sendMessage(inputText)
                                            inputText = ""
                                        },
                                        enabled = inputText.isNotBlank() && !isGenerating,
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            disabledContainerColor = Color.Transparent,
                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        if (isGenerating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "发送",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = "附件",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickActionChip("Agent")
                            QuickActionChip("PPT")
                            QuickActionChip("搜索")
                            QuickActionChip("翻译")
                            QuickActionChip("写作")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EmptyStateView(configs: List<*>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (configs.isEmpty()) "请先在设置中添加 API 配置" else "有什么可以帮你的？",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
