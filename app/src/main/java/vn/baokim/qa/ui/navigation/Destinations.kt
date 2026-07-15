package vn.baokim.qa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import vn.baokim.qa.data.auth.Role
import vn.baokim.qa.domain.detail.CustomStatus

/** Non-tab routes pushed on top of the bottom-nav scaffold (settings, detail, …). */
object Routes {
    const val PAT = "settings/pat" // PAT cá nhân (E3, #5)
    const val CREATE_SUBTASK = "create-subtask" // Tạo QA sub-task (E9, #14)
}

/**
 * Task detail route (E5, #10). The issue key is a path arg; the tapped task's current
 * custom-status labels ride along as an optional comma-joined query arg — the detail
 * endpoint doesn't return them, so we seed the overlay from the list we came from (D6/D7:
 * tasks carry `customs`). Values are backend slugs, safe in a URL; encoded anyway.
 */
object DetailArgs {
    const val KEY = "key"
    const val CUSTOMS = "customs"
    const val ROUTE = "task/{$KEY}?$CUSTOMS={$CUSTOMS}"

    /** [customs] = raw backend slug values carried on the tapped task (my-work/dashboard). */
    fun route(key: String, customs: List<String>): String {
        val csv = Uri.encode(customs.joinToString(","))
        return "task/${Uri.encode(key)}?$CUSTOMS=$csv"
    }

    fun parseCustoms(raw: String?): Set<CustomStatus> =
        raw.orEmpty().split(",").mapNotNull { CustomStatus.ofValue(it.trim()) }.toSet()
}

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
