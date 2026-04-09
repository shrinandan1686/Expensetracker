package com.trackit.expense.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.DarkSurfaceVariant
import com.trackit.expense.presentation.theme.ExpenseRed
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onViewHistory: () -> Unit,
    onViewUnreviewed: () -> Unit,
    onExpenseClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "TrackIt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                },
                actions = {
                    // Unreviewed badge
                    if (uiState.unloggedCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text("${uiState.unloggedCount}") }
                            }
                        ) {
                            IconButton(onClick = onViewHistory) {
                                Icon(Icons.Default.History, "Unreviewed", tint = Color.White)
                            }
                        }
                    } else {
                        IconButton(onClick = onViewHistory) {
                            Icon(Icons.Default.History, "History", tint = Color.White)
                        }
                    }
                    IconButton(onClick = viewModel::onSyncNow) {
                        Icon(Icons.Default.Sync, "Sync", tint = TrackItSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpense,
                containerColor = TrackItPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, "Add Expense")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TrackItPrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── Monthly summary card ──────────────────────────────────────────
            item {
                MonthlySummaryCard(
                    month = uiState.currentMonth,
                    total = uiState.monthlyTotal,
                    budget = uiState.monthlyBudget,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ── Unreviewed Warning ────────────────────────────────────────────
            if (uiState.unloggedCount > 0) {
                item {
                    UnreviewedWarningCard(
                        count = uiState.unloggedCount,
                        onClick = onViewUnreviewed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Category Chips ────────────────────────────────────────────────
            item {
                CategoryChipsSection(
                    totals = uiState.categoryTotals,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // ── Section header ────────────────────────────────────────────────
            item {
                Text(
                    "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (uiState.recentExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No expenses yet.\nTap + to add your first one!",
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // ── Expense list ──────────────────────────────────────────────────
            items(uiState.recentExpenses, key = { it.id }) { expense ->
                com.trackit.expense.presentation.components.ExpenseListItem(
                    expense = expense,
                    onClick = { onExpenseClick(expense.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    month: String,
    total: Double,
    budget: Double,
    modifier: Modifier = Modifier
) {
    val displayMonth = runCatching {
        val src = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(month)
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(src ?: Date())
    }.getOrElse { month }

    val progress = if (budget > 0) (total / budget).toFloat().coerceIn(0f, 1.1f) else 0f
    val progressColor = when {
        progress < 0.5f -> com.trackit.expense.presentation.theme.BudgetSuccess
        progress < 0.8f -> com.trackit.expense.presentation.theme.BudgetWarning
        else            -> com.trackit.expense.presentation.theme.BudgetError
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(TrackItPrimary, Color(0xFF625B71))),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            displayMonth,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        AnimatedContent(
                            targetState = total,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                            label = "monthly_total"
                        ) { t ->
                            Text(
                                "₹${"%,.0f".format(t)}",
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (budget > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Budget",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "₹${"%,.0f".format(budget)}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                if (budget > 0) {
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.coerceAtMost(1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (progress > 1f) "Over budget by ₹${"%,.0f".format(total - budget)}" 
                               else "${(progress * 100).toInt()}% of monthly budget used",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Total spent this month",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun UnreviewedWarningCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.trackit.expense.presentation.theme.BudgetError.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(com.trackit.expense.presentation.theme.BudgetError.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.History, null, tint = com.trackit.expense.presentation.theme.BudgetError)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Unreviewed Expenses",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "You have $count transactions to confirm",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            Text(
                "Review",
                color = com.trackit.expense.presentation.theme.BudgetError,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CategoryChipsSection(
    totals: List<com.trackit.expense.data.local.dao.CategoryTotal>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        items(totals) { item ->
            val category = remember(item.category) {
                com.trackit.expense.overlay.ExpenseCategory.entries.find { it.displayName == item.category } ?: com.trackit.expense.overlay.ExpenseCategory.OTHERS
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category.emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            item.category,
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "₹${"%,.0f".format(item.total)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expense List Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseListItem(
    expense: Expense,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(expense.transactionAt) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            .format(Date(expense.transactionAt))
    }
    val categoryEmoji = categoryEmoji(expense.category)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Category icon bubble
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryEmoji, fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            // Merchant + date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.merchant.ifBlank { "Unknown" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    expense.category,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    dateStr,
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.width(8.dp))

            // Amount + logged indicator
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    expense.amountDisplay,
                    color = ExpenseRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (!expense.isLogged) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "•  Unreviewed",
                        color = Color(0xFFFFAB40),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun categoryEmoji(category: String): String = when {
    category.contains("Food",        ignoreCase = true) -> "🍔"
    category.contains("Transport",   ignoreCase = true) -> "🚗"
    category.contains("Shopping",    ignoreCase = true) -> "🛍️"
    category.contains("Groceries",   ignoreCase = true) -> "🛒"
    category.contains("Bill",        ignoreCase = true) -> "💡"
    category.contains("Health",      ignoreCase = true) -> "💊"
    category.contains("Entertain",   ignoreCase = true) -> "🎬"
    category.contains("Education",   ignoreCase = true) -> "📚"
    category.contains("Travel",      ignoreCase = true) -> "✈️"
    category.contains("Investment",  ignoreCase = true) -> "📈"
    else                                                -> "💸"
}
