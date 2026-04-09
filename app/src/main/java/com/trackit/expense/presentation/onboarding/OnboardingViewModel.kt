package com.trackit.expense.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.repository.PreferenceRepository
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
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
    private val saveBudgetUseCase: SaveBudgetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onBudgetInputChanged(input: String) {
        _uiState.update { it.copy(budgetInput = input.filter { char -> char.isDigit() }) }
    }

    fun completeOnboarding() {
        val amount = _uiState.value.budgetInput.toDoubleOrNull() ?: 0.0
        val month = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            // Save initial budget
            saveBudgetUseCase(month, amount)
                .onSuccess {
                    // Mark onboarding as completed
                    preferenceRepository.setOnboardingCompleted(true)
                    _uiState.update { it.copy(isCompleted = true, isSaving = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message, isSaving = false) }
                }
        }
    }
}
