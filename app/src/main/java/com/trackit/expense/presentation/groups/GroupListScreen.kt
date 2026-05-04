package com.trackit.expense.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackit.expense.presentation.theme.DarkBackground
import com.trackit.expense.presentation.theme.DarkSurface
import com.trackit.expense.presentation.theme.TrackItPrimary
import com.trackit.expense.presentation.theme.TrackItSecondary

@Composable
fun GroupListScreen(
    onGroupClick: (String) -> Unit,
    viewModel: GroupListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "Groups",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            if (state.isLoading && state.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackItPrimary)
                }
            } else if (state.groups.isEmpty()) {
                EmptyGroupsPlaceholder()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.groups, key = { it.group.id }) { item ->
                        GroupCard(item = item, onClick = { onGroupClick(item.group.id) })
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = viewModel::showCreateDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = TrackItPrimary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create group")
        }

    }

    // Create Group Dialog
    if (state.showCreateDialog) {
        CreateGroupDialog(
            name = state.newGroupName,
            emoji = state.newGroupEmoji,
            isCreating = state.isCreating,
            error = state.createError,
            onNameChange = viewModel::onGroupNameChanged,
            onEmojiChange = viewModel::onGroupEmojiChanged,
            onDismiss = viewModel::dismissCreateDialog,
            onCreate = viewModel::createGroup
        )
    }
}

@Composable
private fun GroupCard(item: GroupWithUserBalance, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji / icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TrackItPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.group.emoji.isNullOrEmpty()) {
                    Text(text = item.group.emoji, fontSize = 22.sp)
                } else {
                    Icon(Icons.Default.Group, contentDescription = null, tint = TrackItPrimary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.group.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = "${item.group.members.size} member${if (item.group.members.size != 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyGroupsPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Group, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No groups yet", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
        Text("Tap + to create one", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
    }
}

@Composable
private fun CreateGroupDialog(
    name: String,
    emoji: String,
    isCreating: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        containerColor = DarkSurface,
        title = { Text("New Group", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TrackItPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = TrackItPrimary,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 2) onEmojiChange(it) },
                    label = { Text("Emoji (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TrackItPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = TrackItPrimary,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank() && !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = TrackItPrimary)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}
