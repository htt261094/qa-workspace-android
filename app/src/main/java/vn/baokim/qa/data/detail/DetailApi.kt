package vn.baokim.qa.data.detail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Task detail + write endpoints (E5, #10). These are the existing root-level handlers in
 * `qa_dashboard.py` (NOT under `/api/`), reused verbatim — the app only speaks JSON to them.
 * Auth is the Bearer session token attached by
 * [AuthInterceptor][vn.baokim.qa.data.net.AuthInterceptor] (D2 hướng C).
 *
 * Two auth layers matter here (spec §5):
 *  - **Reads** (`/issue-comments`) use the server's shared read-only PAT — no personal PAT
 *    needed.
 *  - **Writes** to Jira (`/do-transition`, `/add-comment`, `/set-duedate`) go through the
 *    caller's **personal PAT** for correct attribution; with no PAT the backend refuses with
 *    400 `{ok:false, code:"no_pat", msg}`. The custom-status overlay is internal (KV, not
 *    Jira) so it needs no PAT.
 *
 * Write endpoints answer 200 on success and **400 on any failure** (`_reply_json`), so those
 * take a raw [Response] — the repository reads the error body to surface the server's
 * Vietnamese `msg`/`code` (same pattern as PatApi.savePat).
 */
interface DetailApi {

    /** Full detail of one issue: `{ok, detail:{…, comments, bugs, testcases}}` (200) or 400. */
    @GET("issue-comments")
    suspend fun issueComments(@Query("key") key: String): Response<IssueCommentsResponse>

    /** Workflow transitions available now: `{ok, transitions:[{id,to}]}` or `{ok:false,msg}`. */
    @POST("jira-transitions")
    suspend fun transitions(@Body body: KeyRequest): Response<TransitionsResponse>

    /** Perform a transition (personal PAT). `{ok, msg}`. */
    @POST("do-transition")
    suspend fun doTransition(@Body body: TransitionRequest): Response<MsgResponse>

    /** Post a comment (personal PAT). `{ok, msg}`. */
    @POST("add-comment")
    suspend fun addComment(@Body body: CommentRequest): Response<MsgResponse>

    /** UI gate: may this user edit the due date on Jira? `{ok, canEdit}` or `{ok:false,msg}`. */
    @POST("duedate-perm")
    suspend fun dueDatePerm(@Body body: KeyRequest): Response<DueDatePermResponse>

    /** Set/clear the due date (personal PAT). Empty `duedate` clears it. `{ok, msg}`. */
    @POST("set-duedate")
    suspend fun setDueDate(@Body body: DueDateRequest): Response<MsgResponse>

    /** Toggle one internal custom-status label (no PAT). Returns the new full list `{ok, values}`. */
    @POST("set-custom-status")
    suspend fun setCustomStatus(@Body body: CustomStatusRequest): Response<CustomStatusResponse>
}

// --- requests ----------------------------------------------------------------

@Serializable
data class KeyRequest(val key: String)

@Serializable
data class TransitionRequest(val key: String, val id: String)

@Serializable
data class CommentRequest(val key: String, val body: String)

/** `duedate` = "yyyy-MM-dd", or "" to clear (backend field name is `duedate`). */
@Serializable
data class DueDateRequest(val key: String, val duedate: String)

/** `status` = a custom-status value ("" removes all); `summary` labels the activity entry. */
@Serializable
data class CustomStatusRequest(val key: String, val status: String, val summary: String = "")

// --- responses ---------------------------------------------------------------

@Serializable
data class IssueCommentsResponse(
    val ok: Boolean = false,
    val detail: DetailDto? = null,
    val msg: String? = null,
)

@Serializable
data class DetailDto(
    val key: String = "",
    val summary: String = "",
    val description: String = "",
    val status: String = "",
    val assignee: String = "",
    val duedate: String? = null,
    val updated: String? = null,
    val created: String? = null,
    val devs: List<String> = emptyList(),
    val comments: List<CommentDto> = emptyList(),
    val bugs: List<BugDto> = emptyList(),
    val testcases: List<TestcaseDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val author: String = "",
    @SerialName("when") val whenAt: String? = null,
    val body: String = "",
)

@Serializable
data class BugDto(
    val id: String = "",
    val summary: String = "",
    val severity: String = "",
    val status: String = "",
    val module: String = "",
)

@Serializable
data class TestcaseDto(
    val id: String = "",
    val name: String = "",
    val count: Int = 0,
    val pass: Int = 0,
    val fail: Int = 0,
)

@Serializable
data class TransitionsResponse(
    val ok: Boolean = false,
    val transitions: List<TransitionDto> = emptyList(),
    val msg: String? = null,
)

@Serializable
data class TransitionDto(val id: String = "", val to: String = "")

/** Generic write result. `code` carries "no_pat" when the user hasn't configured a PAT. */
@Serializable
data class MsgResponse(
    val ok: Boolean = false,
    val msg: String? = null,
    val code: String? = null,
)

@Serializable
data class DueDatePermResponse(
    val ok: Boolean = false,
    val canEdit: Boolean = false,
    val msg: String? = null,
)

@Serializable
data class CustomStatusResponse(
    val ok: Boolean = false,
    val values: List<String> = emptyList(),
)
