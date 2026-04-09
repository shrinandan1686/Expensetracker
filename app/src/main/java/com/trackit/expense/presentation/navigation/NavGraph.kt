package com.trackit.expense.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.trackit.expense.presentation.add.AddExpenseScreen
import com.trackit.expense.presentation.history.HistoryScreen
import com.trackit.expense.presentation.home.HomeScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text

/**
 * Navigation route definitions for TrackIt.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object AddExpense : Screen("add_expense")
    data object History    : Screen("history")
    data object Analytics  : Screen("analytics")
    data object UnloggedExpenses : Screen("unlogged_expenses") {
        const val DEEP_LINK = "trackit://unlogged"
    }
    data object Settings   : Screen("settings")

    data object ExpenseDetail : Screen("expense_detail/{expenseId}") {
        fun createRoute(expenseId: String) = "expense_detail/$expenseId"
    }
}

/**
 * Root [NavHost] for TrackIt.
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            com.trackit.expense.presentation.onboarding.OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onAddExpense     = { navController.navigate(Screen.AddExpense.route) },
                onViewHistory    = { navController.navigate(Screen.History.route) },
                onViewUnreviewed = { navController.navigate(Screen.UnloggedExpenses.route) },
                onExpenseClick   = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) }
            )
        }

        composable(Screen.AddExpense.route) {
            AddExpenseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onExpenseClick = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) }
            )
        }

        composable(Screen.Analytics.route) {
            com.trackit.expense.presentation.analytics.CategoryBreakdownScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.UnloggedExpenses.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = Screen.UnloggedExpenses.DEEP_LINK }
            )
        ) {
            com.trackit.expense.presentation.history.UnloggedExpensesScreen(
                onNavigateBack = { navController.popBackStack() },
                onExpenseClick = { id -> navController.navigate(Screen.ExpenseDetail.createRoute(id)) }
            )
        }

        composable(Screen.Settings.route) {
            com.trackit.expense.presentation.settings.SettingsScreen()
        }

        composable(
            route     = Screen.ExpenseDetail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("expenseId") ?: return@composable
            // Placeholder for detail screen
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Expense Detail: $id", color = Color.White)
            }
        }
    }
}
