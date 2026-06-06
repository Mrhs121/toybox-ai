package com.toybox.minipos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toybox.minipos.data.model.Product

@Composable
fun ProductEditDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (barcode: String, name: String, price: Double, category: String) -> Unit,
    onScanBarcode: (() -> Unit)? = null
) {
    val isEdit = product != null && product.name.isNotBlank()
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var name by remember { mutableStateOf(product?.name ?: "") }
    var priceText by remember { mutableStateOf(if (product != null && product.price > 0) product.price.toString() else "") }
    var category by remember { mutableStateOf(product?.category ?: "") }

    // Update barcode when product changes (e.g. from scan result)
    LaunchedEffect(product?.barcode) {
        if (product != null && product.barcode.isNotBlank() && product.name.isBlank()) {
            barcode = product.barcode
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑商品" else "添加商品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("条形码") },
                    singleLine = true,
                    enabled = !isEdit,
                    trailingIcon = {
                        if (!isEdit && onScanBarcode != null) {
                            IconButton(onClick = onScanBarcode) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "扫码",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("商品名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("售价 (元)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val price = priceText.toDoubleOrNull()
            val canSave = barcode.isNotBlank() && name.isNotBlank() && price != null && price > 0
            TextButton(
                onClick = { onSave(barcode.trim(), name.trim(), price!!, category.trim()) },
                enabled = canSave
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
