package com.toybox.llmchat.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toybox.llmchat.data.model.ApiConfig

@Composable
fun ModelSelectorInTopBar(
    configs: List<ApiConfig>,
    selectedConfig: ApiConfig?,
    onConfigSelected: (ApiConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(28.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedConfig?.modelName ?: "选择模型",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (configs.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("请先添加 API 配置") },
                    onClick = { expanded = false }
                )
            } else {
                configs.forEach { config ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = config.modelName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = config.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onConfigSelected(config)
                            expanded = false
                        },
                        trailingIcon = {
                            if (config.id == selectedConfig?.id) {
                                RadioButton(selected = true, onClick = null)
                            } else {
                                RadioButton(selected = false, onClick = null)
                            }
                        }
                    )
                }
            }
        }
    }
}
