package com.trackit.expense.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Items for the bottom navigation bar.
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home      : BottomNavItem(Screen.Home.route,      "Home",      Icons.Default.Home)
    data object Expenses  : BottomNavItem(Screen.History.route,   "Expenses",  Icons.Default.History)
    data object Analytics : BottomNavItem(Screen.Analytics.route, "Analytics", Icons.Default.Analytics)
    data object Settings  : BottomNavItem(Screen.Settings.route,  "Settings",  Icons.Default.Settings)

    companion object {
        val items = listOf(Home, Expenses, Analytics, Settings)
    }
}
