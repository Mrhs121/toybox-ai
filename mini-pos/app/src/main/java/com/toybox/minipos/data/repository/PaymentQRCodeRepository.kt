package com.toybox.minipos.data.repository

import com.toybox.minipos.data.local.PaymentQRCodeDao
import com.toybox.minipos.data.model.PaymentQRCode
import kotlinx.coroutines.flow.Flow

class PaymentQRCodeRepository(private val dao: PaymentQRCodeDao) {
    val allQRCodes: Flow<List<PaymentQRCode>> = dao.getAllQRCodes()

    suspend fun insert(qrCode: PaymentQRCode): Long = dao.insert(qrCode)

    suspend fun update(qrCode: PaymentQRCode) = dao.update(qrCode)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
