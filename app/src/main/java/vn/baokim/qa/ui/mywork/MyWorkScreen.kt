package vn.baokim.qa.ui.mywork

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import vn.baokim.qa.domain.mywork.MyWorkTask
import vn.baokim.qa.domain.mywork.StatusCategory
import vn.baokim.qa.domain.mywork.TaskBucket
import vn.baokim.qa.ui.theme.QaWorkspaceTheme

/**
 * "Việc của tôi" — the personal task lens (E4, #7). Stateful entry: wires the Hilt
 * ViewModel and hands its state to the stateless [MyWorkContent].
 *
 * Under Compose Preview / layoutlib there's no Activity context, so `hiltViewModel()`
 * throws (it needs a real `ViewModelStoreOwner`). We short-circuit in inspection mode and
 * render sample content instead — this keeps both this screen's preview and the QaApp
 * shell preview (which hosts MyWork as the start destination) rendering.
 */
@Composable
fun MyWorkScreen(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        MyWorkContent(MyWorkUiState(loading = false, buckets = SAMPLE_BUCKETS), {}, {}, modifier)
        return
    }
    val viewModel: MyWorkViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    MyWorkContent(state, viewModel::onPullRefresh, viewModel::retry, modifier)
}

/**
 * Renders the server's buckets in order (D3), each task as a card with a
 * statusCategory-coloured pill. Pull down to refresh (E4.3); shows loading / empty /
 * error states, and keeps cached data visible offline (E4.4) with only a lightweight
 * failure hint on top. Stateless so it can be previewed and tested without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWorkContent(
    state: MyWorkUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
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
                text = stringResource(R.string.mywork_error),
                actionLabel = stringResource(R.string.mywork_retry),
                onAction = onRetry,
            )

            state.isEmpty -> CenterMessage(text = stringResource(R.string.mywork_empty))

            else -> TaskList(state.buckets, staleError = state.error)
        }
    }
}

@Composable
private fun TaskList(buckets: List<TaskBucket>, staleError: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Cache is showing but the last refresh failed — hint without hiding the data.
        if (staleError) {
            item {
                Text(
                    text = stringResource(R.string.mywork_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        buckets.forEach { bucket ->
            item(key = "header-${bucket.key}") {
                Text(
                    text = "${bucket.label} · ${bucket.tasks.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(bucket.tasks, key = { it.key }) { task -> TaskCard(task) }
        }
    }
}

@Composable
private fun TaskCard(task: MyWorkTask) {
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
                    text = task.key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
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

/** Pill colours by statusCategory (spec §2). Tuned to read on both light and dark. */
@Composable
private fun pillColors(category: StatusCategory): Pair<Color, Color> = when (category) {
    StatusCategory.NEW -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
    StatusCategory.INDETERMINATE -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    StatusCategory.DONE -> MaterialTheme.colorScheme.secondaryContainer to
        MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenterMessage(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
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

private val SAMPLE_BUCKETS = listOf(
    TaskBucket(
        key = "doing", label = "Đang làm",
        tasks = listOf(
            MyWorkTask("PSIT1H26-123", "[QA] Test luồng thanh toán ví", "In Progress",
                StatusCategory.INDETERMINATE, "2026-07-18", "thanhht1", "PSIT1H26", null, false),
            MyWorkTask("DA51H26-88", "[QA] Regression đăng nhập", "PENDING",
                StatusCategory.INDETERMINATE, "2026-07-10", "thanhht1", "DA51H26", null, true),
        ),
    ),
    TaskBucket(
        key = "todo", label = "TO DO",
        tasks = listOf(
            MyWorkTask("DA61H26-4", "[QA] Viết test case OTP", "TO DO",
                StatusCategory.NEW, null, "thanhht1", "DA61H26", null, false),
        ),
    ),
)

@Preview(name = "MyWork — list", showBackground = true)
@Composable
private fun MyWorkListPreview() {
    QaWorkspaceTheme { MyWorkContent(MyWorkUiState(loading = false, buckets = SAMPLE_BUCKETS), {}, {}) }
}

@Preview(name = "MyWork — empty", showBackground = true)
@Composable
private fun MyWorkEmptyPreview() {
    QaWorkspaceTheme { MyWorkContent(MyWorkUiState(loading = false), {}, {}) }
}
