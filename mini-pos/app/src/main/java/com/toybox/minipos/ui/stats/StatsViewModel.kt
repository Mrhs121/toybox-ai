package com.toybox.minipos.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.minipos.MiniPosApp
import com.toybox.minipos.data.model.Order
import com.toybox.minipos.data.model.OrderItem
import com.toybox.minipos.data.repository.OrderRepository
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.util.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as MiniPosApp).database
    private val orderRepo = OrderRepository(db.orderDao())
    private val json = Json { ignoreUnknownKeys = true }

    // Today stats
    private val todayRange: Pair<Long, Long> get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private val weekRange: Pair<Long, Long> get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private val monthRange: Pair<Long, Long> get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }

    val todaySales: StateFlow<Double> = orderRepo.getTotalAmountBetween(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayOrders: StateFlow<Int> = orderRepo.getOrderCountBetween(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekSales: StateFlow<Double> = orderRepo.getTotalAmountBetween(weekRange.first, weekRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthSales: StateFlow<Double> = orderRepo.getTotalAmountBetween(monthRange.first, monthRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val recentOrders: StateFlow<List<Order>> = orderRepo.allOrders
        .map { it.take(20) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Top selling products this month
    val topProducts: StateFlow<List<Triple<String, String, Int>>> = orderRepo.getOrdersBetween(monthRange.first, monthRange.second)
        .map { orders ->
            val productSales = mutableMapOf<String, Pair<String, Int>>()
            orders.forEach { order ->
                try {
                    val items = json.decodeFromString<List<OrderItem>>(order.itemsJson)
                    items.forEach { item ->
                        val existing = productSales[item.barcode]
                        productSales[item.barcode] = (existing?.first ?: item.name) to
                                ((existing?.second ?: 0) + item.quantity)
                    }
                } catch (_: Exception) { }
            }
            productSales.entries
                .sortedByDescending { it.value.second }
                .take(10)
                .map { Triple(it.key, it.value.first, it.value.second) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
