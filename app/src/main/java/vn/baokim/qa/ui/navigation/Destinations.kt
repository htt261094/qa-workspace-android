package vn.baokim.qa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.ui.graphics.vector.ImageVector
import vn.baokim.qa.data.auth.Role

/** Bottom-nav tabs (spec §9.2). Task detail is pushed on top, not a tab. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Dashboard),
    MyWork("my-work", "Việc của tôi", Icons.AutoMirrored.Filled.Assignment),
    Bugs("bugs", "Bugs", Icons.Filled.BugReport);

    /** Role gate (E2.4): Dashboard is admin-only; MyWork + Bugs are for everyone. */
    fun isVisibleTo(role: Role): Boolean = when (this) {
        Dashboard -> role.canSeeDashboard
        MyWork, Bugs -> true
    }
}
