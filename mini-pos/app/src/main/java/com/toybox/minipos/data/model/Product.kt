package com.toybox.minipos.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val price: Double,
    val category: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
