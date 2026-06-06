package com.toybox.minipos.ui.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toybox.minipos.data.model.Product
import com.toybox.minipos.scanner.BarcodeScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel,
    onNavigateBack: () -> Unit
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val editingProduct by viewModel.editingProduct.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Product?>(null) }
    var showScannerForAdd by remember { mutableStateOf(false) }
    // Track if scanner was opened from within the dialog
    var scanFromDialog by remember { mutableStateOf(false) }

    // Stable callbacks to avoid recomposition of every list item
    val onEditProduct = remember<(Product) -> Unit> { { viewModel.setEditingProduct(it) } }
    val onDeleteProduct = remember<(Product) -> Unit> { { showDeleteConfirm = it } }

    // Add/Edit dialog
    // editingProduct with name blank = scan-to-add (pre-fill barcode only)
    // editingProduct with name not blank = edit existing
    val isEditMode = editingProduct != null && editingProduct!!.name.isNotBlank()
    if ((showAddDialog || editingProduct != null) && !showScannerForAdd) {
        ProductEditDialog(
            product = editingProduct,
            onDismiss = {
                showAddDialog = false
                viewModel.setEditingProduct(null)
            },
            onSave = { barcode, name, price, category ->
                if (isEditMode) {
                    viewModel.updateProduct(barcode, name, price, category)
                } else {
                    viewModel.addProduct(barcode, name, price, category)
                }
                showAddDialog = false
                viewModel.setEditingProduct(null)
            },
            onScanBarcode = {
                // Close dialog and open scanner, remember we came from dialog
                scanFromDialog = true
                showAddDialog = false
                viewModel.setEditingProduct(null)
                showScannerForAdd = true
            }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除商品") },
            text = { Text("确定要删除「${product.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(product.barcode)
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }

    // Scanner (from top bar or from dialog)
    if (showScannerForAdd) {
        BarcodeScannerScreen(
            onBarcodeScanned = { barcode ->
                showScannerForAdd = false
                if (scanFromDialog) {
                    // Re-open dialog with scanned barcode pre-filled
                    scanFromDialog = false
                    viewModel.setEditingProduct(Product(barcode = barcode, name = "", price = 0.0))
                    showAddDialog = true
                } else {
                    // From top bar: open dialog with scanned barcode
                    showAddDialog = true
                    viewModel.setEditingProduct(Product(barcode = barcode, name = "", price = 0.0))
                }
            },
            onClose = {
                showScannerForAdd = false
                scanFromDialog = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("商品管理")
                        if (products.isNotEmpty()) {
                            Text(
                                "共 ${products.size} 件商品",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showScannerForAdd = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码添加")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加商品") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索商品名称或条码") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isBlank()) "还没有商品" else "未找到匹配商品",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(products, key = { it.barcode }) { product ->
                        ProductItem(
                            product = product,
                            onEdit = { onEditProduct(product) },
                            onDelete = { onDeleteProduct(product) }
                        )
                        if (product != products.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductItem(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = product.barcode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (product.category.isNotBlank()) {
                Text(
                    text = product.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text(
            text = "¥%.2f".format(product.price),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
