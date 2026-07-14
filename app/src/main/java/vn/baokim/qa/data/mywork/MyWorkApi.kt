package vn.baokim.qa.data.mywork

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Personal work endpoint (E4, #7). Mirrors backend `/api/my-work` (E0.2 `[BE]`), which
 * reuses the existing `/my-work` handler with JSON output instead of HTML (spec §4).
 * Auth is the Bearer session token attached by `AuthInterceptor` (D2 hướng C); the
 * backend scopes the result to the logged-in user's own Jira tasks.
 *
 * The app treats the server as the source of truth (D3): buckets, their order, grouping,
 * and task sort are decided server-side and rendered as-is. `ignoreUnknownKeys` (see
 * NetworkModule) means the backend can add fields without breaking older builds.
 */
interface MyWorkApi {

    @GET("api/my-work")
    suspend fun myWork(): MyWorkResponse
}

@Serializable
data class MyWorkResponse(
    val ok: Boolean = false,
    val buckets: List<TaskBucketDto> = emptyList(),
)

@Serializable
data class TaskBucketDto(
    val key: String = "",
    val label: String = "",
    val tasks: List<TaskDto> = emptyList(),
)

@Serializable
data class TaskDto(
    val key: String = "",
    val summary: String = "",
    val status: String = "",
    val statusCategory: String = "",
    val dueDate: String? = null,
    val assignee: String? = null,
    val project: String? = null,
    val url: String? = null,
    val overdue: Boolean = false,
)
