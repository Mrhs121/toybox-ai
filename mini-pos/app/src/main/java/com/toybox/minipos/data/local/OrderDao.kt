package com.toybox.minipos.data.local

import androidx.room.*
import com.toybox.minipos.data.model.Order
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE createdAt BETWEEN :startTime AND :endTime ORDER BY createdAt DESC")
    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<Order>>

    @Query("SELECT COALESCE(SUM(totalAmount), 0.0) FROM orders WHERE createdAt BETWEEN :startTime AND :endTime")
    fun getTotalAmountBetween(startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM orders WHERE createdAt BETWEEN :startTime AND :endTime")
    fun getOrderCountBetween(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE createdAt >= :startTime")
    suspend fun getOrderCountSince(startTime: Long): Int

    @Insert
    suspend fun insert(order: Order): Long
}
