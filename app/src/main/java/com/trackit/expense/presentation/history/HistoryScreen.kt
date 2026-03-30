package com.trackit.expense.presentation.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * History screen — full expense log with filtering and search.
 *
 * Features:
 * - Searchable expense list
 * - Filter chips: All / This Week / This Month / Custom range
 * - Swipe-to-delete with undo snackbar
 * - Tap to view expense detail
 *
 * State is managed by [HistoryViewModel].
 *
 * TODO: Implement full UI with Material3 components.
 */
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onExpenseClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "History Screen — TODO: Implement UI")
            // TODO: Replace with full layout:
            //  - TopAppBar with back button and search field
            //  - FilterChipRow (All / Week / Month / Custom)
            //  - LazyColumn of ExpenseCard with SwipeToDismiss
            //  - EmptyState composable when filteredExpenses is empty
        }
    }
}
