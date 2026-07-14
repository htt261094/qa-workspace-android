package vn.baokim.qa.data.mywork

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Personal work endpoint (E4, #7). Mirrors backend `/api/my-work`, which reuses the
 * existing `/my-work` handler with JSON output (spec §4). Auth is the Bearer session
 * token attached by `AuthInterceptor` (D2 hướng C); the backend scopes the result to the
 * logged-in user's own Jira tasks.
 *
 * Shape is a **flat** task list (verified against the live server): `{ok, stale, tasks}`.
 * The backend does not bucket or sort — the app groups by status (D6, [MyWorkBuckets]).
 * `ignoreUnknownKeys` (NetworkModule) drops the render-only fields the web needs but the
 * app doesn't (hasTc, customs, dueCls, nComments, …).
 */
interface MyWorkApi {

    @GET("api/my-work")
    suspend fun myWork(): MyWorkResponse
}

@Serializable
data class MyWorkResponse(
    val ok: Boolean = false,
    val stale: Boolean = false, // backend served cached/degraded data
    val tasks: List<TaskDto> = emptyList(),
)

@Serializable
data class TaskDto(
    val key: String = "",
    val summary: String = "",
    val jira: String = "",       // raw Jira status: "TO DO" | "In Progress" | "PENDING" | "DONE" | "CANCELLED"
    val due: String? = null,     // ISO yyyy-MM-dd, or null
    val overdue: Boolean = false,
    val jiraUrl: String? = null,
)
