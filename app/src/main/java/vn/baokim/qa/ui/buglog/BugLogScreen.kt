package vn.baokim.qa.ui.buglog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R
import vn.baokim.qa.domain.buglog.Bug
import vn.baokim.qa.domain.buglog.LinkFilter
import vn.baokim.qa.ui.theme.QaWorkspaceTheme

/**
 * Bug Log table (E8.2/E8.4/E8.5/E8.6). Month tabs, tester/dev/link filters, per-row linked
 * Jira-task chips (tap → open detail), a link editor (add/remove by task key) when the
 * backend marks the log editable, and an Excel export that shares the `.xlsx` via a chooser.
 *
 * Stateful entry wires the Hilt ViewModel; short-circuits in inspection mode so previews
 * render sample data without a ViewModelStoreOwner (same pattern as the other screens).
 */
@Composable
fun BugLogScreen(onTaskClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        BugLogContent(SAMPLE, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, modifier)
        return
    }
    val viewModel: BugLogViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Export success → fire a share chooser for the saved .xlsx (E8.5), then clear the trigger.
    val context = LocalContext.current
    val exportUri = state.exportUri
    if (exportUri != null) {
        val chooserTitle = stringResource(R.string.buglog_share_title)
        androidx.compose.runtime.LaunchedEffect(exportUri) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, exportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
            viewModel.onExportConsumed()
        }
    }

    BugLogContent(
        state = state,
        onRefresh = viewModel::onPullRefresh,
        onRetry = viewModel::retry,
        onMonthSelected = viewModel::onMonthSelected,
        onTesterSelected = viewModel::onTesterSelected,
        onDevSelected = viewModel::onDevSelected,
        onLinkFilterSelected = viewModel::onLinkFilterSelected,
        onExport = viewModel::export,
        onLink = viewModel::link,
        onMessageShown = viewModel::onMessageShown,
        onTaskClick = onTaskClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugLogContent(
    state: BugLogUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMonthSelected: (String) -> Unit,
    onTesterSelected: (String) -> Unit,
    onDevSelected: (String) -> Unit,
    onLinkFilterSelected: (LinkFilter) -> Unit,
    onExport: () -> Unit,
    onLink: (bugKey: String, taskKey: String, add: Boolean) -> Unit,
    onMessageShown: () -> Unit,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    state.message?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            onMessageShown()
        }
    }

    // Bug whose link editor is open (E8.4). Kept fresh from state so chips update after a link op.
    var editingKey by remember { mutableStateOf<String?>(null) }
    val editing = editingKey?.let { key -> state.bugs.firstOrNull { it.key == key } }
    if (editing != null) {
        LinkDialog(
            bug = editing,
            onAdd = { task -> onLink(editing.key, task, true) },
            onRemove = { task -> onLink(editing.key, task, false) },
            onDismiss = { editingKey = null },
        )
    }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> CenterBox { CircularProgressIndicator() }
                state.isErrorEmpty -> CenterMessage(
                    text = stringResource(R.string.buglog_error),
                    actionLabel = stringResource(R.string.buglog_retry),
                    onAction = onRetry,
                )
                state.isEmpty -> CenterMessage(text = stringResource(R.string.buglog_empty))
                else -> BugLogBody(
                    state, onMonthSelected, onTesterSelected, onDevSelected,
                    onLinkFilterSelected, onExport, onTaskClick,
                    onEditLink = { editingKey = it },
                )
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BugLogBody(
    state: BugLogUiState,
    onMonthSelected: (String) -> Unit,
    onTesterSelected: (String) -> Unit,
    onDevSelected: (String) -> Unit,
    onLinkFilterSelected: (LinkFilter) -> Unit,
    onExport: () -> Unit,
    onTaskClick: (String) -> Unit,
    onEditLink: (String) -> Unit,
) {
    val months = state.months
    val visible = state.visibleBugs
    Column(Modifier.fillMaxSize()) {
        // header: synced stamp + export
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.buglog_synced, state.syncedAt.ifBlank { "—" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onExport) { Text(stringResource(R.string.buglog_export)) }
        }

        if (state.error) {
            Text(
                text = stringResource(R.string.buglog_stale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (months.size > 1) {
            val selectedIndex = months.indexOf(state.effectiveMonth).coerceAtLeast(0)
            ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 16.dp) {
                months.forEachIndexed { i, m ->
                    Tab(selected = i == selectedIndex, onClick = { onMonthSelected(m) }, text = { Text(m) })
                }
            }
        }

        FilterBar(state, onTesterSelected, onDevSelected, onLinkFilterSelected)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.buglog_count, visible.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.buglog_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(visible.size, key = { visible[it].key }) { i ->
                    BugCard(visible[i], state.editable, onTaskClick, onEditLink)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    state: BugLogUiState,
    onTesterSelected: (String) -> Unit,
    onDevSelected: (String) -> Unit,
    onLinkFilterSelected: (LinkFilter) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterDropdown(
                label = stringResource(R.string.buglog_all_testers),
                selected = state.filter.tester,
                options = state.testers,
                onSelected = onTesterSelected,
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = stringResource(R.string.buglog_all_devs),
                selected = state.filter.dev,
                options = state.devs,
                onSelected = onDevSelected,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkFilterChip(R.string.buglog_link_all, LinkFilter.ALL, state.filter.link, onLinkFilterSelected)
            LinkFilterChip(R.string.buglog_link_linked, LinkFilter.LINKED, state.filter.link, onLinkFilterSelected)
            LinkFilterChip(R.string.buglog_link_unlinked, LinkFilter.UNLINKED, state.filter.link, onLinkFilterSelected)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkFilterChip(labelRes: Int, value: LinkFilter, current: LinkFilter, onSelected: (LinkFilter) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelected(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

/** Dropdown with a blank ("all") default at the top; blank selection clears the filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.ifBlank { label },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSelected(""); expanded = false },
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelected(opt); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BugCard(bug: Bug, editable: Boolean, onTaskClick: (String) -> Unit, onEditLink: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bug.id.ifBlank { bug.key },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    IconButton(onClick = { onEditLink(bug.key) }) {
                        Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.buglog_link_title))
                    }
                }
            }
            Text(
                text = bug.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bug.status.isNotBlank()) {
                    StatusPill(bug.status)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = listOfNotNull(
                        bug.module.ifBlank { null },
                        bug.tester.ifBlank { null }?.let { "QA: $it" },
                        bug.dev.ifBlank { null }?.let { "Dev: $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (bug.tasks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bug.tasks.forEach { task ->
                        AssistChip(
                            onClick = { onTaskClick(task) },
                            label = { Text(task, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// --- link editor (E8.4) ------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LinkDialog(bug: Bug, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.buglog_link_title)) },
        text = {
            Column {
                Text(bug.id.ifBlank { bug.key }, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                if (bug.tasks.isEmpty()) {
                    Text(
                        stringResource(R.string.buglog_link_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        bug.tasks.forEach { task ->
                            InputChip(
                                selected = false,
                                onClick = { onRemove(task) },
                                label = { Text(task) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.buglog_link_remove), modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.buglog_link_hint)) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } },
                        enabled = input.isNotBlank(),
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.buglog_link_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.buglog_close)) } },
    )
}

// --- shared bits -------------------------------------------------------------

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

private val SAMPLE = BugLogUiState(
    loading = false,
    editable = true,
    syncedAt = "2026-07-14 09:30",
    bugs = listOf(
        Bug("r1", "PSIT-BE-101", "Ví không trừ tiền khi thanh toán lỗi", "Ví điện tử", "Major",
            "Open", "PSIT", "2026-07", "Thanh", "Hậu", "2026-07-03", listOf("PSIT1H26-123")),
        Bug("r2", "PSIT-BE-102", "OTP hết hạn không báo lỗi rõ ràng", "Auth", "Minor",
            "Fixed", "PSIT", "2026-07", "Hiền", "Hậu", "2026-07-05", emptyList()),
        Bug("r3", "DA51-FE-9", "Sai định dạng số tiền ở màn lịch sử", "Lịch sử GD", "Trivial",
            "Rejected", "DA51", "2026-06", "Thanh", "Nam", "2026-06-20", emptyList()),
    ),
)

@Preview(name = "Bug Log — content", showBackground = true)
@Composable
private fun BugLogPreview() {
    QaWorkspaceTheme { BugLogContent(SAMPLE, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}) }
}
