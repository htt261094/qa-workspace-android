package vn.baokim.qa.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R
import vn.baokim.qa.domain.detail.Comment
import vn.baokim.qa.domain.detail.CustomStatus
import vn.baokim.qa.domain.detail.LinkedBug
import vn.baokim.qa.domain.detail.LinkedTestcase
import vn.baokim.qa.domain.detail.TaskDetail
import vn.baokim.qa.domain.detail.Transition
import vn.baokim.qa.domain.mywork.StatusCategory
import vn.baokim.qa.ui.theme.QaWorkspaceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Callbacks the stateless [TaskDetailContent] fires back into the ViewModel. */
class TaskDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onCommentChange: (String) -> Unit,
    val onSendComment: () -> Unit,
    val onOpenStatus: () -> Unit,
    val onApplyTransition: (Transition) -> Unit,
    val onSetDueDate: (String) -> Unit,
    val onToggleCustom: (CustomStatus) -> Unit,
) {
    companion object {
        val NONE = TaskDetailActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Task detail screen (E5, #10): description + comment history (E5.1), post a comment (E5.2),
 * change status via workflow transitions (E5.3), edit/clear the due date behind the perm gate
 * (E5.4), and toggle the internal custom-status overlay (E5.5). Stateful entry wires the Hilt
 * ViewModel; the UI itself is the stateless [TaskDetailContent].
 */
@Composable
fun TaskDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TaskDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    TaskDetailContent(
        state = state,
        actions = TaskDetailActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onCommentChange = viewModel::onCommentChange,
            onSendComment = viewModel::sendComment,
            onOpenStatus = viewModel::loadTransitions,
            onApplyTransition = viewModel::applyTransition,
            onSetDueDate = viewModel::setDueDate,
            onToggleCustom = viewModel::toggleCustom,
        ),
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailContent(
    state: TaskDetailUiState,
    actions: TaskDetailActions,
    onConsumeMessage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.key ?: state.key) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.detail == null -> ErrorMessage(actions.onRetry, Modifier.align(Alignment.Center))
                else -> DetailBody(state, state.detail, actions)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailBody(state: TaskDetailUiState, detail: TaskDetail, actions: TaskDetailActions) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = detail.summary.ifBlank { detail.key },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Status + change-status trigger (E5.3)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(detail.status, StatusCategory.fromStatus(detail.status))
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { showStatusDialog = true; actions.onOpenStatus() },
                    enabled = !state.statusBusy,
                ) { Text(stringResource(R.string.detail_change_status)) }
            }
        }

        item { MetaRow(detail, state, onEditDue = { showDatePicker = true }) }

        // Custom-status overlay (E5.5)
        item { CustomStatusSection(state, actions.onToggleCustom) }

        if (detail.description.isNotBlank()) {
            item { SectionTitle(stringResource(R.string.detail_description)) }
            item { Text(detail.description, style = MaterialTheme.typography.bodyMedium) }
        }

        if (detail.bugs.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.detail_bugs, detail.bugs.size)) }
            items(detail.bugs, key = { it.id }) { BugRow(it) }
        }

        if (detail.testcases.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.detail_testcases, detail.testcases.size)) }
            items(detail.testcases, key = { it.id }) { TestcaseRow(it) }
        }

        // Comments (E5.1 history + E5.2 add)
        item { SectionTitle(stringResource(R.string.detail_comments, detail.comments.size)) }
        item { CommentComposer(state, actions.onCommentChange, actions.onSendComment) }
        if (detail.comments.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.detail_no_comments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(detail.comments) { CommentRow(it) }
        }
    }

    if (showStatusDialog) {
        StatusDialog(
            state = state,
            onDismiss = { showStatusDialog = false },
            onPick = { showStatusDialog = false; actions.onApplyTransition(it) },
        )
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initial = detail.dueDate,
            onDismiss = { showDatePicker = false },
            onPick = { showDatePicker = false; actions.onSetDueDate(it) },
            onClear = { showDatePicker = false; actions.onSetDueDate("") },
        )
    }
}

// --- meta (assignee, devs, due date) -----------------------------------------

