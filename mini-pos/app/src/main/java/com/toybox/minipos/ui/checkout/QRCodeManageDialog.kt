package com.toybox.minipos.ui.checkout

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.toybox.minipos.data.model.PaymentQRCode

@Composable
fun QRCodeManageDialog(
    qrCodes: List<PaymentQRCode>,
    onAdd: (name: String, imageUri: String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // State for adding new QR code
    var showAddForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Take persistent permission so the URI survives reboots
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            pickedUri = it
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收款码管理") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (qrCodes.isEmpty() && !showAddForm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "暂无收款码，点击下方添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(qrCodes, key = { it.id }) { qrCode ->
                            QRCodeItem(
                                qrCode = qrCode,
                                onDelete = { onDelete(qrCode.id) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Add form
                if (showAddForm) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "添加收款码",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("名称") },
                        placeholder = { Text("如：微信、支付宝") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    if (pickedUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(pickedUri),
                            contentDescription = "收款码预览",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (pickedUri != null) "重新选择" else "选择图片")
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank() && pickedUri != null) {
                                    onAdd(newName.trim(), pickedUri.toString())
                                    newName = ""
                                    pickedUri = null
                                    showAddForm = false
                                }
                            },
                            enabled = newName.isNotBlank() && pickedUri != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("保存")
                        }
                    }

                    TextButton(
                        onClick = {
                            showAddForm = false
                            newName = ""
                            pickedUri = null
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("取消")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加收款码")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun QRCodeItem(
    qrCode: PaymentQRCode,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除收款码") },
            text = { Text("确定删除「${qrCode.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(qrCode.imageUri)),
                contentDescription = qrCode.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = qrCode.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
