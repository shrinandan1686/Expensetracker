package com.trackit.expense.presentation.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.presentation.theme.*

/**
 * Premium Expense Detail Screen.
 * Shows transaction metadata, location, and raw SMS data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExpenseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewModel.deleteExpense(onNavigateBack)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                ExpenseDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = TrackItPrimary
                    )
                }
                is ExpenseDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = ExpenseRed, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(state.message, color = Color.White)
                    }
                }
                is ExpenseDetailUiState.Success -> {
                    DetailContent(state.expense)
                }
            }
        }
    }
}

@Composable
private fun DetailContent(expense: Expense) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Large Amount Header ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Spent",
                    style = MaterialTheme.typography.labelLarge,
                    color = DarkOnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = expense.amountDisplay,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    ),
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = TrackItPrimary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = expense.category,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TrackItPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- 2. Info Grid ---
        InfoSection(
            title = "TRANSACTION INFO",
            items = listOf(
                InfoItem("Merchant", expense.merchant, Icons.Outlined.Store),
                InfoItem("Account", expense.account.ifBlank { "Unknown" }, Icons.Outlined.AccountBalance),
                InfoItem("Date", java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(expense.transactionAt)), Icons.Outlined.Event)
            )
        )

        // --- 3. Location Section ---
        if (expense.latitude != null && expense.longitude != null) {
            Spacer(Modifier.height(24.dp))
            LocationSection(
                address = expense.locationAddress ?: "Unknown Location",
                onOpenMap = {
                    val uri = "geo:${expense.latitude},${expense.longitude}?q=${expense.latitude},${expense.longitude}(Transaction)"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
            )
        }

        // --- 4. Raw SMS (Expandable) ---
        Spacer(Modifier.height(24.dp))
        var showRawSms by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showRawSms = !showRawSms },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Message, null, tint = DarkOnSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Original SMS", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (showRawSms) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint = DarkOnSurfaceVariant
                    )
                }
                
                AnimatedVisibility(visible = showRawSms) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = expense.rawSms,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, items: List<InfoItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.icon, null, tint = DarkOnSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.labelSmall, color = DarkOnSurfaceVariant)
                            Text(item.value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                    if (index < items.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = DarkSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSection(address: String, onOpenMap: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "LOCATION",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = DarkOnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenMap() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TrackItPrimary.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, null, tint = TrackItPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.OpenInNew, null, tint = DarkOnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class InfoItem(val label: String, val value: String, val icon: ImageVector)
