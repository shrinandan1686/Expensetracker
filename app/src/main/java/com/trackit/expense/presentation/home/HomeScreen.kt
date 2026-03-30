package com.trackit.expense.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Home screen — entry point of TrackIt.
 *
 * Displays:
 * - Monthly spend summary card
 * - Recent expense list
 * - FAB to add a new expense
 *
 * State is managed by [HomeViewModel] (Hilt-injected).
 * Navigation callbacks are passed in from [NavGraph].
 *
 * TODO: Implement full UI with Material3 components.
 */
@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onViewHistory: () -> Unit,
    onExpenseClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                else -> Text(text = "Home Screen — TODO: Implement UI")
                // TODO: Replace with full Compose layout:
                //  - TopAppBar with greeting + monthly total
                //  - LazyColumn of ExpenseCard composables
                //  - FloatingActionButton → onAddExpense()
            }
        }
    }
}
