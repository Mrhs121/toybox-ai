package com.toybox.minipos.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_qr_codes")
data class PaymentQRCode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUri: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
