package vn.baokim.qa.data.buglog

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Bug Log + analytics endpoints (E8, #13). Backend contract D10:
 *
 *  - **GET `/api/bug-log`** → `{ok, editable, bugs:[…], months:[…], syncedAt, …}`. Open to every
 *    authed role; `editable` is `false` for DEV (backend enforces the write ban anyway).
 *  - **GET `/api/analytics`** → `{ok, syncedAt, months, crossMetrics, metrics:{ym:{…}}}`. DEV gets
 *    403 (mirrors the web), the Bugs screen hides the tab for dev.
 *  - **POST `/link-task`** `{keys, task, op}` → `{ok, links}`. Add/remove a bug↔Jira-task link
 *    (E8.4). App-side only (no Jira write) — open to non-dev.
 *  - **POST `/export-bug-log`** `{rows, filename}` → an `.xlsx` binary (E8.5). Any authed role.
 *
 * Auth is the Bearer session token attached by
 * [AuthInterceptor][vn.baokim.qa.data.net.AuthInterceptor] (D2 hướng C). `ignoreUnknownKeys`
 * (NetworkModule) drops the fields the web needs but the app doesn't (`sources`, `reopen`
 * on bug-log, per-bug `detail`/`bugs` lists on analytics).
 */
interface BugLogApi {

    @GET("api/bug-log")
    suspend fun bugLog(): BugLogResponse

    @GET("api/analytics")
    suspend fun analytics(): AnalyticsResponse

    /** Add/remove one bug↔task link. 200 `{ok, links}` / 400 `{ok:false}`. */
    @POST("link-task")
    suspend fun linkTask(@Body body: LinkTaskRequest): Response<LinkTaskResponse>

    /** Exports the displayed rows to `.xlsx`; streamed so the whole file isn't buffered twice. */
    @POST("export-bug-log")
    @Streaming
    suspend fun exportBugLog(@Body body: ExportRequest): Response<ResponseBody>
}

// --- /api/bug-log ------------------------------------------------------------

@Serializable
data class BugLogResponse(
    val ok: Boolean = false,
    val editable: Boolean = false,
    val bugs: List<BugDto> = emptyList(),
    val months: List<String> = emptyList(),
    val syncedAt: String = "",
)

@Serializable
data class BugDto(
    val key: String = "",
    val id: String = "",
    val summary: String = "",
    val module: String = "",
    val severity: String = "",
    val status: String = "",
    val project: String = "",
    val month: String = "",
    val qa: String = "",      // tester
    val dev: String = "",
    val created: String = "",
    val tasks: List<String> = emptyList(),
)

// --- /api/analytics ----------------------------------------------------------

@Serializable
data class AnalyticsResponse(
    val ok: Boolean = false,
    val syncedAt: String = "",
    val months: List<String> = emptyList(),
    val crossMetrics: CrossMetricsDto = CrossMetricsDto(),
    val metrics: Map<String, MonthMetricsDto> = emptyMap(),
)

@Serializable
data class CrossMetricsDto(
    val coverage: CoverageDto = CoverageDto(),
    val density: DensityDto = DensityDto(),
    val execution: ExecutionDto = ExecutionDto(),
)

@Serializable
data class CoverageDto(val tasksWithTc: Int = 0, val totalTasks: Int = 0, val pct: Double? = null)

@Serializable
data class DensityDto(val totalBugs: Int = 0, val totalTasks: Int = 0, val value: Double? = null)

@Serializable
data class ExecutionDto(
    val total: Int = 0,
    val executed: Int = 0,
    val norun: Int = 0,
    val pass: Int = 0,
    val fail: Int = 0,
    val execPct: Double? = null,
    val passRate: Double? = null,
)

@Serializable
data class MonthMetricsDto(
    val grand: Int = 0,
    val chart: Map<String, Map<String, Double>> = emptyMap(), // dev → {project → count}
    val valid: ValidDto = ValidDto(),
    val reopen: ReopenDto = ReopenDto(),
    val backlog: BacklogDto = BacklogDto(),
)

@Serializable
data class ValidDto(
    val total: Int = 0,
    val reject: Int = 0,
    val closed: Int = 0,
    val validPct: Double? = null,
    val rejectPct: Double? = null,
)

@Serializable
data class ReopenDto(
    val totalBugs: Int = 0,
    val distinctTotal: Int = 0,
    val devs: Map<String, DevReopenDto> = emptyMap(),
)

@Serializable
data class DevReopenDto(val nb: Double = 0.0, val fx: Double = 0.0, val denom: Double = 0.0)

@Serializable
data class BacklogDto(
    val prev: String = "",
    val total: Int = 0,
    val resolved: Int = 0,
    val stillOpen: Int = 0,
    val newCount: Int = 0,
    val hasSnapshot: Boolean = false,
)

// --- /link-task --------------------------------------------------------------

/** `task` = one Jira key; `op` ∈ add/remove/clear (D8/#55 contract). */
@Serializable
data class LinkTaskRequest(
    val keys: List<String>,
    val task: String,
    val op: String,
)

@Serializable
data class LinkTaskResponse(val ok: Boolean = false)

// --- /export-bug-log ---------------------------------------------------------

/** `rows` = 7 columns per row (ID/Module/Mô tả/Ngày/Trạng thái/Tester/Dev), server-fixed header. */
@Serializable
data class ExportRequest(
    val rows: List<List<String>>,
    val filename: String,
)
