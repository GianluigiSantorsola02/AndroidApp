package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.data.local.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class PinMode { CREATE, CONFIRM, UNLOCK }

data class PinUiState(
    val pin: String = "",
    val mode: PinMode = PinMode.UNLOCK,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val tempPin: String = "",
    val isInitialized: Boolean = false
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repository: SecurityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Tentativo di lettura con timeout per evitare blocchi infiniti
            val hasPin = withTimeoutOrNull(1000) {
                repository.hasPinSet.firstOrNull()
            } ?: false

            _uiState.value = _uiState.value.copy(
                mode = if (hasPin) PinMode.UNLOCK else PinMode.CREATE,
                isInitialized = true
            )
        }
    }

    fun onNumberClick(number: String) {
        if (_uiState.value.pin.length < 4) {
            val newPin = _uiState.value.pin + number
            _uiState.value = _uiState.value.copy(pin = newPin, error = null)
        }
    }

    fun onCancelClick() {
        if (_uiState.value.pin.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(pin = _uiState.value.pin.dropLast(1))
        }
    }

    fun onContinue() {
        val state = _uiState.value
        if (state.pin.length != 4) return

        viewModelScope.launch {
            when (state.mode) {
                PinMode.CREATE -> {
                    _uiState.value = state.copy(mode = PinMode.CONFIRM, tempPin = state.pin, pin = "")
                }
                PinMode.CONFIRM -> {
                    if (state.pin == state.tempPin) {
                        repository.savePin(state.pin)
                        _uiState.value = state.copy(isAuthenticated = true)
                    } else {
                        _uiState.value = state.copy(
                            mode = PinMode.CREATE,
                            pin = "",
                            tempPin = "",
                            error = "I PIN non coincidono. Riprova."
                        )
                    }
                }
                PinMode.UNLOCK -> {
                    if (repository.verifyPin(state.pin)) {
                        _uiState.value = state.copy(isAuthenticated = true)
                    } else {
                        _uiState.value = state.copy(pin = "", error = "PIN errato.")
                    }
                }
            }
        }
    }
}
