package vn.baokim.qa.ui.buglog

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R
import vn.baokim.qa.domain.buglog.Analytics
import vn.baokim.qa.domain.buglog.BacklogSummary
import vn.baokim.qa.domain.buglog.CrossMetrics
import vn.baokim.qa.domain.buglog.DevBugs
import vn.baokim.qa.domain.buglog.DevReopen
import vn.baokim.qa.domain.buglog.MonthMetrics
import vn.baokim.qa.domain.buglog.ReopenSummary
import vn.baokim.qa.domain.buglog.ValidRate
import vn.baokim.qa.ui.theme.QaWorkspaceTheme
import kotlin.math.roundToInt

/**
 * Analytics screen (E8.3, #13). All numbers are computed by `/api/analytics` (D3/D10) — this
 * only picks a month and renders. Cross metrics are month-independent; Valid Bug Rate, the
 * per-dev bug chart, the reopen table and the T-1 backlog are per selected month. Pull to
 * refresh; the last snapshot stays on a failed refresh. DEV never reaches here (403).
 */
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        AnalyticsContent(AnalyticsUiState(loading = false, data = SAMPLE, selectedMonth = "2026-07"), {}, {}, {}, modifier)
        return
    }
    val viewModel: AnalyticsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    AnalyticsContent(state, viewModel::onPullRefresh, viewModel::retry, viewModel::onMonthSelected, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsContent(
    state: AnalyticsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMonthSelected: (String) -> Unit,
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
                actionLabel = stringResource(R.string.analytics_retry),
                onAction = onRetry,
            )
            state.isEmpty -> CenterMessage(text = stringResource(R.string.analytics_empty))
            else -> AnalyticsBody(state, onMonthSelected)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsBody(state: AnalyticsUiState, onMonthSelected: (String) -> Unit) {
    val months = state.data.months
    val metrics = state.currentMetrics
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.error != null) {
            item {
                Text(
                    stringResource(R.string.analytics_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.analytics_synced, state.data.syncedAt.ifBlank { "—" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { CrossCard(state.data.cross) }

        if (months.isNotEmpty()) {
            val selectedIndex = months.indexOf(state.effectiveMonth).coerceAtLeast(0)
            item {
                ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
                    months.forEachIndexed { i, m ->
                        Tab(selected = i == selectedIndex, onClick = { onMonthSelected(m) }, text = { Text(m) })
                    }
                }
            }
        }

        if (metrics != null) {
            item { ValidCard(metrics.valid, metrics.grand) }
            item { ChartCard(metrics.bugsPerDev) }
            item { ReopenCard(metrics.reopen) }
            item { BacklogCard(metrics.backlog) }
        }
    }
}

// --- cross metrics -----------------------------------------------------------

@Composable
private fun CrossCard(cross: CrossMetrics) {
    MetricCard(stringResource(R.string.analytics_cross_title)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.analytics_coverage), pct(cross.coveragePct))
            Stat(stringResource(R.string.analytics_density), ratio(cross.densityValue))
            Stat(stringResource(R.string.analytics_execution), pct(cross.executionPct))
            Stat(stringResource(R.string.analytics_pass_rate), pct(cross.passRate))
        }
    }
}

// --- valid bug rate ----------------------------------------------------------

