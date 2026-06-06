package com.toybox.minipos.data.local

import androidx.room.*
import com.toybox.minipos.data.model.PaymentQRCode
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentQRCodeDao {
    @Query("SELECT * FROM payment_qr_codes ORDER BY displayOrder, createdAt")
    fun getAllQRCodes(): Flow<List<PaymentQRCode>>

    @Insert
    suspend fun insert(qrCode: PaymentQRCode): Long

    @Update
    suspend fun update(qrCode: PaymentQRCode)

    @Query("DELETE FROM payment_qr_codes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
