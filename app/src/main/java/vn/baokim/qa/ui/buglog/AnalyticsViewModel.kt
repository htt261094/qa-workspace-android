package vn.baokim.qa.ui.buglog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.baokim.qa.data.buglog.AnalyticsRepository
import vn.baokim.qa.data.buglog.AnalyticsResult
import vn.baokim.qa.domain.buglog.Analytics
import vn.baokim.qa.domain.buglog.MonthMetrics
import javax.inject.Inject

/** Distinguishes the analytics failure modes so the UI can explain each (D10). */
enum class AnalyticsError { FORBIDDEN, UNAVAILABLE, GENERIC }

/**
 * State for the analytics screen (E8.3). Network-only (no cache): the last snapshot stays in
 * [data] across refreshes so a failed refresh keeps the numbers on screen. [selectedMonth]
 * picks which month's metrics to render; the cross metrics are month-independent.
 */
data class AnalyticsUiState(
    val data: Analytics = Analytics.EMPTY,
    val selectedMonth: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AnalyticsError? = null,
) {
    val hasContent: Boolean get() = data.months.isNotEmpty()
    val effectiveMonth: String? get() = selectedMonth?.takeIf { it in data.months } ?: data.months.firstOrNull()
    val currentMetrics: MonthMetrics? get() = effectiveMonth?.let { data.metrics(it) }
    val isErrorEmpty: Boolean get() = error != null && !hasContent
    val isEmpty: Boolean get() = !loading && error == null && !hasContent
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AnalyticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onPullRefresh() = refresh(pull = true)

    fun retry() = refresh()

    fun onMonthSelected(month: String) = _state.update { it.copy(selectedMonth = month) }

    private fun refresh(pull: Boolean = false) {
        _state.update { it.copy(refreshing = pull, loading = !pull && !it.hasContent, error = null) }
        viewModelScope.launch {
            when (val result = repository.load()) {
                is AnalyticsResult.Success ->
                    _state.update { it.copy(data = result.data, loading = false, refreshing = false, error = null) }
                AnalyticsResult.Forbidden -> fail(AnalyticsError.FORBIDDEN)
                AnalyticsResult.Unavailable -> fail(AnalyticsError.UNAVAILABLE)
                AnalyticsResult.Error -> fail(AnalyticsError.GENERIC)
            }
        }
    }

    private fun fail(error: AnalyticsError) = _state.update {
        it.copy(loading = false, refreshing = false, error = error)
    }
}
