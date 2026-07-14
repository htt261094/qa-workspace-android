package vn.baokim.qa.data.dashboard

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Team dashboard endpoint (E6, #9). Mirrors backend `/api/dashboard`
 * (`build_dashboard_payload` + `_get_api_dashboard`, D7). **ADMIN only** — the backend
 * answers 403 for non-admin sessions and 503 while Jira is unreachable; the app also
 * gates the tab so QA/dev never reach here (D5). Auth is the Bearer session token the
 * [AuthInterceptor][vn.baokim.qa.data.net.AuthInterceptor] attaches (D2 hướng C).
 *
 * Shape (D7, verified against a live snapshot — 535 tasks / 5 members / 11 activities):
 *   `{ok, stale, tasks:[…], meta, members:[name…], workload:[…], activities:[…]}`
 *
 * The KPIs are counted server-side in [meta] and the workload [level] is computed
 * server-side (spec §2 thresholds) — the app never re-derives either (D3). It only
 * filters the flat [tasks] list client-side for the task table (E6.3). `ignoreUnknownKeys`
 * (NetworkModule) drops the render-only fields the web needs but the app doesn't
 * (dueDisp/updatedDisp/canCustom/hasTc/customs/created/…).
 */
interface DashboardApi {

    @GET("api/dashboard")
    suspend fun dashboard(): DashboardResponse
}

@Serializable
data class DashboardResponse(
    val ok: Boolean = false,
    val stale: Boolean = false, // backend served cached/degraded data
    val meta: MetaDto = MetaDto(),
    val tasks: List<DashTaskDto> = emptyList(),
    val workload: List<WorkloadDto> = emptyList(),
)

/** KPI counts, pre-computed by the backend (D7). Maps to spec §3.4 KPI cards. */
@Serializable
data class MetaDto(
    val active: Int = 0,        // Active
    val todo: Int = 0,
    val progress: Int = 0,
    val new: Int = 0,
    val stuck: Int = 0,         // Kẹt
    val overdue: Int = 0,       // Quá hạn
    val done: Int = 0,
    val resolvedWeek: Int = 0,  // Ra tuần
    val createdWeek: Int = 0,   // Vào tuần
)

@Serializable
data class DashTaskDto(
    val key: String = "",
    val summary: String = "",
    val jira: String = "",          // raw Jira status
    val active: Boolean = false,
    val overdue: Boolean = false,
    val stuck: Boolean = false,
    val isNew: Boolean = false,
    val due: String? = null,        // ISO yyyy-MM-dd, or null
    val assignee: AssigneeDto? = null,
    val jiraUrl: String? = null,
)

@Serializable
data class AssigneeDto(
    val name: String = "",
    val init: String = "",          // initials for the avatar chip
)

@Serializable
data class WorkloadDto(
    val name: String = "",
    val init: String = "",
    val count: Int = 0,             // active tasks assigned to this person
    val level: String = "",         // "over" | "ok" | "light" — backend-computed (spec §2)
)
