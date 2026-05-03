package com.toybox.llmchat.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toybox.llmchat.data.model.AttachmentType
import com.toybox.llmchat.data.model.ChatMessage
import com.toybox.llmchat.data.model.Role

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 80.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                // Image attachments
                for (attachment in message.attachments.filter { it.type == AttachmentType.IMAGE }) {
                    val file = java.io.File(attachment.filePath)
                    if (file.exists()) {
                        val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = attachment.fileName,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                // File attachments
                for (attachment in message.attachments.filter { it.type == AttachmentType.FILE }) {
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Description, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = attachment.fileName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Text content
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
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {
            SelectionContainer {
                MarkdownText(text = message.content.ifEmpty { "..." })
            }

            Spacer(modifier = Modifier.height(8.dp))

            MessageActionBar(message = message)
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val paragraphs = text.split("\n\n")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { paragraph ->
            val trimmed = paragraph.trim()
            when {
                trimmed.startsWith("```") -> {
                    val code = trimmed.removePrefix("```").removeSuffix("```").trim()
                    val firstLine = code.lines().firstOrNull() ?: ""
                    val lang = if (firstLine.length in 1..20 && firstLine.all { it.isLetter() || it == '+' || it == '#' }) firstLine else ""
                    val codeContent = if (lang.isNotEmpty() && code.startsWith(lang)) {
                        code.removePrefix(lang).trimStart('\n')
                    } else {
                        code
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (lang.isNotEmpty()) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = codeContent,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                trimmed.startsWith("### ") -> {
                    StyledMarkdownText(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                trimmed.startsWith("## ") -> {
                    StyledMarkdownText(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                trimmed.startsWith("# ") -> {
                    StyledMarkdownText(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val items = trimmed.lines().map { it.removePrefix("- ").removePrefix("* ").trim() }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEach { item ->
                            Row {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                StyledMarkdownText(text = item)
                            }
                        }
                    }
                }
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val items = trimmed.lines().map { it.replace(Regex("^\\d+\\.\\s"), "").trim() }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEachIndexed { index, item ->
                            Row {
                                Text(
                                    text = "${index + 1}. ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                StyledMarkdownText(text = item)
                            }
                        }
                    }
                }
                else -> {
                    StyledMarkdownText(text = trimmed)
                }
            }
        }
    }
}

@Composable
private fun StyledMarkdownText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val annotatedString = buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Inline code `...`
            val codeStart = remaining.indexOf('`')
            if (codeStart >= 0) {
                val codeEnd = remaining.indexOf('`', codeStart + 1)
                if (codeEnd > codeStart) {
                    if (codeStart > 0) {
                        appendInlineStyles(remaining.substring(0, codeStart))
                    }
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.onSurface
                    ))
                    append(remaining.substring(codeStart + 1, codeEnd))
                    pop()
                    remaining = remaining.substring(codeEnd + 1)
                    continue
                }
            }
            appendInlineStyles(remaining)
            break
        }
    }

    Text(
        text = annotatedString,
        style = style,
        color = MaterialTheme.colorScheme.onBackground
    )
}

private fun AnnotatedString.Builder.appendInlineStyles(text: String) {
    var remaining = text
    while (remaining.isNotEmpty()) {
        // Bold **...** or __...__
        val boldStart = remaining.indexOf("**").takeIf { it >= 0 }
            ?: remaining.indexOf("__").takeIf { it >= 0 }
        if (boldStart != null) {
            val delimiter = remaining.substring(boldStart, boldStart + 2)
            val boldEnd = remaining.indexOf(delimiter, boldStart + 2)
            if (boldEnd > boldStart) {
                if (boldStart > 0) {
                    append(remaining.substring(0, boldStart))
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(remaining.substring(boldStart + 2, boldEnd))
                pop()
                remaining = remaining.substring(boldEnd + 2)
                continue
            }
        }
        // Italic *...* (single *, not **)
        val italicStart = remaining.indexOf("*").takeIf { it >= 0 }
        if (italicStart != null && italicStart + 1 < remaining.length && remaining[italicStart + 1] != '*') {
            val italicEnd = remaining.indexOf("*", italicStart + 1)
            if (italicEnd > italicStart) {
                if (italicStart > 0) {
                    append(remaining.substring(0, italicStart))
                }
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(remaining.substring(italicStart + 1, italicEnd))
                pop()
                remaining = remaining.substring(italicEnd + 1)
                continue
            }
        }
        // No more inline styles
        append(remaining)
        break
    }
}

@Composable
private fun MessageActionBar(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
