package com.trackit.expense.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.UserProfile
import com.trackit.expense.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(
            val profile: UserProfile,
            val isSaving: Boolean = false,
            val savedSuccess: Boolean = false
        ) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            userRepository.getProfile()
                .onSuccess { _uiState.value = UiState.Loaded(it) }
                .onFailure { _uiState.value = UiState.Error(it.message ?: "Could not load profile") }
        }
    }

    fun save(name: String, upiId: String) {
        val current = _uiState.value as? UiState.Loaded ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, savedSuccess = false)
            userRepository.updateProfile(name, upiId)
                .onSuccess {
                    _uiState.value = current.copy(
                        profile = current.profile.copy(name = name, upiId = upiId),
                        isSaving = false,
                        savedSuccess = true
                    )
                }
                .onFailure { _uiState.value = current.copy(isSaving = false) }
        }
    }

    fun clearSavedSuccess() {
        val current = _uiState.value as? UiState.Loaded ?: return
        _uiState.value = current.copy(savedSuccess = false)
    }
}
