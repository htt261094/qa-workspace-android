package vn.baokim.qa.data.activity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Notification bell endpoints (E7, #11). Root-level handlers in `qa_dashboard.py` (NOT under
 * `/api/`), reused verbatim — the app only speaks JSON. Auth is the Bearer session token
 * attached by [AuthInterceptor][vn.baokim.qa.data.net.AuthInterceptor] (D2 hướng C).
 *
 * Contract (read from backend source, D9):
 *  - **GET `/activity-feed`** → 200 `{ok, activities:[…], tasks:{…}}`, or **400 `{ok:false}`**
 *    when Jira is unreachable (best-effort bell). Scope is server-side: admin sees the whole
 *    team, a QA member sees only tasks they watch. The backend already strips the caller's own
 *    events, so every item here is worth surfacing. `tasks` is a real-time patch map the web
 *    uses to live-update chips; the app doesn't need it for notifications and drops it.
 *  - **POST `/dismiss`** `{ids:[…]}` → 200 `{ok:true}` / 400 `{ok:false}` — mark those activity
 *    ids read for this user (cross-device, stored server-side keyed by email).
 */
interface ActivityApi {

    @GET("activity-feed")
    suspend fun activityFeed(): Response<ActivityFeedResponse>

    @POST("dismiss")
    suspend fun dismiss(@Body body: DismissRequest): Response<OkResponse>
}

@Serializable
data class DismissRequest(val ids: List<String>)

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class ActivityFeedResponse(
    val ok: Boolean = false,
    val activities: List<ActivityDto> = emptyList(),
)

/**
 * Union of fields across event kinds (`_compute_activity_feed` + custom-status events).
 * Optional per kind — `ignoreUnknownKeys`/`explicitNulls=false` (NetworkModule) tolerate the
 * variance and the render-only fields the app ignores (`comment_delta`, `by`, …).
 */
@Serializable
data class ActivityDto(
    val id: String = "",
    val kind: String = "",
    val key: String? = null,
    val summary: String = "",
    val author: String = "",
    @SerialName("when") val whenAt: String? = null,
    @SerialName("is_unread") val isUnread: Boolean = false,
    val old: String? = null,
    val new: String? = null,
    val assignee: String? = null,
    val body: String? = null,
    val mention: Boolean = false,
)
