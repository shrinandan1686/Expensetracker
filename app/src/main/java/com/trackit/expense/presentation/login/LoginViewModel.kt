package com.trackit.expense.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data object Success : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onGoogleSignInResult(idToken: String?) {
        if (idToken == null) {
            _uiState.value = UiState.Error("Sign-in cancelled or failed")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.signInWithGoogle(idToken)
                .onSuccess { _uiState.value = UiState.Success }
                .onFailure { _uiState.value = UiState.Error(it.message ?: "Sign-in failed") }
        }
    }

    fun clearError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
