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
import com.trackit.expense.presentation.detail.ExpenseDetailScreen
import com.trackit.expense.presentation.groups.AddSplitScreen
import com.trackit.expense.presentation.groups.GroupDetailScreen
import com.trackit.expense.presentation.groups.GroupListScreen
import com.trackit.expense.presentation.history.HistoryScreen
import com.trackit.expense.presentation.home.HomeScreen
import com.trackit.expense.presentation.login.LoginScreen
import com.trackit.expense.presentation.profile.ProfileScreen
import com.trackit.expense.presentation.report.ReportScreen

/**
 * Navigation route definitions for TrackIt.
 */
sealed class Screen(val route: String) {
    data object Login      : Screen("login")
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object AddExpense : Screen("add_expense?amount={amount}&merchant={merchant}&account={account}&expenseId={expenseId}&duplicateId={duplicateId}&isDuplicate={isDuplicate}") {
        const val DEEP_LINK = "trackit://add_expense?amount={amount}&merchant={merchant}&account={account}&expenseId={expenseId}&duplicateId={duplicateId}&isDuplicate={isDuplicate}"
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
        fun deepLink(expenseId: String) = "trackit://expense_detail/$expenseId"
        const val DEEP_LINK_PATTERN = "trackit://expense_detail/{expenseId}"
    }

    data object Reports : Screen("reports") {
        const val DEEP_LINK = "trackit://reports"
    }

    data object Profile : Screen("profile")

    data object GroupList : Screen("groups")

    data object GroupDetail : Screen("groups/{groupId}") {
        fun createRoute(groupId: String) = "groups/$groupId"
    }

    data object AddSplit : Screen("groups/{groupId}/add_split") {
        fun createRoute(groupId: String) = "groups/$groupId/add_split"
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
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

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
                navArgument("amount")      { type = NavType.StringType; nullable = true },
                navArgument("merchant")    { type = NavType.StringType; nullable = true },
                navArgument("account")     { type = NavType.StringType; nullable = true },
                navArgument("expenseId")   { type = NavType.StringType; nullable = true },
                navArgument("duplicateId") { type = NavType.StringType; nullable = true },
                navArgument("isDuplicate") { type = NavType.StringType; nullable = true }
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
            com.trackit.expense.presentation.settings.SettingsScreen(
                onNavigateToReview  = { navController.navigate(Screen.UnloggedExpenses.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.GroupList.route) {
            GroupListScreen(
                onGroupClick = { groupId -> navController.navigate(Screen.GroupDetail.createRoute(groupId)) }
            )
        }

        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            GroupDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddSplit = { groupId -> navController.navigate(Screen.AddSplit.createRoute(groupId)) }
            )
        }

        composable(
            route = Screen.AddSplit.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            AddSplitScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Reports.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = Screen.Reports.DEEP_LINK }
            )
        ) {
            ReportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route      = Screen.ExpenseDetail.route,
            arguments  = listOf(navArgument("expenseId") { type = NavType.StringType }),
            deepLinks  = listOf(navDeepLink { uriPattern = Screen.ExpenseDetail.DEEP_LINK_PATTERN })
        ) {
            ExpenseDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
