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
import com.trackit.expense.presentation.detail.ExpenseDetailScreen

/**
 * Navigation route definitions for TrackIt.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object AddExpense : Screen("add_expense?amount={amount}&merchant={merchant}&account={account}&expenseId={expenseId}") {
        const val DEEP_LINK = "trackit://add_expense?amount={amount}&merchant={merchant}&account={account}&expenseId={expenseId}"
        fun createRoute(amount: String? = null, merchant: String? = null, account: String? = null, expenseId: String? = null): String {
            val queryParams = mutableListOf<String>()
            amount?.let { queryParams.add("amount=$it") }
            merchant?.let { queryParams.add("merchant=$it") }
            account?.let { queryParams.add("account=$it") }
            expenseId?.let { queryParams.add("expenseId=$it") }
            return if (queryParams.isEmpty()) "add_expense" else "add_expense?${queryParams.joinToString("&")}"
        }
    }
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

        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("amount") { type = NavType.StringType; nullable = true },
                navArgument("merchant") { type = NavType.StringType; nullable = true },
                navArgument("account") { type = NavType.StringType; nullable = true },
                navArgument("expenseId") { type = NavType.StringType; nullable = true }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = Screen.AddExpense.DEEP_LINK }
            )
        ) {
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
        ) {
            ExpenseDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
