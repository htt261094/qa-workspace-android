package vn.baokim.qa.ui.buglog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.buglog.BugLogRefresh
import vn.baokim.qa.data.buglog.BugLogRepository
import vn.baokim.qa.data.buglog.ExportResult
import vn.baokim.qa.data.buglog.LinkResult
import vn.baokim.qa.domain.buglog.Bug
import vn.baokim.qa.domain.buglog.BugFilter
import vn.baokim.qa.domain.buglog.LinkFilter
import javax.inject.Inject

/**
 * State for the Bug Log table (E8.2/E8.4/E8.5/E8.6). Offline-first: [bugs] is backed by the
 * Room cache so the list shows instantly, and a network refresh replaces it. The month tabs
 * and tester/dev option lists are derived from the cached rows (D10 — endpoint is flat). A
 * failed refresh keeps the cache visible with a stale hint.
 */
data class BugLogUiState(
    val bugs: List<Bug> = emptyList(),
    val editable: Boolean = false,       // from backend (false for DEV) — gates link editing
    val syncedAt: String = "",
    val selectedMonth: String? = null,   // null → first available month
    val filter: BugFilter = BugFilter(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: Boolean = false,
    val message: String? = null,         // one-shot snackbar (link/export outcome)
    val exportUri: Uri? = null,          // one-shot share trigger
    val exportName: String = "",
) {
    /** Months present in the cache, newest-first ("yyyy-MM" sorts lexicographically). */
    val months: List<String> get() = bugs.mapNotNull { it.month.ifBlank { null } }.distinct().sortedDescending()

    val effectiveMonth: String? get() = selectedMonth?.takeIf { it in months } ?: months.firstOrNull()

    private val monthBugs: List<Bug> get() = effectiveMonth?.let { m -> bugs.filter { it.month == m } } ?: emptyList()

    /** Distinct tester / dev names in the current month, for the filter dropdowns. */
    val testers: List<String> get() = monthBugs.mapNotNull { it.tester.ifBlank { null } }.distinct().sorted()
    val devs: List<String> get() = monthBugs.mapNotNull { it.dev.ifBlank { null } }.distinct().sorted()

    /** Rows shown in the table: current month, then the tester/dev/link filter. */
    val visibleBugs: List<Bug> get() = filter.apply(monthBugs)

    val hasContent: Boolean get() = bugs.isNotEmpty()
    val isErrorEmpty: Boolean get() = error && !hasContent
    val isEmpty: Boolean get() = !loading && !error && !hasContent
}

@HiltViewModel
class BugLogViewModel @Inject constructor(
    private val repository: BugLogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BugLogUiState())
    val state: StateFlow<BugLogUiState> = _state.asStateFlow()

    init {
        // Room cache stream folded into state so the list survives cold start / offline (E8.6).
        repository.observeBugs()
            .onEach { cached -> _state.update { it.copy(bugs = cached) } }
            .launchIn(viewModelScope)
        refresh()
    }

    fun onPullRefresh() = refresh(pull = true)

    fun retry() = refresh()

    fun onMonthSelected(month: String) = _state.update { it.copy(selectedMonth = month, filter = BugFilter()) }

    fun onTesterSelected(tester: String) = _state.update { it.copy(filter = it.filter.copy(tester = tester)) }

    fun onDevSelected(dev: String) = _state.update { it.copy(filter = it.filter.copy(dev = dev)) }

    fun onLinkFilterSelected(link: LinkFilter) = _state.update { it.copy(filter = it.filter.copy(link = link)) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onExportConsumed() = _state.update { it.copy(exportUri = null) }

    private fun refresh(pull: Boolean = false) {
        _state.update { it.copy(refreshing = pull, loading = !pull && !it.hasContent, error = false) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is BugLogRefresh.Success -> _state.update {
                    it.copy(
                        editable = result.editable,
                        syncedAt = result.syncedAt,
                        loading = false,
                        refreshing = false,
                        error = false,
                    )
                }
                BugLogRefresh.Error -> _state.update { it.copy(loading = false, refreshing = false, error = true) }
            }
        }
    }

    /** Add/remove one bug↔task link (E8.4), then refresh so the chips reflect it. */
    fun link(bugKey: String, taskKey: String, add: Boolean) {
        val key = taskKey.trim()
        if (key.isBlank()) return
        viewModelScope.launch {
            val ok = repository.link(bugKey, key, add) == LinkResult.Success
            if (ok) refresh(pull = true)
            _state.update { it.copy(message = if (ok) MSG_LINK_OK else MSG_LINK_FAIL) }
        }
    }

    /** Export the currently-visible rows to Excel (E8.5); emits a share Uri on success. */
    fun export() {
        val s = _state.value
        val rows = s.visibleBugs.map { b ->
            listOf(b.id, b.module, b.summary, b.created, b.status, b.tester, b.dev)
        }
        if (rows.isEmpty()) {
            _state.update { it.copy(message = MSG_EXPORT_EMPTY) }
            return
        }
        val name = "bug-log${s.effectiveMonth?.let { "-$it" } ?: ""}.xlsx"
        viewModelScope.launch {
            when (val result = repository.export(rows, name)) {
                is ExportResult.Success ->
                    _state.update { it.copy(exportUri = result.uri, exportName = result.filename) }
                ExportResult.Error -> _state.update { it.copy(message = MSG_EXPORT_FAIL) }
            }
        }
    }

    private companion object {
        const val MSG_LINK_OK = "Đã cập nhật liên kết task"
        const val MSG_LINK_FAIL = "Không cập nhật được liên kết"
        const val MSG_EXPORT_EMPTY = "Không có dòng nào để export"
        const val MSG_EXPORT_FAIL = "Export thất bại"
    }
}
