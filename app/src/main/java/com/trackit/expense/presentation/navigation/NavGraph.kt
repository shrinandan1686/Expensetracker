package com.trackit.expense.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trackit.expense.presentation.add.AddExpenseScreen
import com.trackit.expense.presentation.history.HistoryScreen
import com.trackit.expense.presentation.home.HomeScreen

/**
 * Navigation route definitions for TrackIt.
 *
 * Sealed class pattern keeps all routes in one place and prevents typos.
 * Arguments are appended as path segments (e.g. "expense_detail/{expenseId}").
 */
sealed class Screen(val route: String) {
    data object Home          : Screen("home")
    data object AddExpense    : Screen("add_expense")
    data object History       : Screen("history")
    data object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun createRoute(expenseId: Long) = "expense_detail/$expenseId"
    }
}

/**
 * Root [NavHost] for TrackIt.
 *
 * Hosted by [MainActivity]. The [NavHostController] is created here and passed
 * down to screens that need to trigger navigation.
 *
 * StartDestination: [Screen.Home]
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                onAddExpense     = { navController.navigate(Screen.AddExpense.route) },
                onViewHistory    = { navController.navigate(Screen.History.route) },
                onExpenseClick   = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) }
            )
        }

        composable(route = Screen.AddExpense.route) {
            AddExpenseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onExpenseClick = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) }
            )
        }

        // TODO: Add ExpenseDetailScreen composable when implemented
    }
}
