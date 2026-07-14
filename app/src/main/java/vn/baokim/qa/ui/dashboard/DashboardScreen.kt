package vn.baokim.qa.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R
import vn.baokim.qa.domain.dashboard.Dashboard
import vn.baokim.qa.domain.dashboard.DashboardFilter
import vn.baokim.qa.domain.dashboard.DashboardTask
import vn.baokim.qa.domain.dashboard.Kpi
import vn.baokim.qa.domain.dashboard.WorkloadEntry
import vn.baokim.qa.domain.dashboard.WorkloadLevel
import vn.baokim.qa.domain.mywork.StatusCategory
import vn.baokim.qa.ui.theme.QaWorkspaceTheme

/**
 * Team Dashboard (E6, #9) — admin-only lens over the whole team's Jira tasks. Stateful
 * entry: wires the Hilt ViewModel and hands its state to the stateless [DashboardContent].
 *
 * Like [MyWorkScreen][vn.baokim.qa.ui.mywork.MyWorkScreen] this short-circuits in Compose
 * inspection mode (no ViewModelStoreOwner under layoutlib) so previews render sample data.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        DashboardContent(DashboardUiState(loading = false, data = SAMPLE), {}, {}, {}, modifier)
        return
    }
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    DashboardContent(state, viewModel::onPullRefresh, viewModel::retry, viewModel::onFilterSelected, modifier)
}

/**
 * KPI cards (E6.2) + workload per person with threshold colours (E6.4) + a filterable task
 * table with status pills (E6.3). Pull down to refresh; the last snapshot stays visible on
 * a failed refresh with a stale hint. Stateless so it previews/tests without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilterSelected: (DashboardFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> CenterBox { CircularProgressIndicator() }

            state.isErrorEmpty -> CenterMessage(
                text = stringResource(errorMessage(state.error)),
                actionLabel = stringResource(R.string.dashboard_retry),
                onAction = onRetry,
            )

            state.isEmpty -> CenterMessage(text = stringResource(R.string.dashboard_empty))

            else -> DashboardBody(state, onFilterSelected)
        }
    }
}

@Composable
private fun DashboardBody(state: DashboardUiState, onFilterSelected: (DashboardFilter) -> Unit) {
    val filtered = state.filteredTasks
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.error != null) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item { KpiRow(state.data.kpi) }

        if (state.data.workload.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dashboard_workload_title)) }
            items(state.data.workload, key = { it.name }) { WorkloadRow(it) }
        }

        item { SectionTitle(stringResource(R.string.dashboard_tasks_title)) }
        item { FilterRow(state, onFilterSelected) }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(filtered, key = { it.key }) { TaskCard(it) }
        }
    }
}

// --- KPI cards (E6.2) --------------------------------------------------------

@Composable
private fun KpiRow(kpi: Kpi) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val cards = listOf(
            R.string.dashboard_kpi_active to kpi.active,
            R.string.dashboard_kpi_overdue to kpi.overdue,
            R.string.dashboard_kpi_stuck to kpi.stuck,
            R.string.dashboard_kpi_created_week to kpi.createdWeek,
            R.string.dashboard_kpi_resolved_week to kpi.resolvedWeek,
        )
        items(cards) { (labelRes, value) -> KpiCard(stringResource(labelRes), value) }
    }
}

@Composable
private fun KpiCard(label: String, value: Int) {
    Card(modifier = Modifier.width(104.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- workload (E6.4) ---------------------------------------------------------

@Composable
private fun WorkloadRow(entry: WorkloadEntry) {
    val (container, content) = workloadColors(entry.level)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(entry.initials)
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
                Text(
                    text = stringResource(R.string.dashboard_workload_count, entry.count),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Workload band colours (spec §2): ≥15 over (alarm), 5–14 ok, ≤4 light. */
@Composable
private fun workloadColors(level: WorkloadLevel): Pair<Color, Color> = when (level) {
    WorkloadLevel.OVER -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
    WorkloadLevel.OK -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    WorkloadLevel.LIGHT -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Avatar(initials: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// --- task table (E6.3) -------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(state: DashboardUiState, onFilterSelected: (DashboardFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DashboardFilter.entries) { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text("${filter.label} · ${state.chipCount(filter)}") },
            )
        }
    }
}

@Composable
private fun TaskCard(task: DashboardTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.summary.ifBlank { task.key },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(task.status, task.statusCategory)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.assignee.ifBlank { stringResource(R.string.dashboard_unassigned) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                task.dueDate?.let { due ->
                    Text(
                        text = stringResource(R.string.mywork_due, due),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.overdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (task.overdue) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, category: StatusCategory) {
    val (container, content) = pillColors(category)
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Status pill colours by statusCategory — same mapping as MyWork (spec §2). */
@Composable
private fun pillColors(category: StatusCategory): Pair<Color, Color> = when (category) {
    StatusCategory.NEW -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
    StatusCategory.INDETERMINATE -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    StatusCategory.DONE -> MaterialTheme.colorScheme.secondaryContainer to
        MaterialTheme.colorScheme.onSecondaryContainer
}

// --- shared bits -------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private fun errorMessage(error: DashboardError?): Int = when (error) {
    DashboardError.FORBIDDEN -> R.string.dashboard_error_forbidden
    DashboardError.UNAVAILABLE -> R.string.dashboard_error_unavailable
    else -> R.string.dashboard_error
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenterMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    CenterBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

// --- previews ----------------------------------------------------------------

private val SAMPLE = Dashboard(
    kpi = Kpi(active = 128, overdue = 14, stuck = 6, createdWeek = 23, resolvedWeek = 19),
    workload = listOf(
        WorkloadEntry("Nguyễn Thanh", "NT", 17, WorkloadLevel.OVER),
        WorkloadEntry("Trần Hiền", "TH", 9, WorkloadLevel.OK),
        WorkloadEntry("Mai Hậu", "MH", 3, WorkloadLevel.LIGHT),
    ),
    tasks = listOf(
        DashboardTask("PSIT1H26-123", "[QA] Test luồng thanh toán ví", "In Progress",
            StatusCategory.INDETERMINATE, "Nguyễn Thanh", "NT", "2026-07-18",
            active = true, overdue = false, stuck = false, isNew = false, url = null),
        DashboardTask("DA51H26-88", "[QA] Regression đăng nhập", "PENDING",
            StatusCategory.INDETERMINATE, "Trần Hiền", "TH", "2026-07-10",
            active = true, overdue = true, stuck = true, isNew = false, url = null),
        DashboardTask("DA61H26-4", "[QA] Viết test case OTP", "TO DO",
            StatusCategory.NEW, "", "", null,
            active = true, overdue = false, stuck = false, isNew = true, url = null),
    ),
)

@Preview(name = "Dashboard — content", showBackground = true)
@Composable
private fun DashboardPreview() {
    QaWorkspaceTheme { DashboardContent(DashboardUiState(loading = false, data = SAMPLE), {}, {}, {}) }
}

@Preview(name = "Dashboard — forbidden", showBackground = true)
@Composable
private fun DashboardForbiddenPreview() {
    QaWorkspaceTheme {
        DashboardContent(DashboardUiState(loading = false, error = DashboardError.FORBIDDEN), {}, {}, {})
    }
}
