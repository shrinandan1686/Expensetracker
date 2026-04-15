package com.trackit.expense.presentation.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.domain.usecase.DeleteExpenseUseCase
import com.trackit.expense.domain.usecase.GetExpenseByIdUseCase
import com.trackit.expense.overlay.ExpenseCategory
import com.trackit.expense.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the manual Add Expense screen.
 *
 * @property duplicateExpenseId  If non-null, a possible duplicate with this ID exists in DB.
 *                               Drives [DuplicateWarningCard] in the UI.
 * @property duplicateDismissed  True once the user has explicitly dismissed the warning.
 */
data class AddExpenseUiState(
    val expenseId: String?               = null,
    val amountInput: String              = "",
    val merchant: String                 = "",
    val selectedCategory: ExpenseCategory = ExpenseCategory.OTHERS,
    val account: String                  = "",
    val notes: String                    = "",
    val isSubmitting: Boolean            = false,
    val isSaved: Boolean                 = false,
    val errorMessage: String?            = null,
    val latitude: Double?                = null,
    val longitude: Double?               = null,
    val locationAddress: String?         = null,
    val transactionAt: Long?             = null,
    val createdAt: Long?                 = null,
    val rawSms: String                   = "",
    // ── Duplicate detection ────────────────────────────────────────────────
    val duplicateExpenseId: String?      = null,
    val duplicateDismissed: Boolean      = false
) {
    /** True if the warning card should be visible. */
    val showDuplicateWarning: Boolean
        get() = duplicateExpenseId != null && !duplicateDismissed
}

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase:    AddExpenseUseCase,
    private val getExpenseByIdUseCase: GetExpenseByIdUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val locationHelper:       LocationHelper,
    savedStateHandle:                 SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            amountInput = savedStateHandle.get<String>("amount") ?: "",
            merchant    = savedStateHandle.get<String>("merchant") ?: "",
            account     = savedStateHandle.get<String>("account")?.let { "XX$it" } ?: "",
            // "isDuplicate" flag and "duplicateId" arrive from the notification deep link
            duplicateExpenseId = if (savedStateHandle.get<String>("isDuplicate") == "true")
                                     savedStateHandle.get<String>("duplicateId")
                                 else null
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        val prefilledMerch = savedStateHandle.get<String>("merchant")
        if (!prefilledMerch.isNullOrBlank()) {
            _uiState.update { it.copy(selectedCategory = ExpenseCategory.inferFrom(prefilledMerch)) }
        }

        val expenseId = savedStateHandle.get<String>("expenseId")
        if (expenseId != null) {
            viewModelScope.launch {
                getExpenseByIdUseCase(expenseId).firstOrNull()?.let { existing ->
                    _uiState.update {
                        it.copy(
                            expenseId        = existing.id,
                            amountInput      = if (existing.amount > 0) existing.amount.toString() else it.amountInput,
                            merchant         = existing.merchant.ifBlank { it.merchant },
                            account          = existing.account.ifBlank { it.account },
                            selectedCategory = ExpenseCategory.inferFrom(existing.category),
                            notes            = existing.notes ?: "",
                            latitude         = existing.latitude,
                            longitude        = existing.longitude,
                            locationAddress  = existing.locationAddress,
                            transactionAt    = existing.transactionAt,
                            createdAt        = existing.createdAt,
                            rawSms           = existing.rawSms
                        )
                    }
                    if (existing.latitude == null) fetchLocation()
                }
            }
        } else {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            locationHelper.getCurrentLocation()?.let { loc ->
                _uiState.update {
                    it.copy(latitude = loc.latitude, longitude = loc.longitude, locationAddress = loc.address)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input handlers
    // ─────────────────────────────────────────────────────────────────────────

    fun onAmountChanged(input: String)          = _uiState.update { it.copy(amountInput = input, errorMessage = null) }
    fun onMerchantChanged(value: String)        = _uiState.update { it.copy(merchant = value) }
    fun onCategorySelected(cat: ExpenseCategory) = _uiState.update { it.copy(selectedCategory = cat) }
    fun onAccountChanged(value: String)         = _uiState.update { it.copy(account = value) }
    fun onNotesChanged(value: String)           = _uiState.update { it.copy(notes = value) }
    fun onErrorDismissed()                      = _uiState.update { it.copy(errorMessage = null) }

    // ─────────────────────────────────────────────────────────────────────────
    // Duplicate warning actions
    // ─────────────────────────────────────────────────────────────────────────

    /** User tapped "Save Anyway" on the duplicate warning — dismiss and continue. */
    fun onDismissDuplicateWarning() {
        _uiState.update { it.copy(duplicateDismissed = true) }
    }

    /**
     * User tapped "Discard" on the duplicate warning.
     * Deletes the auto-saved backup expense (which was saved with isLogged=false by
     * SmsReceiver) and signals the UI to navigate back.
     */
    fun onDiscardDuplicate() {
        val idToDelete = _uiState.value.expenseId ?: run {
            _uiState.update { it.copy(isSaved = true) } // nav back
            return
        }
        viewModelScope.launch {
            deleteExpenseUseCase(idToDelete)
                .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────────────────

    fun onSaveExpense() {
        val state  = _uiState.value
        val amount = state.amountInput.replace(",", "").toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid amount.") }
            return
        }
        if (state.merchant.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Merchant name is required.") }
            return
        }

        val expense = Expense(
            id              = state.expenseId ?: java.util.UUID.randomUUID().toString(),
            amount          = amount,
            merchant        = state.merchant.trim(),
            category        = state.selectedCategory.displayName,
            account         = state.account.trim(),
            notes           = state.notes.trim().takeIf { it.isNotBlank() },
            rawSms          = state.rawSms,
            isLogged        = true,
            loggedAt        = System.currentTimeMillis(),
            transactionAt   = state.transactionAt ?: System.currentTimeMillis(),
            createdAt       = state.createdAt ?: System.currentTimeMillis(),
            latitude        = state.latitude,
            longitude       = state.longitude,
            locationAddress = state.locationAddress
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            addExpenseUseCase(expense)
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, isSaved = true) } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message) } }
        }
    }
}
