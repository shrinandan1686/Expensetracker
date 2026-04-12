package com.trackit.expense.presentation.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.usecase.AddExpenseUseCase
import com.trackit.expense.overlay.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import com.trackit.expense.util.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.trackit.expense.domain.usecase.GetExpenseByIdUseCase

/**
 * UI state for the manual Add Expense screen.
 *
 * @property amountInput     Raw string from the amount text field.
 * @property merchant        Merchant name typed by the user.
 * @property selectedCategory Currently selected [ExpenseCategory].
 * @property account         Account/card identifier (optional free text).
 * @property notes           Optional note.
 * @property isSubmitting    True while the Room write is in progress.
 * @property isSaved         True once the expense has been saved — triggers nav back.
 * @property errorMessage    Validation or save error message.
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
    val rawSms: String                   = ""
)

/**
 * ViewModel for [AddExpenseScreen].
 *
 * Handles form field updates, client-side validation, and persisting the expense
 * via [AddExpenseUseCase] (offline-first — writes to Room immediately).
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val addExpenseUseCase: AddExpenseUseCase,
    private val getExpenseByIdUseCase: GetExpenseByIdUseCase,
    private val locationHelper: LocationHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            amountInput = savedStateHandle.get<String>("amount") ?: "",
            merchant    = savedStateHandle.get<String>("merchant") ?: "",
            account     = savedStateHandle.get<String>("account")?.let { "XX$it" } ?: ""
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        // Infer category if merchant was pre-filled
        val prefilledMerch = savedStateHandle.get<String>("merchant")
        if (!prefilledMerch.isNullOrBlank()) {
            _uiState.update { it.copy(selectedCategory = ExpenseCategory.inferFrom(prefilledMerch)) }
        }
        
        val expenseId = savedStateHandle.get<String>("expenseId")
        if (expenseId != null) {
            viewModelScope.launch {
                getExpenseByIdUseCase(expenseId).firstOrNull()?.let { existingExpense ->
                    _uiState.update { it.copy(
                        expenseId = existingExpense.id,
                        amountInput = if (existingExpense.amount > 0) existingExpense.amount.toString() else it.amountInput,
                        merchant = existingExpense.merchant.ifBlank { it.merchant },
                        account = existingExpense.account.ifBlank { it.account },
                        selectedCategory = ExpenseCategory.inferFrom(existingExpense.category),
                        notes = existingExpense.notes ?: "",
                        latitude = existingExpense.latitude,
                        longitude = existingExpense.longitude,
                        locationAddress = existingExpense.locationAddress,
                        transactionAt = existingExpense.transactionAt,
                        createdAt = existingExpense.createdAt,
                        rawSms = existingExpense.rawSms
                    ) }
                    
                    // Fallback: If background SmsReceiver couldn't get the location (due to permission limits), 
                    // fetch it now while the app is in the foreground
                    if (existingExpense.latitude == null) {
                        fetchLocation()
                    }
                }
            }
        } else {
            // Fetch current location
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            locationHelper.getCurrentLocation()?.let { location ->
                _uiState.update { it.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    locationAddress = location.address
                ) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input event handlers (one per form field)
    // ─────────────────────────────────────────────────────────────────────────

    fun onAmountChanged(input: String) =
        _uiState.update { it.copy(amountInput = input, errorMessage = null) }

    fun onMerchantChanged(value: String) =
        _uiState.update { it.copy(merchant = value) }

    fun onCategorySelected(cat: ExpenseCategory) =
        _uiState.update { it.copy(selectedCategory = cat) }

    fun onAccountChanged(value: String) =
        _uiState.update { it.copy(account = value) }

    fun onNotesChanged(value: String) =
        _uiState.update { it.copy(notes = value) }

    // ─────────────────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────────────────

    fun onSaveExpense() {
        val state = _uiState.value

        // Validate amount
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
            id            = state.expenseId ?: java.util.UUID.randomUUID().toString(),
            amount        = amount,
            merchant      = state.merchant.trim(),
            category      = state.selectedCategory.displayName,
            account       = state.account.trim(),
            notes         = state.notes.trim().takeIf { it.isNotBlank() },
            rawSms        = state.rawSms,
            isLogged      = true,             // user manually entered it
            loggedAt      = System.currentTimeMillis(),
            transactionAt = state.transactionAt ?: System.currentTimeMillis(),
            createdAt     = state.createdAt ?: System.currentTimeMillis(),
            latitude      = state.latitude,
            longitude     = state.longitude,
            locationAddress = state.locationAddress
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            addExpenseUseCase(expense)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSaved = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message) }
                }
        }
    }

    fun onErrorDismissed() =
        _uiState.update { it.copy(errorMessage = null) }
}
