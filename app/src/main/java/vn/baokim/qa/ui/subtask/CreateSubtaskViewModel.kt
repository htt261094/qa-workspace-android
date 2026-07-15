package vn.baokim.qa.ui.subtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.subtask.CreateSubtasksResult
import vn.baokim.qa.data.subtask.SubtaskRepository
import vn.baokim.qa.domain.subtask.CreatedSubtask
import vn.baokim.qa.domain.subtask.ParentTask
import vn.baokim.qa.domain.subtask.Person
import java.time.LocalDate
import javax.inject.Inject

/** A type-ahead picker slot (parent / assignee / leader). */
data class PickerState<T>(
    val query: String = "",
    val results: List<T> = emptyList(),
    val searching: Boolean = false,
    val selected: T? = null,
)

/**
 * State for the create-QA-sub-task screen (E9, #14). One parent (Task-PTSP) + one or more
 * summaries (one per non-blank line) become one or many sub-tasks under it. Start date defaults
 * to today, due date is required. Leader defaults to Hiền (spec §; auto-fill like the web);
 * assignee is optional. All three pickers are `/search-*` type-aheads.
 */
data class CreateSubtaskUiState(
    val parent: PickerState<ParentTask> = PickerState(),
    val assignee: PickerState<Person> = PickerState(),
    val leader: PickerState<Person> = PickerState(selected = DEFAULT_LEADER),

    val summaries: String = "",
    val startDate: String = LocalDate.now().toString(), // yyyy-MM-dd, default today
    val dueDate: String = "",

    val submitting: Boolean = false,
    /** Sub-tasks created by the last submit — shown as a success panel (with Jira links). */
    val created: List<CreatedSubtask> = emptyList(),

    val message: String? = null, // one-shot snackbar (server's Vietnamese msg or a fallback)
) {
    val titleLines: List<String> get() = summaries.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val lineCount: Int get() = titleLines.size
    val canSubmit: Boolean
        get() = parent.selected != null && lineCount > 0 &&
            startDate.isNotBlank() && dueDate.isNotBlank() && !submitting

    companion object {
        /** Leader mặc định = Hiền (username hiennt19), pre-selected like the web form. */
        val DEFAULT_LEADER = Person("hiennt19", "Hiền")
    }
}

