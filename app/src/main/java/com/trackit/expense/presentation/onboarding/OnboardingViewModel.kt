package com.trackit.expense.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.repository.PreferenceRepository
import com.trackit.expense.domain.repository.UserRepository
import com.trackit.expense.domain.usecase.SaveBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class OnboardingUiState(
    val budgetInput: String = "",
    val nameInput: String = "",
    val upiIdInput: String = "",
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val saveBudgetUseCase: SaveBudgetUseCase,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onBudgetInputChanged(input: String) {
        _uiState.update { it.copy(budgetInput = input.filter { c -> c.isDigit() }) }
    }

    fun onNameChanged(input: String) {
        _uiState.update { it.copy(nameInput = input) }
    }

    fun onUpiIdChanged(input: String) {
        _uiState.update { it.copy(upiIdInput = input) }
    }

    fun completeOnboarding() {
        val state = _uiState.value
        val amount = state.budgetInput.toDoubleOrNull() ?: 0.0
        val month = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            saveBudgetUseCase(month, amount).onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message, isSaving = false) }
                return@launch
            }

            // Best-effort: save profile to backend; don't block onboarding on failure
            if (state.nameInput.isNotBlank() || state.upiIdInput.isNotBlank()) {
                userRepository.updateProfile(
                    name  = state.nameInput.trim(),
                    upiId = state.upiIdInput.trim()
                )
            }

            preferenceRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isCompleted = true, isSaving = false) }
        }
    }
}
