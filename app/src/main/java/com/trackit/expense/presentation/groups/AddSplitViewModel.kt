package com.trackit.expense.presentation.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.model.SplitParticipant
import com.trackit.expense.domain.repository.GroupRepository
import com.trackit.expense.domain.usecase.AddSplitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplitType { EQUAL, CUSTOM }

data class ParticipantEntry(val userId: String, val name: String, val included: Boolean, val customAmount: String)

data class AddSplitUiState(
    val group: Group? = null,
    val description: String = "",
    val amountInput: String = "",
    val paidBy: String = "",
    val splitType: SplitType = SplitType.EQUAL,
    val participants: List<ParticipantEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddSplitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val addSplitUseCase: AddSplitUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(AddSplitUiState())
    val uiState: StateFlow<AddSplitUiState> = _uiState.asStateFlow()

    init {
        loadGroup()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            groupRepository.getGroupById(groupId)
                .onSuccess { group ->
                    val currentUserId = firebaseAuth.currentUser?.uid ?: ""
                    _uiState.update { state ->
                        state.copy(
                            group = group,
                            paidBy = currentUserId,
                            participants = group.members.map { m ->
                                ParticipantEntry(userId = m.userId, name = m.name.ifBlank { m.userId }, included = true, customAmount = "")
                            },
                            isLoading = false
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    fun onDescriptionChanged(v: String) = _uiState.update { it.copy(description = v) }
    fun onAmountChanged(v: String) = _uiState.update { it.copy(amountInput = v.filter { c -> c.isDigit() || c == '.' }) }
    fun onPaidByChanged(userId: String) = _uiState.update { it.copy(paidBy = userId) }
    fun onSplitTypeChanged(type: SplitType) = _uiState.update { it.copy(splitType = type) }

    fun onParticipantToggled(userId: String) = _uiState.update { state ->
        state.copy(participants = state.participants.map { p ->
            if (p.userId == userId) p.copy(included = !p.included) else p
        })
    }

    fun onCustomAmountChanged(userId: String, amount: String) = _uiState.update { state ->
        state.copy(participants = state.participants.map { p ->
            if (p.userId == userId) p.copy(customAmount = amount.filter { c -> c.isDigit() || c == '.' }) else p
        })
    }

    fun submit() {
        val state = _uiState.value
        val total = state.amountInput.toDoubleOrNull() ?: return
        if (state.description.isBlank() || total <= 0) return

        val included = state.participants.filter { it.included }
        if (included.isEmpty()) return

        val participants = when (state.splitType) {
            SplitType.EQUAL -> {
                val share = Math.round(total / included.size * 100.0) / 100.0
                included.map { SplitParticipant(userId = it.userId, amount = share, isPaid = false) }
            }
            SplitType.CUSTOM -> {
                included.map { SplitParticipant(userId = it.userId, amount = it.customAmount.toDoubleOrNull() ?: 0.0, isPaid = false) }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            addSplitUseCase(
                groupId = groupId,
                description = state.description.trim(),
                totalAmount = total,
                paidBy = state.paidBy,
                participants = participants
            ).onSuccess {
                _uiState.update { it.copy(isSaved = true, isSaving = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
