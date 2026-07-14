package vn.baokim.qa.ui.pat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.pat.PatRepository
import vn.baokim.qa.data.pat.SaveResult
import javax.inject.Inject

/**
 * State for the PAT screen (E3, #5). [input] holds the PAT only while the user is typing
 * it into the field — it is never persisted or logged, and is cleared right after a save.
 */
data class PatUiState(
    val loadingStatus: Boolean = true,
    val statusUnknown: Boolean = false, // couldn't reach the server to check
    val hasPat: Boolean = false,
    val input: String = "",
    val revealed: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val message: String? = null, // one-shot feedback for the snackbar
) {
    val canSave: Boolean get() = input.isNotBlank() && !saving && !deleting
    val busy: Boolean get() = saving || deleting
}

@HiltViewModel
class PatViewModel @Inject constructor(
    private val repository: PatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PatUiState())
    val state: StateFlow<PatUiState> = _state.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        _state.update { it.copy(loadingStatus = true) }
        viewModelScope.launch {
            val has = repository.hasPat()
            _state.update {
                it.copy(
                    loadingStatus = false,
                    statusUnknown = has == null,
                    hasPat = has ?: it.hasPat,
                )
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun toggleReveal() = _state.update { it.copy(revealed = !it.revealed) }

    fun save() {
        val pat = _state.value.input.trim()
        if (pat.isEmpty() || _state.value.busy) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.savePat(pat)
            _state.update {
                when (result) {
                    is SaveResult.Success -> it.copy(
                        saving = false,
                        hasPat = true,
                        statusUnknown = false,
                        input = "", // drop the secret from state immediately
                        revealed = false,
                        message = result.message ?: MSG_SAVED,
                    )
                    is SaveResult.Failure -> it.copy(
                        saving = false,
                        message = result.message ?: MSG_SAVE_ERROR,
                    )
                }
            }
        }
    }

    fun delete() {
        if (_state.value.busy) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val ok = repository.deletePat()
            _state.update {
                it.copy(
                    deleting = false,
                    hasPat = if (ok) false else it.hasPat,
                    message = if (ok) MSG_DELETED else MSG_DELETE_ERROR,
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val MSG_SAVED = "Đã lưu PAT"
        const val MSG_SAVE_ERROR = "Lỗi mạng khi lưu PAT"
        const val MSG_DELETED = "Đã xoá PAT"
        const val MSG_DELETE_ERROR = "Lỗi xoá PAT"
    }
}
