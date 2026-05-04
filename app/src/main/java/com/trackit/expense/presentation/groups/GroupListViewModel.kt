package com.trackit.expense.presentation.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.repository.GroupRepository
import com.trackit.expense.domain.usecase.CreateGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupWithUserBalance(
    val group: Group,
    val userBalance: Double
)

data class GroupListUiState(
    val groups: List<GroupWithUserBalance> = emptyList(),
    val isLoading: Boolean = false,
    val refreshError: String? = null,
    val showCreateDialog: Boolean = false,
    val newGroupName: String = "",
    val newGroupEmoji: String = "",
    val isCreating: Boolean = false,
    // Separate error for the create dialog — not auto-cleared, stays visible until user dismisses
    val createError: String? = null
)

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val createGroupUseCase: CreateGroupUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupListUiState())
    val uiState: StateFlow<GroupListUiState> = _uiState.asStateFlow()

    init {
        observeGroups()
        refresh()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            groupRepository.getGroups().collect { groups ->
                _uiState.update { state ->
                    state.copy(groups = groups.map { GroupWithUserBalance(group = it, userBalance = 0.0) })
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, refreshError = null) }
            groupRepository.refreshGroups()
                .onFailure { e ->
                    Log.w(TAG, "refreshGroups failed: ${e.message}", e)
                    _uiState.update { it.copy(refreshError = e.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true, createError = null) }

    fun dismissCreateDialog() = _uiState.update {
        it.copy(showCreateDialog = false, newGroupName = "", newGroupEmoji = "", createError = null, isCreating = false)
    }

    fun onGroupNameChanged(name: String) = _uiState.update { it.copy(newGroupName = name, createError = null) }

    fun onGroupEmojiChanged(emoji: String) = _uiState.update { it.copy(newGroupEmoji = emoji) }

    fun createGroup() {
        val state = _uiState.value
        if (state.newGroupName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, createError = null) }
            createGroupUseCase(
                name  = state.newGroupName.trim(),
                emoji = state.newGroupEmoji.trim().takeIf { it.isNotEmpty() }
            ).onSuccess {
                Log.d(TAG, "Group created: ${it.name}")
                _uiState.update { it.copy(showCreateDialog = false, newGroupName = "", newGroupEmoji = "", isCreating = false, createError = null) }
            }.onFailure { e ->
                Log.e(TAG, "createGroup failed: ${e.message}", e)
                _uiState.update { it.copy(isCreating = false, createError = e.message ?: "Failed to create group") }
            }
        }
    }

    companion object {
        private const val TAG = "GroupListVM"
    }
}
