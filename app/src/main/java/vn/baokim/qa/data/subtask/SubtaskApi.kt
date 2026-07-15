package vn.baokim.qa.data.subtask

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Create-QA-sub-task endpoints (E9, #14). These are the existing root-level handlers in
 * `qa_dashboard.py` (NOT under `/api/`), reused verbatim — the app only speaks JSON. Auth is
 * the Bearer session token attached by [AuthInterceptor][vn.baokim.qa.data.net.AuthInterceptor].
 *
 *  - **`/search-parents`, `/search-people`** — type-ahead reads on the shared read-only PAT;
 *    answer 200 `{ok, results:[…]}` or 400 `{ok:false}` (Jira unreachable). Min 2 chars.
 *  - **`/create-subtasks`** — the write, on the caller's **personal PAT** (reporter = the
 *    logged-in user). Handles 1 or many summaries under one parent (verify parent once). With
 *    no PAT the backend refuses with 400 `{ok:false, code:"no_pat", msg}`. On a real attempt it
 *    returns `{ok, created:[…], failed:[…]}` (ok = at least one created; partial failures land
 *    in `failed`). An early error (bad parent / missing field) is 400 `{ok:false, msg}`.
 *
 * We use `/create-subtasks` for the single case too (like the web) — one code path, and it
 * already reports partial failures cleanly.
 */
interface SubtaskApi {

    /** Type-ahead Task-PTSP parents: `{ok, results:[{key,summary,project}]}`. */
    @GET("search-parents")
    suspend fun searchParents(@Query("q") q: String): Response<SearchParentsResponse>

    /** Type-ahead Jira users (Assignee / Leader): `{ok, results:[{name,display}]}`. */
    @GET("search-people")
    suspend fun searchPeople(@Query("q") q: String): Response<SearchPeopleResponse>

    /** Create sub-task(s) under one parent (personal PAT). `{ok, created, failed}` or `{ok:false,…}`. */
    @POST("create-subtasks")
    suspend fun createSubtasks(@Body body: CreateSubtasksRequest): Response<CreateSubtasksResponse>
}

// --- requests ----------------------------------------------------------------

/**
 * Field names match the backend / web payload exactly: `startDate` is camelCase, `duedate` is
 * lowercase. Empty [assignee]/[leader] mean "not set" (backend treats "" as None).
 */
@Serializable
data class CreateSubtasksRequest(
    val parent: String,
    val summaries: List<String>,
    val startDate: String,
    val duedate: String,
    val assignee: String = "",
    val leader: String = "",
)

// --- responses ---------------------------------------------------------------

@Serializable
data class SearchParentsResponse(
    val ok: Boolean = false,
    val results: List<ParentDto> = emptyList(),
)

@Serializable
data class ParentDto(
    val key: String = "",
    val summary: String = "",
    val project: String = "",
)

@Serializable
data class SearchPeopleResponse(
    val ok: Boolean = false,
    val results: List<PersonDto> = emptyList(),
)

@Serializable
data class PersonDto(
    val name: String = "",
    val display: String = "",
)

/**
 * `created`/`failed` are present on any real attempt (even all-fail); a bare `{ok:false, msg}`
 * (no lists) is an early error, and `code == "no_pat"` flags the missing-PAT case.
 */
@Serializable
data class CreateSubtasksResponse(
    val ok: Boolean = false,
    val created: List<CreatedDto> = emptyList(),
    val failed: List<FailedDto> = emptyList(),
    val msg: String? = null,
    val code: String? = null,
)

@Serializable
data class CreatedDto(
    val key: String = "",
    val summary: String = "",
    val url: String = "",
)

@Serializable
data class FailedDto(
    val summary: String = "",
    val msg: String = "",
)
