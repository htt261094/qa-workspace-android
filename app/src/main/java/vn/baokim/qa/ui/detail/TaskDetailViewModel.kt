package vn.baokim.qa.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.detail.CustomStatusResult
import vn.baokim.qa.data.detail.DetailRepository
import vn.baokim.qa.data.detail.DetailResult
import vn.baokim.qa.data.detail.DueDatePermResult
import vn.baokim.qa.data.detail.TransitionsResult
import vn.baokim.qa.data.detail.WriteResult
import vn.baokim.qa.domain.detail.CustomStatus
import vn.baokim.qa.domain.detail.TaskDetail
import vn.baokim.qa.domain.detail.Transition
import vn.baokim.qa.ui.navigation.DetailArgs
import javax.inject.Inject

/**
 * State for the task detail screen (E5, #10). [detail] is loaded fresh from `/issue-comments`
 * (E5.1). Writes — comment (E5.2), status transition (E5.3), due date (E5.4) — go out on the
 * personal PAT and, on success, trigger a reload so the screen reflects the change. The
 * custom-status overlay (E5.5) is internal (no PAT); its label set seeds from the tapped
 * task's `customs` (nav arg, since the detail endpoint doesn't carry them) and updates from
 * each toggle's returned list.
 */
data class TaskDetailUiState(
    val key: String,
    val loading: Boolean = true,
    val error: Boolean = false,
    val detail: TaskDetail? = null,

    val customs: Set<CustomStatus> = emptySet(),
    val customBusy: Boolean = false,

    val transitions: List<Transition> = emptyList(),
    val transitionsLoading: Boolean = false,
    val transitionsError: String? = null,
    val statusBusy: Boolean = false,

    val canEditDue: Boolean = false,
    val dueBusy: Boolean = false,

    val commentDraft: String = "",
    val commentSending: Boolean = false,

    val message: String? = null, // one-shot snackbar (server's Vietnamese msg or a fallback)
) {
    val canSendComment: Boolean get() = commentDraft.isNotBlank() && !commentSending
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repository: DetailRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val key: String = savedStateHandle[DetailArgs.KEY] ?: ""

    private val _state = MutableStateFlow(
        TaskDetailUiState(
            key = key,
            customs = DetailArgs.parseCustoms(savedStateHandle[DetailArgs.CUSTOMS]),
        )
    )
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    init {
        load()
        checkDuePerm()
    }

    fun retry() = load()

    private fun load() {
        _state.update { it.copy(loading = it.detail == null, error = false) }
        viewModelScope.launch {
            when (val result = repository.load(key)) {
                is DetailResult.Success ->
                    _state.update { it.copy(loading = false, error = false, detail = result.detail) }
                DetailResult.Error ->
                    _state.update { it.copy(loading = false, error = it.detail == null) }
            }
        }
    }

    // --- comment (E5.2) ------------------------------------------------------

    fun onCommentChange(text: String) = _state.update { it.copy(commentDraft = text) }

    fun sendComment() {
        val body = _state.value.commentDraft.trim()
        if (body.isEmpty() || _state.value.commentSending) return
        _state.update { it.copy(commentSending = true) }
        viewModelScope.launch {
            when (val r = repository.addComment(key, body)) {
                is WriteResult.Success -> {
                    _state.update { it.copy(commentSending = false, commentDraft = "", message = r.message ?: MSG_COMMENT_OK) }
                    load() // pull the new comment into the history
                }
                is WriteResult.Failure ->
                    _state.update { it.copy(commentSending = false, message = failMsg(r.message, r.noPat)) }
            }
        }
    }

    // --- status transition (E5.3) --------------------------------------------

    /** Lazily fetch the available transitions when the user opens the status picker. */
    fun loadTransitions() {
        if (_state.value.transitionsLoading) return
        _state.update { it.copy(transitionsLoading = true, transitionsError = null, transitions = emptyList()) }
        viewModelScope.launch {
            when (val r = repository.transitions(key)) {
                is TransitionsResult.Success ->
                    _state.update { it.copy(transitionsLoading = false, transitions = r.transitions) }
                is TransitionsResult.Failure ->
                    _state.update { it.copy(transitionsLoading = false, transitionsError = failMsg(r.message, r.noPat)) }
            }
        }
    }

    fun applyTransition(transition: Transition) {
        if (_state.value.statusBusy) return
        _state.update { it.copy(statusBusy = true) }
        viewModelScope.launch {
            when (val r = repository.doTransition(key, transition.id)) {
                is WriteResult.Success -> {
                    _state.update { it.copy(statusBusy = false, transitions = emptyList(), message = r.message ?: MSG_STATUS_OK) }
                    load() // reflect the new status
                }
                is WriteResult.Failure ->
                    _state.update { it.copy(statusBusy = false, message = failMsg(r.message, r.noPat)) }
            }
        }
    }

    // --- due date (E5.4) -----------------------------------------------------

    private fun checkDuePerm() {
        viewModelScope.launch {
            val canEdit = (repository.dueDatePerm(key) as? DueDatePermResult.Success)?.canEdit == true
            _state.update { it.copy(canEditDue = canEdit) }
        }
    }

    /** [date] = "yyyy-MM-dd", or "" to clear. */
    fun setDueDate(date: String) {
        if (_state.value.dueBusy) return
        _state.update { it.copy(dueBusy = true) }
        viewModelScope.launch {
            when (val r = repository.setDueDate(key, date)) {
                is WriteResult.Success -> {
                    _state.update { it.copy(dueBusy = false, message = r.message ?: MSG_DUE_OK) }
                    load()
                }
                is WriteResult.Failure ->
                    _state.update { it.copy(dueBusy = false, message = failMsg(r.message, r.noPat)) }
            }
        }
    }

    // --- custom status overlay (E5.5) ----------------------------------------

    fun toggleCustom(status: CustomStatus) {
        if (_state.value.customBusy) return
        _state.update { it.copy(customBusy = true) }
        val summary = _state.value.detail?.summary ?: ""
        viewModelScope.launch {
            when (val r = repository.toggleCustomStatus(key, status.value, summary)) {
                is CustomStatusResult.Success ->
                    _state.update { it.copy(customBusy = false, customs = r.values.toSet()) }
                CustomStatusResult.Error ->
                    _state.update { it.copy(customBusy = false, message = MSG_CUSTOM_ERROR) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun failMsg(serverMsg: String?, noPat: Boolean): String =
        serverMsg ?: if (noPat) MSG_NO_PAT else MSG_WRITE_ERROR

    private companion object {
        const val MSG_COMMENT_OK = "Đã gửi comment"
        const val MSG_STATUS_OK = "Đã đổi status"
        const val MSG_DUE_OK = "Đã cập nhật hạn"
        const val MSG_CUSTOM_ERROR = "Không đổi được nhãn tình trạng"
        const val MSG_NO_PAT = "Bạn chưa cấu hình PAT. Vào Cài đặt để thêm."
        const val MSG_WRITE_ERROR = "Thao tác thất bại, thử lại"
    }
}
