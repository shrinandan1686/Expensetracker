package com.trackit.expense.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.TrackItPrimary

@Composable
fun AddSplitScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddSplitViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
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
            Text(
                text = "Add Expense",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            // Amount
            item {
                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChanged,
                    label = { Text("Total amount") },
                    prefix = { Text("₹ ", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            // Paid by
            item {
                Text("Paid by", color = TrackItPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                state.group?.members?.let { members ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            val selected = state.paidBy == member.userId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onPaidByChanged(member.userId) },
                                label = { Text(member.name.ifBlank { "Me" }, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TrackItPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Split type toggle
            item {
                Text("Split type", color = TrackItPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SplitType.EQUAL to "Equal", SplitType.CUSTOM to "Custom").forEach { (type, label) ->
                        val selected = state.splitType == type
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onSplitTypeChanged(type) },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TrackItPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Participants
            item {
                Text("Participants", color = TrackItPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            items(state.participants) { participant ->
                ParticipantRow(
                    participant = participant,
                    splitType = state.splitType,
                    equalShare = if (state.splitType == SplitType.EQUAL) {
                        val total = state.amountInput.toDoubleOrNull() ?: 0.0
                        val count = state.participants.count { it.included }
                        if (count > 0) total / count else 0.0
                    } else 0.0,
                    onToggle = { viewModel.onParticipantToggled(participant.userId) },
                    onCustomAmountChanged = { viewModel.onCustomAmountChanged(participant.userId, it) }
                )
            }

            // Submit button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary),
                    enabled = !state.isSaving && state.description.isNotBlank() && (state.amountInput.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Add Expense", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    state.error?.let { msg ->
        LaunchedEffect(msg) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantEntry,
    splitType: SplitType,
    equalShare: Double,
    onToggle: () -> Unit,
    onCustomAmountChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (participant.included) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (participant.included) TrackItPrimary else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = participant.name,
            color = if (participant.included) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp
        )
        if (participant.included) {
            when (splitType) {
                SplitType.EQUAL -> Text(
                    text = "₹ ${"%.2f".format(equalShare)}",
                    color = TrackItPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                SplitType.CUSTOM -> OutlinedTextField(
                    value = participant.customAmount,
                    onValueChange = onCustomAmountChanged,
                    prefix = { Text("₹ ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(100.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    colors = fieldColors()
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = TrackItPrimary,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = TrackItPrimary,
    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
)
