package com.trackit.expense.presentation.groups

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.expense.domain.model.Balance
import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.model.GroupMember
import com.trackit.expense.domain.model.Split
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItSecondary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GroupDetailScreen(
    onNavigateBack: () -> Unit,
    onAddSplit: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Launch UPI intent when settlement is recorded
    LaunchedEffect(state.settlementLaunched) {
        if (state.settlementLaunched) {
            viewModel.onSettlementLaunchHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                state.group?.let { group ->
                    Text(
                        text = "${group.emoji ?: ""} ${group.name}".trim(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackItPrimary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Members section
                    state.group?.let { group ->
                        item {
                            MembersRow(members = group.members)
                        }
                    }

                    // Balances section
                    if (state.balances.isNotEmpty()) {
                        item {
                            Text(
                                text = "Balances",
                                color = TrackItPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.balances, key = { "${it.fromUserId}-${it.toUserId}" }) { balance ->
                            BalanceRow(
                                balance = balance,
                                group = state.group,
                                currentUserId = state.currentUserId,
                                onSettleUp = { toUserId, amount, toMember ->
                                    viewModel.settleUp(toUserId, amount)
                                    // Launch UPI deep link immediately
                                    toMember?.upiId?.let { upiId ->
                                        val upiUri = "upi://pay?pa=$upiId&pn=${Uri.encode(toMember.name)}&am=$amount&cu=INR&tn=${Uri.encode("TrackIt settlement")}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                        context.startActivity(Intent.createChooser(intent, "Pay via"))
                                    }
                                }
                            )
                        }
                    }

                    // Splits section
                    if (state.splits.isNotEmpty()) {
                        item {
                            Text(
                                text = "Expenses",
                                color = TrackItPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.splits, key = { it.id }) { split ->
                            SplitRow(split = split, group = state.group)
                        }
                    } else if (!state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No expenses yet. Tap + to add one.", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FAB
        state.group?.let { group ->
            FloatingActionButton(
                onClick = { onAddSplit(group.id) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = TrackItPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        }
    }
}

@Composable
private fun MembersRow(members: List<GroupMember>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Members", color = TrackItPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
            members.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TrackItPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TrackItPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = member.name.ifBlank { member.userId },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    if (!member.upiId.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "• ${member.upiId}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(
    balance: Balance,
    group: Group?,
    currentUserId: String,
    onSettleUp: (toUserId: String, amount: Double, toMember: GroupMember?) -> Unit
) {
    val fromName = group?.members?.find { it.userId == balance.fromUserId }?.name?.ifBlank { balance.fromUserId } ?: balance.fromUserId
    val toMember = group?.members?.find { it.userId == balance.toUserId }
    val toName   = toMember?.name?.ifBlank { balance.toUserId } ?: balance.toUserId
    val amountStr = formatCurrency(balance.amount)

    val isCurrentUserDebtor = balance.fromUserId == currentUserId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUserDebtor) TrackItSecondary.copy(alpha = 0.08f) else DarkSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isCurrentUserDebtor) {
                    Text(text = "You owe $toName", color = TrackItSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                } else if (balance.toUserId == currentUserId) {
                    Text(text = "$fromName owes you", color = TrackItPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                } else {
                    Text(text = "$fromName owes $toName", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                Text(text = amountStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (isCurrentUserDebtor) {
                Button(
                    onClick = { onSettleUp(balance.toUserId, balance.amount, toMember) },
                    colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Settle Up", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SplitRow(split: Split, group: Group?) {
    val paidByName = group?.members?.find { it.userId == split.paidBy }?.name?.ifBlank { split.paidBy } ?: split.paidBy
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = split.description, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(text = "Paid by $paidByName • ${split.participants.size} people", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text(text = formatCurrency(split.totalAmount), color = TrackItPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return fmt.format(amount)
}
