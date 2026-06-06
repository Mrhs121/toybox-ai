package com.toybox.minipos.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toybox.minipos.scanner.ContinuousScannerScreen
import com.toybox.minipos.ui.checkout.CheckoutScreen
import com.toybox.minipos.ui.checkout.CheckoutViewModel
import com.toybox.minipos.ui.products.ProductListScreen
import com.toybox.minipos.ui.products.ProductViewModel
import com.toybox.minipos.ui.stats.StatsScreen
import com.toybox.minipos.ui.stats.StatsViewModel

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    CHECKOUT("checkout", "收银", Icons.Default.PointOfSale),
    STATS("stats", "统计", Icons.Default.Analytics),
    PRODUCTS("products", "商品", Icons.Default.Inventory2)
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Shared ViewModels
    val checkoutViewModel: CheckoutViewModel = viewModel()
    val productViewModel: ProductViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()

    // Scanner state hoisted to NavGraph so it renders outside Scaffold
    var showScanner by remember { mutableStateOf(false) }
    val cartItems by checkoutViewModel.cartItems.collectAsState()
    val totalItems by checkoutViewModel.totalItems.collectAsState()
    val totalAmount by checkoutViewModel.totalAmount.collectAsState()
    val lastScanResult by checkoutViewModel.lastScanResult.collectAsState()
    val scanEventId by checkoutViewModel.scanEventId.collectAsState()

    // Only show bottom bar on main screens
    val showBottomBar = currentRoute in Screen.entries.map { it.route }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        Screen.entries.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.CHECKOUT.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) }
            ) {
                composable(Screen.CHECKOUT.route) {
                    CheckoutScreen(
                        viewModel = checkoutViewModel,
                        onNavigateToProducts = {
                            navController.navigate(Screen.PRODUCTS.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenScanner = { showScanner = true }
                    )
                }

                composable(Screen.STATS.route) {
                    StatsScreen(viewModel = statsViewModel)
                }

                composable(Screen.PRODUCTS.route) {
                    ProductListScreen(
                        viewModel = productViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Scanner as full-screen overlay — same Window as Activity, so edge-to-edge works
        if (showScanner) {
            BackHandler { showScanner = false }
            ContinuousScannerScreen(
                onBarcodeScanned = { barcode ->
                    checkoutViewModel.addProductByBarcode(barcode)
                },
                cartItems = cartItems,
                cartItemCount = totalItems,
                cartTotal = totalAmount,
                lastScannedName = lastScanResult,
                scanEventId = scanEventId,
                onClose = { showScanner = false },
                onManualInput = null
            )
        }
    }
}
