package com.trackit.expense.presentation.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.trackit.expense.domain.model.Balance
import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.model.Split
import com.trackit.expense.domain.repository.GroupRepository
import com.trackit.expense.domain.repository.SplitRepository
import com.trackit.expense.domain.usecase.GetGroupBalancesUseCase
import com.trackit.expense.domain.usecase.SettleDebtUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailUiState(
    val group: Group? = null,
    val splits: List<Split> = emptyList(),
    val balances: List<Balance> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val settlementLaunched: Boolean = false
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val splitRepository: SplitRepository,
    private val getGroupBalancesUseCase: GetGroupBalancesUseCase,
    private val settleDebtUseCase: SettleDebtUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(GroupDetailUiState(currentUserId = firebaseAuth.currentUser?.uid ?: ""))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        observeSplits()
        load()
    }

    private fun observeSplits() {
        viewModelScope.launch {
            splitRepository.getSplits(groupId).collect { splits ->
                _uiState.update { it.copy(splits = splits) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            groupRepository.getGroupById(groupId)
                .onSuccess { group -> _uiState.update { it.copy(group = group) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }

            splitRepository.refreshSplits(groupId)

            getGroupBalancesUseCase(groupId)
                .onSuccess { balances -> _uiState.update { it.copy(balances = balances) } }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun settleUp(toUserId: String, amount: Double) {
        viewModelScope.launch {
            settleDebtUseCase(groupId, toUserId, amount)
                .onSuccess { _uiState.update { it.copy(settlementLaunched = true) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onSettlementLaunchHandled() = _uiState.update { it.copy(settlementLaunched = false) }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
