package com.toybox.minipos.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.toybox.minipos.data.model.CartItem
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Single-shot scanner for simple use cases (e.g. product edit dialog).
 */
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreview(onBarcodeScanned = onBarcodeScanned, onClose = onClose)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("需要摄像头权限才能扫描条码")
        }
    }
}

/**
 * Continuous scanner for checkout flow.
 * Shows camera + cart list + summary.
 */
@Composable
fun ContinuousScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    cartItems: List<CartItem>,
    cartItemCount: Int,
    cartTotal: Double,
    lastScannedName: String?,
    scanEventId: Long = 0L,
    onClose: () -> Unit,
    onManualInput: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        ContinuousCameraWithCart(
            onBarcodeScanned = onBarcodeScanned,
            cartItems = cartItems,
            cartItemCount = cartItemCount,
            cartTotal = cartTotal,
            lastScannedName = lastScannedName,
            scanEventId = scanEventId,
            onClose = onClose,
            onManualInput = onManualInput
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("需要摄像头权限才能扫描条码")
        }
    }
}

@Composable
private fun CameraPreview(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scannedBarcode by remember { mutableStateOf<String?>(null) }

    // Beep sound
    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME) } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImage(imageProxy) { barcode ->
                                    if (scannedBarcode == null) {
                                        scannedBarcode = barcode
                                        try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
                                        onBarcodeScanned(barcode)
                                    }
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭")
        }

        Text(
            text = "将条码对准框内",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ContinuousCameraWithCart(
    onBarcodeScanned: (String) -> Unit,
    cartItems: List<CartItem>,
    cartItemCount: Int,
    cartTotal: Double,
    lastScannedName: String?,
    scanEventId: Long,
    onClose: () -> Unit,
    onManualInput: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Beep sound — use STREAM_MUSIC for louder, more reliable playback
    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME) } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    // Cooldown: prevent the same barcode from being scanned repeatedly
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var canScan by remember { mutableStateOf(true) }

    // Feedback state
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackIsSuccess by remember { mutableStateOf(true) }
    var feedbackText by remember { mutableStateOf("") }

    // Reset cooldown after a delay, then clear so re-scanning the same barcode works
    LaunchedEffect(lastScannedBarcode) {
        if (lastScannedBarcode != null) {
            canScan = false
            delay(1500)
            lastScannedBarcode = null
            canScan = true
        }
    }

    // Show feedback briefly
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500)
            showFeedback = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera full screen behind everything
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImage(imageProxy) { barcode ->
                                    if (canScan && barcode != lastScannedBarcode) {
                                        lastScannedBarcode = barcode
                                        // Beep immediately on scan
                                        try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (_: Exception) {}
                                        onBarcodeScanned(barcode)
                                    }
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top overlay: close + hint (above status bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "连续扫码中",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        // Feedback overlay
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (feedbackIsSuccess)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (feedbackIsSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (feedbackIsSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom cart panel overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Cart items list (scrollable, max ~50% screen)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.50f)
                    .weight(1f, fill = false),
                shadowElevation = 8.dp,
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "扫描商品条码开始收银",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(cartItems, key = { it.product.barcode }) { item ->
                            ScannerCartItem(item = item)
                        }
                    }
                }
            }

            // Bottom summary + buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已扫 $cartItemCount 件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "¥%.2f".format(cartTotal),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onManualInput != null) {
                            OutlinedButton(
                                onClick = onManualInput,
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("手动输入", fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("完成", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // React to scan events — visual feedback only (beep already played at scan time)
    LaunchedEffect(scanEventId) {
        if (scanEventId > 0) {
            if (lastScannedName != null) {
                feedbackIsSuccess = true
                feedbackText = "已添加: $lastScannedName"
                showFeedback = true
            } else {
                feedbackIsSuccess = false
                feedbackText = "未找到商品"
                showFeedback = true
            }
        }
    }
}

@Composable
private fun ScannerCartItem(item: CartItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "¥%.2f × %d".format(item.product.price, item.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "¥%.2f".format(item.subtotal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun processImage(imageProxy: ImageProxy, onBarcode: (String) -> Unit) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                barcode.rawValue?.let { onBarcode(it) }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
