package com.trackit.expense.presentation.report

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.DarkSurfaceVariant
import com.trackit.expense.presentation.theme.TrackItPrimary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Share sheet launcher for CSV export
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.clearShareUri() }

    LaunchedEffect(uiState.shareUri) {
        uiState.shareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(intent, "Share expenses CSV"))
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onExport) {
                        Icon(Icons.Default.FileDownload, "Export CSV", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TrackItPrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Month Picker ──────────────────────────────────────────────────
            item {
                MonthPickerRow(
                    months = uiState.availableMonths,
                    selected = uiState.selectedMonth,
                    onSelect = viewModel::onMonthSelected
                )
            }

            // ── Summary Card ──────────────────────────────────────────────────
            item {
                SummaryCard(uiState)
            }

            // ── 6-Month Bar Chart ─────────────────────────────────────────────
            item {
                BarChartCard(
                    totals = uiState.monthlyTotals,
                    selectedMonth = uiState.selectedMonth
                )
            }

            // ── Daily Heatmap ─────────────────────────────────────────────────
            item {
                HeatmapCard(
                    dailyTotals = uiState.dailyTotals,
                    daysInMonth = uiState.daysInMonth,
                    firstDayOffset = uiState.firstDayOfWeek,
                    selectedDay = uiState.selectedDay,
                    onDayClick = viewModel::onDaySelected
                )
            }

            // ── Top Merchants ─────────────────────────────────────────────────
            if (uiState.topMerchants.isNotEmpty()) {
                item {
                    SectionCard(title = "Top Merchants") {
                        uiState.topMerchants.forEach { stat ->
                            TopMerchantRow(stat)
                        }
                    }
                }
            }
        }
    }

    // ── Day Detail Bottom Sheet ───────────────────────────────────────────────
    if (uiState.selectedDay != null) {
        DayDetailSheet(
            day = uiState.selectedDay!!,
            month = uiState.selectedMonth,
            expenses = uiState.selectedDayExpenses,
            onDismiss = viewModel::onDismissDaySheet
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Month Picker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthPickerRow(
    months: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(months) { month ->
            val isSelected = month == selected
            val label = monthLabel(month)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(month) },
                label = { Text(label, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TrackItPrimary,
                    selectedLabelColor = Color.White,
                    containerColor = DarkSurfaceVariant,
                    labelColor = Color.White.copy(alpha = 0.7f)
                )
            )
        }
    }
}

private fun monthLabel(month: String): String {
    // "2026-03" → "Mar '26"
    val parts = month.split("-")
    if (parts.size != 2) return month
    val year = parts[0].takeLast(2)
    val mon = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        .getOrElse(parts[1].toIntOrNull() ?: 0) { month }
    return "$mon '$year"
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: ReportUiState) {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    SectionCard(title = "Summary") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow("Total Spent", "₹${fmt.format(state.monthlyTotal)}")
            if (state.monthlyBudget > 0) {
                SummaryRow("Budget", "₹${fmt.format(state.monthlyBudget)}")
                SummaryRow(
                    "Remaining",
                    "₹${fmt.format(state.monthlyBudget - state.monthlyTotal)}",
                    valueColor = if (state.monthlyTotal > state.monthlyBudget)
                        MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                )
            }
            SummaryRow("Avg Daily Spend", "₹${fmt.format(state.avgDailySpend)}")
            state.highestExpense?.let {
                SummaryRow("Highest Expense", "₹${fmt.format(it.amount)} · ${it.merchant}")
            }
            if (state.topCategory.isNotBlank()) {
                SummaryRow("Top Category", state.topCategory)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-Month Bar Chart (Canvas)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BarChartCard(totals: List<MonthTotal>, selectedMonth: String) {
    if (totals.isEmpty()) return
    val maxVal = totals.maxOf { it.total }.let { if (it == 0.0) 1.0 else it }

    SectionCard(title = "Last 6 Months") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            totals.forEach { mt ->
                val isCurrent = mt.month == selectedMonth
                val fraction = (mt.total / maxVal).toFloat().coerceIn(0.02f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (isCurrent) TrackItPrimary
                                else TrackItPrimary.copy(alpha = 0.35f)
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        mt.label,
                        fontSize = 10.sp,
                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily Heatmap
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeatmapCard(
    dailyTotals: Map<Int, Double>,
    daysInMonth: Int,
    firstDayOffset: Int,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit
) {
    val maxDay = dailyTotals.values.maxOrNull() ?: 1.0
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    SectionCard(title = "Daily Spending") {
        // Day-of-week headers
        Row(Modifier.fillMaxWidth()) {
            dayLabels.forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Grid cells: offset blank cells + day cells
        val cells = mutableListOf<Int?>()
        repeat(firstDayOffset) { cells.add(null) }
        (1..daysInMonth).forEach { cells.add(it) }
        // Pad to complete last row
        while (cells.size % 7 != 0) cells.add(null)

        val rows = cells.chunked(7)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { day ->
                    val amount = day?.let { dailyTotals[it] } ?: 0.0
                    val alpha = if (day != null && maxDay > 0)
                        (amount / maxDay).toFloat().coerceIn(0.08f, 1f)
                    else 0f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    day == null -> Color.Transparent
                                    day == selectedDay -> TrackItPrimary
                                    amount > 0 -> TrackItPrimary.copy(alpha = alpha)
                                    else -> DarkSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                            .then(
                                if (day != null) Modifier.clickable { onDayClick(day) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Text(
                                "$day",
                                fontSize = 10.sp,
                                color = if (day == selectedDay || alpha > 0.5f) Color.White
                                        else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Merchants
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopMerchantRow(stat: MerchantStat) {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.merchant,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${stat.count} transaction${if (stat.count > 1) "s" else ""}",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            "₹${fmt.format(stat.total)}",
            color = TrackItPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day Detail Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: Int,
    month: String,
    expenses: List<Expense>,
    onDismiss: () -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN"))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Day $day — ${monthLabel(month)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Total: ₹${fmt.format(expenses.sumOf { it.amount })}",
                style = MaterialTheme.typography.bodySmall,
                color = TrackItPrimary
            )
            Spacer(Modifier.height(16.dp))

            if (expenses.isEmpty()) {
                Text("No expenses recorded", color = Color.White.copy(alpha = 0.5f))
            } else {
                expenses.forEach { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.merchant, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(expense.category, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("₹${fmt.format(expense.amount)}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared layout helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
