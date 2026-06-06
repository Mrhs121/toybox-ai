package com.toybox.minipos.data.repository

import com.toybox.minipos.data.local.OrderDao
import com.toybox.minipos.data.model.Order
import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {
    val allOrders: Flow<List<Order>> = orderDao.getAllOrders()

    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<Order>> =
        orderDao.getOrdersBetween(startTime, endTime)

    fun getTotalAmountBetween(startTime: Long, endTime: Long): Flow<Double> =
        orderDao.getTotalAmountBetween(startTime, endTime)

    fun getOrderCountBetween(startTime: Long, endTime: Long): Flow<Int> =
        orderDao.getOrderCountBetween(startTime, endTime)

    suspend fun insert(order: Order): Long = orderDao.insert(order)

    suspend fun getOrderCountSince(startTime: Long): Int = orderDao.getOrderCountSince(startTime)
}
