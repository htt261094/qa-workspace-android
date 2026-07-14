package vn.baokim.qa.ui.pat

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import vn.baokim.qa.R

/**
 * PAT management screen (E3, #5). Enter a Jira PAT → `/save-pat`, view `/has-pat`
 * status, and remove it via `/delete-pat`. The PAT is masked by default and the raw
 * value never leaves this field except in the request body (OPSEC §7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot feedback: show the server/message text, then clear it so it won't re-fire.
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
                title = { Text(stringResource(R.string.pat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pat_back),
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.pat_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(20.dp))

            StatusRow(state)

            Spacer(Modifier.height(16.dp))

            if (state.statusUnknown) {
                TextButton(onClick = viewModel::refreshStatus, enabled = !state.busy) {
                    Text(stringResource(R.string.pat_retry))
                }
            }

            val fieldLabel = if (state.hasPat) R.string.pat_field_replace_label else R.string.pat_field_label
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(fieldLabel)) },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = if (state.revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    val reveal = state.revealed
                    IconButton(onClick = viewModel::toggleReveal) {
                        Icon(
                            imageVector = if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (reveal) R.string.pat_hide else R.string.pat_show,
                            ),
                        )
                    }
                },
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(if (state.hasPat) R.string.pat_replace else R.string.pat_save))
                }
            }

            if (state.hasPat) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::delete,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.pat_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(state: PatUiState) {
    val (icon: ImageVector?, textRes: Int) = when {
        state.loadingStatus -> null to R.string.pat_status_unknown // spinner shown instead of icon
        state.statusUnknown -> Icons.Filled.ErrorOutline to R.string.pat_status_unknown
        state.hasPat -> Icons.Filled.CheckCircle to R.string.pat_status_saved
        else -> Icons.Filled.ErrorOutline to R.string.pat_status_none
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.loadingStatus) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else if (icon != null) {
            val tint = if (state.hasPat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (!state.loadingStatus) {
            Text(stringResource(textRes), style = MaterialTheme.typography.titleSmall)
        }
    }
}
