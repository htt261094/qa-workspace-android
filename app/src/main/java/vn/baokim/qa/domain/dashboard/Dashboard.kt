package vn.baokim.qa.domain.dashboard

import vn.baokim.qa.domain.mywork.StatusCategory

/**
 * UI-facing model for the team Dashboard (E6, #9).
 *
 * Backend `/api/dashboard` counts the KPIs and computes the workload level server-side
 * (D7/D3), so this layer carries those through verbatim — no re-derivation. The only
 * client-side work is [DashboardFilter], which slices the flat task list for the table
 * (E6.3); it never re-computes the KPI numbers in [Kpi].
 */
data class Dashboard(
    val kpi: Kpi,
    val workload: List<WorkloadEntry>,
    val tasks: List<DashboardTask>,
) {
    companion object {
        val EMPTY = Dashboard(Kpi(), emptyList(), emptyList())
    }
}

/** KPI counts (spec §3.4), all pre-counted by the backend `meta` (D7). */
data class Kpi(
    val active: Int = 0,
    val overdue: Int = 0,
    val stuck: Int = 0,
    val createdWeek: Int = 0,   // Vào tuần
    val resolvedWeek: Int = 0,  // Ra tuần
)

data class WorkloadEntry(
    val name: String,
    val initials: String,
    val count: Int,             // active tasks assigned to this person
    val level: WorkloadLevel,
)

/**
 * Workload band (spec §2): backend decides which one via the raw `level` string, the app
 * only maps it to a colour. Unknown → [OK] so a new backend label never crashes the pill.
 */
enum class WorkloadLevel {
    OVER,   // ≥15 — quá tải
    OK,     // 5–14
    LIGHT;  // ≤4 — nhẹ

    companion object {
        fun fromRaw(raw: String?): WorkloadLevel = when (raw?.trim()?.lowercase()) {
            "over" -> OVER
            "light" -> LIGHT
            else -> OK
        }
    }
}

data class DashboardTask(
    val key: String,              // Jira issue key
    val summary: String,
    val status: String,           // raw Jira status (backend `jira`)
    val statusCategory: StatusCategory,
    val assignee: String,         // display name; blank when unassigned
    val assigneeInitials: String,
    val dueDate: String?,         // ISO yyyy-MM-dd, or null
    val active: Boolean,
    val overdue: Boolean,
    val stuck: Boolean,
    val isNew: Boolean,
    val url: String?,
    val customs: List<String> = emptyList(), // custom-status slugs, seeds the detail overlay (E5.5)
)

/**
 * Table filters for the dashboard task list (E6.3). Each predicate reads flags the backend
 * already set on the task (`active`/`isNew`/`overdue`/`stuck`) or the derived
 * [StatusCategory] — the app does NOT re-derive the KPI counts, it only picks which rows
 * to show. Chip counts are `count(tasks)`, not the KPI meta numbers.
 */
enum class DashboardFilter(val label: String, val matches: (DashboardTask) -> Boolean) {
    ACTIVE("Active", { it.active }),
    NEW("New", { it.isNew }),
    OVERDUE("Quá hạn", { it.overdue }),
    STUCK("Kẹt", { it.stuck }),
    DONE("Done", { it.statusCategory == StatusCategory.DONE });

    fun apply(tasks: List<DashboardTask>): List<DashboardTask> = tasks.filter(matches)
}
