package com.toybox.minipos.ui.checkout

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.toybox.minipos.data.model.CartItem
import com.toybox.minipos.ui.theme.PriceColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
    onNavigateToProducts: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val totalItems by viewModel.totalItems.collectAsState()
    val error by viewModel.error.collectAsState()
    val checkoutSuccess by viewModel.checkoutSuccess.collectAsState()

    val qrCodes by viewModel.qrCodes.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showQRCodeManage by remember { mutableStateOf(false) }

    // Checkout success dialog
    val successAmount = checkoutSuccess
    if (successAmount != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCheckoutSuccess() },
            title = { Text("结算成功") },
            text = {
                Text(
                    "¥%.2f".format(successAmount),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = PriceColor
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCheckoutSuccess() }) {
                    Text("确定")
                }
            }
        )
    }

    // Checkout confirmation dialog — shows QR codes if available
    if (showCheckoutDialog) {
        if (qrCodes.isNotEmpty()) {
            var selectedTabIndex by remember { mutableIntStateOf(0) }

            AlertDialog(
                onDismissRequest = { showCheckoutDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("确认结算")
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "共 $totalItems 件商品",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "¥%.2f".format(totalAmount),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriceColor
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.fillMaxWidth(),
                            edgePadding = 0.dp
                        ) {
                            qrCodes.forEachIndexed { index, qr ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(qr.name) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        val currentQR = qrCodes.getOrNull(selectedTabIndex)
                        if (currentQR != null) {
                            Image(
                                painter = rememberAsyncImagePainter(Uri.parse(currentQR.imageUri)),
                                contentDescription = currentQR.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.checkout()
                        showCheckoutDialog = false
                    }) {
                        Text("确认收款")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = false }) {
                        Text("取消")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showCheckoutDialog = false },
                title = { Text("确认结算") },
                text = {
                    Column {
                        Text("共 $totalItems 件商品")
                        Text(
                            "合计: ¥%.2f".format(totalAmount),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = PriceColor
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.checkout()
                        showCheckoutDialog = false
                    }) {
                        Text("确认付款")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    // Manual barcode input dialog
    if (showManualInput) {
        AlertDialog(
            onDismissRequest = { showManualInput = false },
            title = { Text("手动输入条码") },
            text = {
                OutlinedTextField(
                    value = manualBarcode,
                    onValueChange = { manualBarcode = it },
                    label = { Text("条码") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualBarcode.isNotBlank()) {
                        viewModel.addProductByBarcode(manualBarcode.trim())
                        manualBarcode = ""
                        showManualInput = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showManualInput = false; manualBarcode = "" }) { Text("取消") }
            }
        )
    }

    // QR code management dialog
    if (showQRCodeManage) {
        QRCodeManageDialog(
            qrCodes = qrCodes,
            onAdd = { name, uri -> viewModel.addQRCode(name, uri) },
            onDelete = { id -> viewModel.deleteQRCode(id) },
            onDismiss = { showQRCodeManage = false }
        )
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("收银台") },
                actions = {
                    TextButton(onClick = { showQRCodeManage = true }) {
                        Icon(Icons.Default.QrCode2, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("收款码")
                    }
                    TextButton(onClick = onNavigateToProducts) {
                        Icon(Icons.Default.Inventory2, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("商品管理")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scan button — primary visual focus
            Button(
                onClick = onOpenScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "扫码结账",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Secondary actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showManualInput = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("手动输入")
                }
                if (cartItems.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearCart() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清空")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cart items list
            if (cartItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "购物车为空",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "扫描商品条码或手动输入开始收银",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartItems, key = { it.product.barcode }) { item ->
                        CartItemCard(
                            item = item,
                            onIncrease = { viewModel.updateQuantity(item.product.barcode, 1) },
                            onDecrease = { viewModel.updateQuantity(item.product.barcode, -1) },
                            onRemove = { viewModel.removeItem(item.product.barcode) }
                        )
                    }
                }
            }

            // Bottom summary bar
            AnimatedVisibility(visible = cartItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "共 $totalItems 件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "¥%.2f".format(totalAmount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PriceColor
                            )
                        }
                        Button(
                            onClick = { showCheckoutDialog = true },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PriceColor
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("结算", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "¥%.2f".format(item.product.price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PriceColor
                )
            }

            // Quantity controls — fixed width so it doesn't shift
            Row(
                modifier = Modifier.width(96.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "减少", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 20.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onIncrease, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "增加", modifier = Modifier.size(16.dp))
                }
            }

            // Subtotal — fixed width right-aligned
            Column(
                modifier = Modifier.width(96.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "¥%.2f".format(item.subtotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PriceColor
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
