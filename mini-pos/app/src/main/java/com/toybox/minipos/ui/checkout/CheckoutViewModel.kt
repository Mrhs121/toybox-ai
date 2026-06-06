package com.toybox.minipos.ui.checkout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.minipos.MiniPosApp
import com.toybox.minipos.data.model.*
import com.toybox.minipos.data.repository.OrderRepository
import com.toybox.minipos.data.repository.PaymentQRCodeRepository
import com.toybox.minipos.data.repository.ProductRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class CheckoutViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MiniPosApp).database
    private val productRepo = ProductRepository(db.productDao())
    private val orderRepo = OrderRepository(db.orderDao())
    private val qrCodeRepo = PaymentQRCodeRepository(db.paymentQRCodeDao())

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _checkoutSuccess = MutableStateFlow<Double?>(null)
    val checkoutSuccess: StateFlow<Double?> = _checkoutSuccess.asStateFlow()

    // Track last scan result for continuous scanner feedback
    private val _lastScanResult = MutableStateFlow<String?>(null)
    val lastScanResult: StateFlow<String?> = _lastScanResult.asStateFlow()
    // Incremented on every scan attempt so the scanner UI can react
    private val _scanEventId = MutableStateFlow(0L)
    val scanEventId: StateFlow<Long> = _scanEventId.asStateFlow()

    val totalAmount: StateFlow<Double> = _cartItems.map { items ->
        items.sumOf { it.subtotal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalItems: StateFlow<Int> = _cartItems.map { items ->
        items.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Payment QR codes
    val qrCodes: StateFlow<List<PaymentQRCode>> = qrCodeRepo.allQRCodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addQRCode(name: String, imageUri: String) {
        viewModelScope.launch {
            qrCodeRepo.insert(PaymentQRCode(name = name, imageUri = imageUri))
        }
    }

    fun deleteQRCode(id: Long) {
        viewModelScope.launch {
            qrCodeRepo.deleteById(id)
        }
    }

    fun addProductByBarcode(barcode: String) {
        viewModelScope.launch {
            val product = productRepo.getProductByBarcode(barcode)
            if (product != null) {
                val currentItems = _cartItems.value.toMutableList()
                val existingIndex = currentItems.indexOfFirst { it.product.barcode == barcode }
                if (existingIndex >= 0) {
                    currentItems[existingIndex] = currentItems[existingIndex].let {
                        it.copy(quantity = it.quantity + 1)
                    }
                } else {
                    currentItems.add(CartItem(product))
                }
                _cartItems.value = currentItems
                _error.value = null
                _lastScanResult.value = product.name
            } else {
                _error.value = "未找到条码: $barcode"
                _lastScanResult.value = null
            }
            _scanEventId.value++
        }
    }

    fun addProductDirectly(product: Product) {
        val currentItems = _cartItems.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.product.barcode == product.barcode }
        if (existingIndex >= 0) {
            currentItems[existingIndex] = currentItems[existingIndex].let {
                it.copy(quantity = it.quantity + 1)
            }
        } else {
            currentItems.add(CartItem(product))
        }
        _cartItems.value = currentItems
    }

    fun updateQuantity(barcode: String, delta: Int) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.product.barcode == barcode }
        if (index >= 0) {
            val newQty = currentItems[index].quantity + delta
            if (newQty <= 0) {
                currentItems.removeAt(index)
            } else {
                currentItems[index] = currentItems[index].copy(quantity = newQty)
            }
            _cartItems.value = currentItems
        }
    }

    fun removeItem(barcode: String) {
        _cartItems.value = _cartItems.value.filter { it.product.barcode != barcode }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _error.value = null
        _checkoutSuccess.value = null
    }

    fun checkout() {
        viewModelScope.launch {
            val items = _cartItems.value
            if (items.isEmpty()) return@launch

            // Generate order number: yyyyMMdd-NNN
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayCount = orderRepo.getOrderCountSince(cal.timeInMillis)
            val orderNo = "$dateStr-${String.format("%03d", todayCount + 1)}"

            val orderItems = items.map { item ->
                OrderItem(
                    barcode = item.product.barcode,
                    name = item.product.name,
                    price = item.product.price,
                    quantity = item.quantity,
                    subtotal = item.subtotal
                )
            }
            val order = Order(
                orderNo = orderNo,
                totalAmount = items.sumOf { it.subtotal },
                itemCount = items.sumOf { it.quantity },
                itemsJson = Json.encodeToString(orderItems)
            )
            orderRepo.insert(order)
            val completedAmount = items.sumOf { it.subtotal }
            _cartItems.value = emptyList()
            _checkoutSuccess.value = completedAmount
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearCheckoutSuccess() {
        _checkoutSuccess.value = null
    }
}