@Composable
private fun MetaRow(detail: TaskDetail, state: TaskDetailUiState, onEditDue: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            MetaLine(stringResource(R.string.detail_assignee), detail.assignee.ifBlank {
                stringResource(R.string.dashboard_unassigned)
            })
            if (detail.devs.isNotEmpty()) {
                MetaLine(stringResource(R.string.detail_devs), detail.devs.joinToString(", "))
            }
            detail.updated?.let { MetaLine(stringResource(R.string.detail_updated), it) }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_due) + ": " +
                        (detail.dueDate ?: stringResource(R.string.detail_no_due)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.dueBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (state.canEditDue) {
                    TextButton(onClick = onEditDue) { Text(stringResource(R.string.detail_edit_due)) }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- custom status overlay (E5.5) --------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomStatusSection(state: TaskDetailUiState, onToggle: (CustomStatus) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.detail_custom_title))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomStatus.entries.forEach { cs ->
                FilterChip(
                    selected = cs in state.customs,
                    onClick = { onToggle(cs) },
                    enabled = !state.customBusy,
                    label = { Text(cs.label) },
                )
            }
        }
    }
}

// --- status transition dialog (E5.3) -----------------------------------------

@Composable
private fun StatusDialog(
    state: TaskDetailUiState,
    onDismiss: () -> Unit,
    onPick: (Transition) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) } },
        title = { Text(stringResource(R.string.detail_change_status)) },
        text = {
            when {
                state.transitionsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_loading))
                }
                state.transitionsError != null -> Text(state.transitionsError)
                state.transitions.isEmpty() -> Text(stringResource(R.string.detail_no_transitions))
                else -> Column {
                    state.transitions.forEach { t ->
                        TextButton(
                            onClick = { onPick(t) },
                            enabled = !state.statusBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(t.to, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
    )
}

// --- due date picker (E5.4) --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val initialMillis = initial?.let {
        runCatching {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.toString())
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(R.string.detail_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text(stringResource(R.string.detail_clear_due)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

// --- comments (E5.1 / E5.2) --------------------------------------------------

@Composable
private fun CommentComposer(
    state: TaskDetailUiState,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = state.commentDraft,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.detail_comment_hint)) },
            enabled = !state.commentSending,
            minLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onSend,
            enabled = state.canSendComment,
            modifier = Modifier.align(Alignment.End),
        ) {
            if (state.commentSending) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.detail_send_comment))
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                comment.whenIso?.let {
                    Text(
                        text = it.take(10),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- linked bugs / testcases -------------------------------------------------

@Composable
private fun BugRow(bug: LinkedBug) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(bug.summary.ifBlank { bug.id }, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelChip(bug.id)
                if (bug.severity.isNotBlank()) LabelChip(bug.severity)
                if (bug.status.isNotBlank()) LabelChip(bug.status)
            }
        }
    }
}

@Composable
private fun TestcaseRow(tc: LinkedTestcase) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tc.name.ifBlank { tc.id }, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.detail_tc_counts, tc.pass, tc.fail, tc.count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// --- shared bits -------------------------------------------------------------

@Composable
private fun StatusPill(status: String, category: StatusCategory) {
    val (container, content) = when (category) {
        StatusCategory.NEW -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        StatusCategory.INDETERMINATE -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        StatusCategory.DONE -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = status.ifBlank { "—" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ErrorMessage(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.detail_error), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.detail_retry)) }
    }
}

// --- previews ----------------------------------------------------------------

private val SAMPLE_DETAIL = TaskDetail(
    key = "PSIT1H26-123",
    summary = "[QA] Test luồng thanh toán ví",
    description = "Kiểm thử end-to-end luồng nạp/rút ví, gồm cả case timeout cổng thanh toán.",
    status = "In Progress",
    assignee = "Nguyễn Thanh",
    dueDate = "2026-07-18",
    updated = "2026-07-14",
    created = "2026-07-01",
    devs = listOf("Trần Dev"),
    comments = listOf(
        Comment("Nguyễn Thanh", "2026-07-14T09:12:00.000+0700", "Đã test xong case nạp, còn case rút."),
        Comment("Trần Hiền", "2026-07-13T16:40:00.000+0700", "Nhớ cover case timeout."),
    ),
    bugs = listOf(LinkedBug("PAY-12", "Sai số dư sau khi rút", "Major", "Open", "Ví")),
    testcases = listOf(LinkedTestcase("f1", "Bộ TC thanh toán ví", 20, 3, 24)),
)

@Preview(name = "Detail — content", showBackground = true)
@Composable
private fun TaskDetailPreview() {
    QaWorkspaceTheme {
        TaskDetailContent(
            state = TaskDetailUiState(
                key = "PSIT1H26-123",
                loading = false,
                detail = SAMPLE_DETAIL,
                canEditDue = true,
                customs = setOf(CustomStatus.DEV_FIXING, CustomStatus.WAIT_DATA),
            ),
            actions = TaskDetailActions.NONE,
        )
    }
}
