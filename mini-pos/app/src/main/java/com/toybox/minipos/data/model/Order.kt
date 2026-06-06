package com.toybox.minipos.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNo: String = "",
    val totalAmount: Double,
    val itemCount: Int,
    val itemsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class OrderItem(
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Int,
    val subtotal: Double
)