@HiltViewModel
class CreateSubtaskViewModel @Inject constructor(
    private val repository: SubtaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSubtaskUiState())
    val state: StateFlow<CreateSubtaskUiState> = _state.asStateFlow()

    private var parentJob: Job? = null
    private var assigneeJob: Job? = null
    private var leaderJob: Job? = null

    // --- parent picker -------------------------------------------------------

    fun onParentQuery(q: String) {
        _state.update { it.copy(parent = it.parent.copy(query = q, selected = null)) }
        parentJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(parent = it.parent.copy(results = emptyList(), searching = false)) }
            return
        }
        parentJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(parent = it.parent.copy(searching = true)) }
            val results = repository.searchParents(q)
            _state.update { it.copy(parent = it.parent.copy(results = results, searching = false)) }
        }
    }

    fun pickParent(p: ParentTask) {
        parentJob?.cancel()
        _state.update { st ->
            // Auto-fill "[QA] <parent summary>" only when the user hasn't typed anything yet,
            // so re-picking a parent never clobbers summaries they already entered (web always
            // overwrites; we're deliberately friendlier).
            val summaries = if (st.summaries.isBlank()) prefixQa(p.summary) else st.summaries
            st.copy(
                parent = st.parent.copy(selected = p, query = "", results = emptyList()),
                summaries = summaries,
            )
        }
    }

    fun clearParent() {
        parentJob?.cancel()
        _state.update { it.copy(parent = PickerState()) }
    }

    // --- assignee picker (optional) ------------------------------------------

    fun onAssigneeQuery(q: String) {
        _state.update { it.copy(assignee = it.assignee.copy(query = q, selected = null)) }
        assigneeJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(assignee = it.assignee.copy(results = emptyList(), searching = false)) }
            return
        }
        assigneeJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(assignee = it.assignee.copy(searching = true)) }
            val results = repository.searchPeople(q)
            _state.update { it.copy(assignee = it.assignee.copy(results = results, searching = false)) }
        }
    }

    fun pickAssignee(p: Person) {
        assigneeJob?.cancel()
        _state.update { it.copy(assignee = it.assignee.copy(selected = p, query = "", results = emptyList())) }
    }

    fun clearAssignee() {
        assigneeJob?.cancel()
        _state.update { it.copy(assignee = PickerState()) }
    }

    // --- leader picker (default Hiền) ----------------------------------------

    fun onLeaderQuery(q: String) {
        _state.update { it.copy(leader = it.leader.copy(query = q, selected = null)) }
        leaderJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(leader = it.leader.copy(results = emptyList(), searching = false)) }
            return
        }
        leaderJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(leader = it.leader.copy(searching = true)) }
            val results = repository.searchPeople(q)
            _state.update { it.copy(leader = it.leader.copy(results = results, searching = false)) }
        }
    }

    fun pickLeader(p: Person) {
        leaderJob?.cancel()
        _state.update { it.copy(leader = it.leader.copy(selected = p, query = "", results = emptyList())) }
    }

    fun clearLeader() {
        leaderJob?.cancel()
        _state.update { it.copy(leader = PickerState()) }
    }

    // --- other fields --------------------------------------------------------

    fun onSummariesChange(text: String) = _state.update { it.copy(summaries = text) }
    fun onStartDate(date: String) = _state.update { it.copy(startDate = date) }
    fun onDueDate(date: String) = _state.update { it.copy(dueDate = date) }

    // --- submit --------------------------------------------------------------

    fun submit() {
        val st = _state.value
        val parent = st.parent.selected ?: return
        val titles = st.titleLines
        if (titles.isEmpty() || st.startDate.isBlank() || st.dueDate.isBlank() || st.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = repository.createSubtasks(
                parent = parent.key,
                summaries = titles,
                startDate = st.startDate,
                dueDate = st.dueDate,
                assignee = st.assignee.selected?.name.orEmpty(),
                leader = st.leader.selected?.name.orEmpty(),
            )
            when (result) {
                is CreateSubtasksResult.Done -> onDone(result)
                is CreateSubtasksResult.Failure ->
                    _state.update { it.copy(submitting = false, message = failMsg(result.message, result.noPat)) }
            }
        }
    }

    private fun onDone(done: CreateSubtasksResult.Done) {
        val created = done.created
        val failed = done.failed
        when {
            created.isEmpty() -> // every summary failed — keep them in the field to fix & retry
                _state.update {
                    it.copy(
                        submitting = false,
                        message = failed.firstOrNull()?.let { f -> "$MSG_FAIL_PREFIX${f.msg}" } ?: MSG_CREATE_ERROR,
                    )
                }
            failed.isEmpty() -> // full success — clear summaries, show what was created
                _state.update {
                    it.copy(
                        submitting = false,
                        summaries = "",
                        created = created,
                        message = "Đã tạo ${created.size} sub-task ✓",
                    )
                }
            else -> // partial — keep only the failed lines so the user can retry them
                _state.update {
                    it.copy(
                        submitting = false,
                        summaries = failed.joinToString("\n") { f -> f.summary },
                        created = created,
                        message = "Đã tạo ${created.size}, lỗi ${failed.size} (giữ lại dòng lỗi)",
                    )
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun failMsg(serverMsg: String?, noPat: Boolean): String =
        serverMsg ?: if (noPat) MSG_NO_PAT else MSG_CREATE_ERROR

    private companion object {
        const val DEBOUNCE_MS = 300L

        /** Prefix "[QA] " unless the parent summary already carries it (case-insensitive). */
        fun prefixQa(summary: String): String {
            val t = summary.trim()
            return if (t.isEmpty()) "" else if (Regex("^\\[QA]", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "[QA] $t"
        }

        const val MSG_FAIL_PREFIX = "Không tạo được: "
        const val MSG_CREATE_ERROR = "Lỗi tạo sub-task, thử lại"
        const val MSG_NO_PAT = "Bạn chưa cấu hình PAT. Vào Cài đặt để thêm."
    }
}
