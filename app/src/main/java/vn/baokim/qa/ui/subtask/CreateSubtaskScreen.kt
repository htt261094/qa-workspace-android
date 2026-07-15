package vn.baokim.qa.ui.subtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R
import vn.baokim.qa.domain.subtask.ParentTask
import vn.baokim.qa.domain.subtask.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Create QA sub-task screen (E9, #14). Pick a Task-PTSP parent (`/search-parents`), auto-fill
 * "[QA] " into the summary, set dates + optional assignee + leader (default Hiền), then create
 * one or many sub-tasks (`/create-subtasks`, one per non-blank summary line) on the personal PAT.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSubtaskScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateSubtaskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subtask_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.subtask_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Parent — Task-PTSP (required)
            SearchPickerField(
                label = stringResource(R.string.subtask_parent_label),
                placeholder = stringResource(R.string.subtask_parent_hint),
                picker = state.parent,
                selectedLabel = { "${it.key} · ${it.summary}" },
                itemPrimary = { it.key },
                itemSecondary = { it.summary },
                onQuery = viewModel::onParentQuery,
                onPick = viewModel::pickParent,
                onClear = viewModel::clearParent,
            )

            Spacer(Modifier.height(16.dp))

            // Summaries — one line = one sub-task
            OutlinedTextField(
                value = state.summaries,
                onValueChange = viewModel::onSummariesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.subtask_summary_label)) },
                placeholder = { Text(stringResource(R.string.subtask_summary_hint)) },
                minLines = 4,
            )
            if (state.lineCount > 1) {
                Text(
                    text = stringResource(R.string.subtask_summary_count, state.lineCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Dates
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = stringResource(R.string.subtask_start_label),
                    value = state.startDate,
                    onPick = viewModel::onStartDate,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = stringResource(R.string.subtask_due_label),
                    value = state.dueDate,
                    onPick = viewModel::onDueDate,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Assignee (optional)
            SearchPickerField(
                label = stringResource(R.string.subtask_assignee_label),
                placeholder = stringResource(R.string.subtask_person_hint),
                picker = state.assignee,
                selectedLabel = { it.display },
                itemPrimary = { it.display },
                itemSecondary = { it.name },
                onQuery = viewModel::onAssigneeQuery,
                onPick = viewModel::pickAssignee,
                onClear = viewModel::clearAssignee,
            )

            Spacer(Modifier.height(16.dp))

            // Leader (default Hiền)
            SearchPickerField(
                label = stringResource(R.string.subtask_leader_label),
                placeholder = stringResource(R.string.subtask_person_hint),
                picker = state.leader,
                selectedLabel = { it.display },
                itemPrimary = { it.display },
                itemSecondary = { it.name },
                onQuery = viewModel::onLeaderQuery,
                onPick = viewModel::pickLeader,
                onClear = viewModel::clearLeader,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (state.lineCount > 1) {
                            stringResource(R.string.subtask_create_many, state.lineCount)
                        } else {
                            stringResource(R.string.subtask_create_one)
                        },
                    )
                }
            }

            if (state.created.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                CreatedPanel(state)
            }
        }
    }
}

/** Success panel listing the sub-tasks created by the last submit. */
@Composable
private fun CreatedPanel(state: CreateSubtaskUiState) {
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.subtask_created_title, state.created.size),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(8.dp))
    state.created.forEach { c ->
        Text(
            text = "• ${c.key} — ${c.summary}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/**
 * Generic type-ahead picker: shows the selected item as a removable chip, otherwise a text
 * field whose (debounced) results render inline below it. Inline (not a floating dropdown) so
 * it plays nicely inside the screen's vertical scroll.
 */
@Composable
private fun <T> SearchPickerField(
    label: String,
    placeholder: String,
    picker: PickerState<T>,
    selectedLabel: (T) -> String,
    itemPrimary: (T) -> String,
    itemSecondary: (T) -> String?,
    onQuery: (String) -> Unit,
    onPick: (T) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
        val selected = picker.selected
        if (selected != null) {
            InputChip(
                selected = true,
                onClick = onClear,
                label = { Text(selectedLabel(selected), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.subtask_clear),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        } else {
            OutlinedTextField(
                value = picker.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                trailingIcon = if (picker.searching) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
            )
            if (picker.results.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column {
                        picker.results.forEach { item ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(itemPrimary(item), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                itemSecondary(item)?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Read-only field that opens a [DatePickerDialog]; emits the pick as "yyyy-MM-dd". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        modifier = modifier.clickable { show = true },
        label = { Text(label) },
        readOnly = true,
        enabled = false, // disabled so the whole field is a click target; colors overridden below
        singleLine = true,
        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
    if (show) {
        val initialMillis = value.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    show = false
                }) { Text(stringResource(R.string.subtask_save)) }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.subtask_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
