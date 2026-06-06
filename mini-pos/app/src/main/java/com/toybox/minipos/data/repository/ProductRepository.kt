package com.toybox.minipos.data.repository

import com.toybox.minipos.data.local.ProductDao
import com.toybox.minipos.data.model.Product
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    fun searchProducts(query: String): Flow<List<Product>> = productDao.searchProducts(query)

    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)

    suspend fun insert(product: Product) = productDao.insert(product)

    suspend fun update(product: Product) = productDao.update(product)

    suspend fun deleteByBarcode(barcode: String) = productDao.deleteByBarcode(barcode)
}
