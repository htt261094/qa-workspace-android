package vn.baokim.qa.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.dashboard.DashboardRepository
import vn.baokim.qa.data.dashboard.DashboardResult
import vn.baokim.qa.domain.dashboard.Dashboard
import vn.baokim.qa.domain.dashboard.DashboardFilter
import vn.baokim.qa.domain.dashboard.DashboardTask
import javax.inject.Inject

/** Distinguishes the dashboard's failure modes so the UI can explain each (D7). */
enum class DashboardError { FORBIDDEN, UNAVAILABLE, GENERIC }

/**
 * State for the team Dashboard screen (E6, #9). Network-only (no Room cache — D7): the last
 * successful snapshot stays in [data] across refreshes so a failed refresh keeps the numbers
 * on screen with only a stale hint. [filter] slices [data.tasks] for the table (E6.3),
 * client-side, without touching the KPI counts.
 */
data class DashboardUiState(
    val data: Dashboard = Dashboard.EMPTY,
    val filter: DashboardFilter = DashboardFilter.ACTIVE,
    val loading: Boolean = true,      // first load, nothing on screen yet → full-screen spinner
    val refreshing: Boolean = false,  // pull-to-refresh while a snapshot is already shown
    val error: DashboardError? = null,
) {
    val hasContent: Boolean get() = data.tasks.isNotEmpty() || data.workload.isNotEmpty()

    /** Rows for the currently selected table filter (E6.3). */
    val filteredTasks: List<DashboardTask> get() = filter.apply(data.tasks)

    /** Count shown on each filter chip — derived from the loaded tasks, not the KPI meta. */
    fun chipCount(f: DashboardFilter): Int = f.apply(data.tasks).size

    /** No snapshot to fall back on and the last load failed → full-screen error. */
    val isErrorEmpty: Boolean get() = error != null && !hasContent

    /** Loaded fine but the team genuinely has no tasks. */
    val isEmpty: Boolean get() = !loading && error == null && !hasContent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onPullRefresh() = refresh(pull = true)

    fun retry() = refresh()

    fun onFilterSelected(filter: DashboardFilter) = _state.update { it.copy(filter = filter) }

    private fun refresh(pull: Boolean = false) {
        _state.update {
            it.copy(refreshing = pull, loading = !pull && !it.hasContent, error = null)
        }
        viewModelScope.launch {
            when (val result = repository.load()) {
                is DashboardResult.Success -> _state.update {
                    it.copy(data = result.data, loading = false, refreshing = false, error = null)
                }
                DashboardResult.Forbidden -> fail(DashboardError.FORBIDDEN)
                DashboardResult.Unavailable -> fail(DashboardError.UNAVAILABLE)
                DashboardResult.Error -> fail(DashboardError.GENERIC)
            }
        }
    }

    private fun fail(error: DashboardError) = _state.update {
        it.copy(loading = false, refreshing = false, error = error)
    }
}
