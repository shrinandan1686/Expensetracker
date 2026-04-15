package com.trackit.expense.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackit.expense.BuildConfig
import com.trackit.expense.data.local.entity.AccountEntity
import com.trackit.expense.data.local.entity.AccountType
import com.trackit.expense.data.local.entity.CategoryEntity
import com.trackit.expense.domain.repository.ThemeMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToReview: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val shareUri      by viewModel.shareUri.collectAsState()
    val importState   by viewModel.importState.collectAsState()
    val context       = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddAccountSheet  by remember { mutableStateOf(false) }
    var showAddCategorySheet by remember { mutableStateOf(false) }
    var showImportDialog     by remember { mutableStateOf(false) }

    // Runtime READ_SMS permission
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    // Share sheet for CSV export
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.clearShareUri() }

    LaunchedEffect(shareUri) {
        shareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(intent, "Share expenses CSV"))
        }
    }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    // Show error snackbar if import fails
    LaunchedEffect(importState) {
        if (importState is ImportState.Error) {
            snackbarHostState.showSnackbar((importState as ImportState.Error).message)
            viewModel.clearImportState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── ACCOUNTS SECTION ─────────────────────────────────────────────
            item {
                SettingsSection(
                    title = "ACCOUNTS",
                    action = {
                        TextButton(onClick = { showAddAccountSheet = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                ) {
                    if (uiState.accounts.isEmpty()) {
                        EmptyState("No accounts linked")
                    } else {
                        uiState.accounts.forEach { account ->
                            AccountItem(
                                account = account,
                                onDelete = { viewModel.deleteAccount(account) }
                            )
                        }
                    }
                }
            }

            // ── CATEGORIES SECTION ───────────────────────────────────────────
            item {
                SettingsSection(
                    title = "CATEGORIES",
                    action = {
                        TextButton(onClick = { showAddCategorySheet = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Custom")
                        }
                    }
                ) {
                    uiState.categories.forEach { category ->
                        CategoryItem(
                            category = category,
                            onToggle = { viewModel.toggleCategory(category) }
                        )
                    }
                }
            }

            // ── NOTIFICATIONS SECTION ────────────────────────────────────────
            item {
                SettingsSection(title = "NOTIFICATIONS") {
                    SettingsSwitchItem(
                        title = "Budget Alerts",
                        subtitle = "Notify when limit is near",
                        icon = Icons.Default.NotificationsActive,
                        checked = uiState.budgetAlertsEnabled,
                        onCheckedChange = viewModel::setBudgetAlertsEnabled
                    )
                    SettingsSwitchItem(
                        title = "Weekly Summary",
                        subtitle = "Every Sunday at 6 PM",
                        icon = Icons.Default.Summarize,
                        checked = uiState.weeklySummaryEnabled,
                        onCheckedChange = viewModel::setWeeklySummaryEnabled
                    )
                    SettingsSwitchItem(
                        title = "Unlogged Reminders",
                        subtitle = "Remind to review new SMS",
                        icon = Icons.Default.History,
                        checked = uiState.unloggedRemindersEnabled,
                        onCheckedChange = viewModel::setUnloggedRemindersEnabled
                    )
                    
                    if (uiState.unloggedRemindersEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Reminder Interval",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        SegmentedControl(
                            options = listOf("2h", "4h", "8h"),
                            selectedIndex = when (uiState.reminderIntervalHours) {
                                2 -> 0
                                4 -> 1
                                8 -> 2
                                else -> 1
                            },
                            onOptionSelected = {
                                val hours = when (it) {
                                    0 -> 2
                                    1 -> 4
                                    2 -> 8
                                    else -> 4
                                }
                                viewModel.setReminderInterval(hours)
                            }
                        )
                    }
                }
            }

            // ── APPEARANCE SECTION ───────────────────────────────────────────
            item {
                SettingsSection(title = "APPEARANCE") {
                    Text(
                        "Theme Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = uiState.themeMode.ordinal,
                        onOptionSelected = { viewModel.setThemeMode(ThemeMode.values()[it]) }
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    SettingsSwitchItem(
                        title = "Dynamic Color",
                        subtitle = "Material You theming",
                        icon = Icons.Default.Palette,
                        checked = uiState.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled
                    )
                }
            }

            // ── SMS IMPORT SECTION ───────────────────────────────────────────
            item {
                SettingsSection(title = "DATA IMPORT") {
                    when (val state = importState) {
                        is ImportState.Loading -> {
                            Column(Modifier.padding(16.dp)) {
                                val pct = if (state.total > 0)
                                    state.read.toFloat() / state.total else 0f
                                Text(
                                    "Reading messages… ${state.read} / ${state.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is ImportState.Done -> {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "✓ Import complete — ${state.imported} new expense${if (state.imported != 1) "s" else ""} added (${state.skipped} skipped)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.imported > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.clearImportState()
                                            onNavigateToReview()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.RateReview, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Review & Categorise Now")
                                    }
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.clearImportState() }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                        else -> {
                            if (hasSmsPermission) {
                                SettingsClickItem(
                                    title    = "Import from SMS",
                                    subtitle = "Scan past bank messages and add missing expenses",
                                    icon     = Icons.Default.Sms,
                                    onClick  = { showImportDialog = true }
                                )
                            } else {
                                SettingsClickItem(
                                    title    = "Grant SMS Permission",
                                    subtitle = "Required to read bank messages",
                                    icon     = Icons.Default.Sms,
                                    onClick  = {
                                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── ABOUT SECTION ───────────────────────────────────────────────
            item {
                SettingsSection(title = "ABOUT") {
                    SettingsClickItem(
                        title = "Export Data",
                        subtitle = "Download all records as CSV",
                        icon = Icons.Default.Download,
                        onClick = viewModel::exportData
                    )
                    SettingsClickItem(
                        title = "App Version",
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        icon = Icons.Default.Info,
                        onClick = {}
                    )
                    SettingsClickItem(
                        title = "Privacy Policy",
                        subtitle = "Read how we handle your data",
                        icon = Icons.Default.Policy,
                        onClick = { /* Open WebView */ }
                    )
                }
            }
        }
    }

    // ── BOTTOM SHEETS ────────────────────────────────────────────────────────
    if (showAddAccountSheet) {
        AccountEntrySheet(
            onDismiss = { showAddAccountSheet = false },
            onSave = { name, last4, type, color ->
                viewModel.addAccount(name, last4, type, color)
                showAddAccountSheet = false
            }
        )
    }

    if (showAddCategorySheet) {
        CategoryEntrySheet(
            onDismiss = { showAddCategorySheet = false },
            onSave = { name, emoji ->
                viewModel.addCategory(name, emoji)
                showAddCategorySheet = false
            }
        )
    }

    if (showImportDialog) {
        ImportPeriodDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { fromMillis ->
                showImportDialog = false
                viewModel.startImport(fromMillis)
            }
        )
    }
}

@Composable
fun ImportPeriodDialog(
    onDismiss: () -> Unit,
    onConfirm: (fromMillis: Long) -> Unit
) {
    val periods = listOf(
        "Last 1 month"  to 1,
        "Last 3 months" to 3,
        "Last 6 months" to 6,
        "Last 1 year"   to 12
    )
    var selectedIndex by remember { mutableStateOf(1) } // default: 3 months

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Import from SMS", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose how far back to scan your bank messages. Only new expenses not already in the app will be added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                periods.forEachIndexed { index, (label, _) ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick  = { selectedIndex = index },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val months = periods[selectedIndex].second
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -months) }
                    onConfirm(cal.timeInMillis)
                }
            ) {
                Text("Start Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
            action?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountItem(
    account: AccountEntity,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = Color.Red.copy(alpha = 0.8f)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, null, tint = Color.White)
            }
        }
    ) {
        ListItem(
            headlineContent = { Text(account.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("${account.type} • •••• ${account.last4}") },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(account.colorHex))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (account.type) {
                            AccountType.CREDIT -> Icons.Default.CreditCard
                            AccountType.DEBIT -> Icons.Default.AccountBalance
                            AccountType.UPI -> Icons.Default.QrCode
                            AccountType.WALLET -> Icons.Default.Wallet
                        },
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingContent = {
                if (account.isDefault) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun CategoryItem(
    category: CategoryEntity,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                category.name, 
                color = if (category.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            ) 
        },
        leadingContent = { 
            Text(
                category.emoji, 
                fontSize = 24.sp,
                modifier = Modifier.alpha(if (category.isEnabled) 1f else 0.4f)
            ) 
        },
        trailingContent = {
            Switch(
                checked = category.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.8f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.9f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun EmptyState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOptionSelected(index) }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEntrySheet(
    onDismiss: () -> Unit,
    onSave: (String, String, AccountType, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.DEBIT) }
    var colorHex by remember { mutableStateOf("#6200EE") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Add Account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name (e.g. HDFC Bank)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = last4,
                    onValueChange = { if (it.length <= 4) last4 = it },
                    label = { Text("Last 4 Digits") },
                    modifier = Modifier.weight(1f)
                )
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AccountType.values().forEach {
                            DropdownMenuItem(
                                text = { Text(it.name) },
                                onClick = { type = it; expanded = false }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onSave(name, last4, type, colorHex) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && last4.length == 4
            ) {
                Text("Link Account")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEntrySheet(
    onDismiss: () -> Unit,
    onSave: (String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📦") }
    
    val emojis = listOf(
        "🍕", "🍔", "☕", "🍺", "🚗", "🚌", "✈️", "🏠", "🛍️", "🎭",
        "🎬", "🎮", "💊", "💪", "📚", "🎓", "💼", "💻", "📱", "🎁",
        "🎉", "💡", "🛠️", "🩹", "🐶", "🐱", "🌱", "🔋", "⚡", "📦"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Custom Category", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(24.dp))
            Text("Pick an Emoji", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(emojis) { emoji ->
                    val isSelected = emoji == selectedEmoji
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { selectedEmoji = emoji }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onSave(name, selectedEmoji) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Create Category")
            }
        }
    }
}

private fun Modifier.alpha(alpha: Float): Modifier = this.then(Modifier.drawWithContent {
    drawContent()
    drawRect(Color.White.copy(alpha = 1f - alpha), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
})