@Composable
private fun ValidCard(valid: ValidRate, grand: Int) {
    MetricCard(stringResource(R.string.analytics_valid_title)) {
        Text(
            stringResource(R.string.analytics_bugs_count, grand),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(stringResource(R.string.analytics_valid_rate), pct(valid.validPct))
            Stat(stringResource(R.string.analytics_reject_rate), pct(valid.rejectPct))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.analytics_valid_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- per-dev bug chart (Compose-drawn bars, E8.3) ----------------------------

@Composable
private fun ChartCard(bugsPerDev: List<DevBugs>) {
    MetricCard(stringResource(R.string.analytics_chart_title)) {
        if (bugsPerDev.isEmpty()) {
            Text(
                stringResource(R.string.analytics_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricCard
        }
        val max = bugsPerDev.maxOf { it.count }.coerceAtLeast(1.0)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bugsPerDev.forEach { dev -> BarRow(dev, max) }
        }
    }
}

@Composable
private fun BarRow(dev: DevBugs, max: Double) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dev.name.ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count(dev.count), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((dev.count / max).toFloat().coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// --- reopen table ------------------------------------------------------------

@Composable
private fun ReopenCard(reopen: ReopenSummary) {
    MetricCard(stringResource(R.string.analytics_reopen_title)) {
        if (reopen.devs.isEmpty()) {
            Text(
                stringResource(R.string.analytics_reopen_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MetricCard
        }
        Row(Modifier.fillMaxWidth()) {
            HeaderCell(stringResource(R.string.analytics_reopen_col_dev), Modifier.weight(1f))
            HeaderCell(stringResource(R.string.analytics_reopen_col_reopen), Modifier.width(72.dp))
            HeaderCell(stringResource(R.string.analytics_reopen_col_fix), Modifier.width(72.dp))
        }
        reopen.devs.forEach { dev -> ReopenRow(dev) }
    }
}

@Composable
private fun ReopenRow(dev: DevReopen) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            dev.name.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(count(dev.reopened), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Text(count(dev.fixes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
    }
}

// --- backlog T-1 -------------------------------------------------------------

@Composable
private fun BacklogCard(backlog: BacklogSummary) {
    MetricCard(stringResource(R.string.analytics_backlog_title)) {
        if (!backlog.hasSnapshot) {
            Text(
                stringResource(R.string.analytics_backlog_no_snapshot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(stringResource(R.string.analytics_backlog_resolved), backlog.resolved.toString())
            Stat(stringResource(R.string.analytics_backlog_still_open), backlog.stillOpen.toString())
            Stat(stringResource(R.string.analytics_backlog_new), backlog.newCount.toString())
        }
    }
}

// --- shared bits -------------------------------------------------------------

@Composable
private fun MetricCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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

private fun errorMessage(error: AnalyticsError?): Int = when (error) {
    AnalyticsError.FORBIDDEN -> R.string.analytics_error_forbidden
    AnalyticsError.UNAVAILABLE -> R.string.analytics_error_unavailable
    else -> R.string.analytics_error
}

/** Percent (already 0–100 from backend) → "45.2%"; null → "—". */
private fun pct(v: Double?): String = if (v == null) "—" else "${(v * 10).roundToInt() / 10.0}%"

/** Ratio like bug/task → "1.3"; null → "—". */
private fun ratio(v: Double?): String = if (v == null) "—" else "${(v * 10).roundToInt() / 10.0}"

/** Count that may be fractional (multi-dev bug split 1/n) → integer when whole, else 1dp. */
private fun count(v: Double): String {
    val rounded = (v * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

// --- preview -----------------------------------------------------------------

private val SAMPLE = Analytics(
    syncedAt = "2026-07-14 09:30",
    months = listOf("2026-07", "2026-06"),
    cross = CrossMetrics(
        coveragePct = 62.5, coverageTasksWithTc = 25, coverageTotalTasks = 40,
        densityValue = 1.3, densityTotalBugs = 52,
        executionPct = 78.0, passRate = 88.5, execPass = 200, execFail = 26, execTotal = 290,
    ),
    byMonth = mapOf(
        "2026-07" to MonthMetrics(
            grand = 34,
            valid = ValidRate(34, 4, 22, validPct = 73.3, rejectPct = 11.8),
            reopen = ReopenSummary(34, 6, listOf(
                DevReopen("Hậu", 3.0, 5.0, 12.0),
                DevReopen("Nam", 2.0, 3.0, 8.0),
            )),
            backlog = BacklogSummary("2026-06", total = 18, resolved = 12, stillOpen = 6, newCount = 34, hasSnapshot = true),
            bugsPerDev = listOf(DevBugs("Hậu", 12.0), DevBugs("Nam", 8.0), DevBugs("Long", 5.5)),
        ),
    ),
)

@Preview(name = "Analytics — content", showBackground = true)
@Composable
private fun AnalyticsPreview() {
    QaWorkspaceTheme {
        AnalyticsContent(AnalyticsUiState(loading = false, data = SAMPLE, selectedMonth = "2026-07"), {}, {}, {})
    }
}

@Preview(name = "Analytics — forbidden", showBackground = true)
@Composable
private fun AnalyticsForbiddenPreview() {
    QaWorkspaceTheme {
        AnalyticsContent(AnalyticsUiState(loading = false, error = AnalyticsError.FORBIDDEN), {}, {}, {})
    }
}
