package vn.baokim.qa.ui.buglog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import vn.baokim.qa.R
import vn.baokim.qa.data.auth.Role

/**
 * Bugs tab host (E8, #13). A two-tab toggle over the Bug Log table and the Analytics screen.
 * Analytics is hidden for DEV (backend answers 403 on `/api/analytics`, mirroring the web —
 * D5/D10); DEV still gets the read-only bug table (E2.4). The parent only owns the toggle;
 * each tab wires its own Hilt ViewModel.
 *
 * [onTaskClick] opens a linked Jira task in the detail screen; the feed carries no
 * custom-status labels so the overlay seeds empty (D8), same as a notification deep-link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugsScreen(
    role: Role,
    onTaskClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val showAnalytics = !role.bugLogReadOnly // everyone except DEV
    if (!showAnalytics) {
        BugLogScreen(onTaskClick = onTaskClick, modifier = modifier)
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.bugs_tab_log)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.bugs_tab_analytics)) })
        }
        when (tab) {
            0 -> BugLogScreen(onTaskClick = onTaskClick)
            else -> AnalyticsScreen()
        }
    }
}
