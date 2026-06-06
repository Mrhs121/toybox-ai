package com.toybox.minipos.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.minipos.MiniPosApp
import com.toybox.minipos.data.model.Product
import com.toybox.minipos.data.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MiniPosApp).database
    private val productRepo = ProductRepository(db.productDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val products: StateFlow<List<Product>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) productRepo.allProducts
        else productRepo.searchProducts(query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _editingProduct = MutableStateFlow<Product?>(null)
    val editingProduct: StateFlow<Product?> = _editingProduct.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addProduct(barcode: String, name: String, price: Double, category: String) {
        viewModelScope.launch {
            productRepo.insert(Product(barcode = barcode, name = name, price = price, category = category))
        }
    }

    fun updateProduct(barcode: String, name: String, price: Double, category: String) {
        viewModelScope.launch {
            productRepo.update(Product(barcode = barcode, name = name, price = price, category = category))
        }
    }

    fun deleteProduct(barcode: String) {
        viewModelScope.launch {
            productRepo.deleteByBarcode(barcode)
        }
    }

    fun setEditingProduct(product: Product?) {
        _editingProduct.value = product
    }
}
