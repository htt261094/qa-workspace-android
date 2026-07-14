package vn.baokim.qa.ui.mywork

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
import vn.baokim.qa.data.mywork.MyWorkRepository
import vn.baokim.qa.data.mywork.RefreshResult
import vn.baokim.qa.domain.mywork.TaskBucket
import javax.inject.Inject

/**
 * State for the "Việc của tôi" screen (E4, #7). Cache-first: [buckets] comes from Room
 * and shows immediately; a refresh runs on open and on pull-to-refresh (E4.3).
 */
data class MyWorkUiState(
    val buckets: List<TaskBucket> = emptyList(),
    val loading: Boolean = true,     // first load with nothing cached yet → full-screen spinner
    val refreshing: Boolean = false, // pull-to-refresh in flight (cache already on screen)
    val error: Boolean = false,      // last refresh failed
) {
    val hasContent: Boolean get() = buckets.isNotEmpty()
    /** Loaded successfully but the user genuinely has no tasks. */
    val isEmpty: Boolean get() = !loading && !error && buckets.isEmpty()
    /** No cache to fall back on and the refresh failed → full-screen error. */
    val isErrorEmpty: Boolean get() = error && buckets.isEmpty()
}

@HiltViewModel
class MyWorkViewModel @Inject constructor(
    private val repository: MyWorkRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyWorkUiState())
    val state: StateFlow<MyWorkUiState> = _state.asStateFlow()

    init {
        repository.observeBuckets()
            .onEach { buckets -> _state.update { it.copy(buckets = buckets) } }
            .launchIn(viewModelScope)
        refresh()
    }

    /** Pull-to-refresh (F5 = pull tươi). Reuses [refresh] but shows the pull indicator. */
    fun onPullRefresh() = refresh(pull = true)

    fun retry() = refresh()

    private fun refresh(pull: Boolean = false) {
        _state.update { it.copy(refreshing = pull, loading = !pull && !it.hasContent, error = false) }
        viewModelScope.launch {
            val result = repository.refresh()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = result is RefreshResult.Error,
                )
            }
        }
    }
}
