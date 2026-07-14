package vn.baokim.qa.data.detail

import kotlinx.serialization.json.Json
import retrofit2.Response
import vn.baokim.qa.domain.detail.Comment
import vn.baokim.qa.domain.detail.CustomStatus
import vn.baokim.qa.domain.detail.LinkedBug
import vn.baokim.qa.domain.detail.LinkedTestcase
import vn.baokim.qa.domain.detail.TaskDetail
import vn.baokim.qa.domain.detail.Transition
import javax.inject.Inject
import javax.inject.Singleton

/** Loading one task's detail (E5.1). Network-only — no cache; detail must be fresh. */
sealed interface DetailResult {
    data class Success(val detail: TaskDetail) : DetailResult
    data object Error : DetailResult
}

/** Available workflow transitions (E5.3). */
sealed interface TransitionsResult {
    data class Success(val transitions: List<Transition>) : TransitionsResult
    /** [noPat] true → user must configure a PAT first; otherwise a generic/[message] failure. */
    data class Failure(val message: String?, val noPat: Boolean = false) : TransitionsResult
}

/** A Jira write (transition / comment / due date, E5.2–E5.4) that needs the personal PAT. */
sealed interface WriteResult {
    data class Success(val message: String?) : WriteResult
    data class Failure(val message: String?, val noPat: Boolean = false) : WriteResult
}

/** Due-date edit permission gate (E5.4). */
sealed interface DueDatePermResult {
    data class Success(val canEdit: Boolean) : DueDatePermResult
    data object Error : DueDatePermResult
}

/** Custom-status overlay toggle (E5.5) — returns the task's new full label set. */
sealed interface CustomStatusResult {
    data class Success(val values: List<CustomStatus>) : CustomStatusResult
    data object Error : CustomStatusResult
}

/**
 * Single point for the task detail screen (E5, #10) over [DetailApi]. Every call is guarded
 * so transport failures become typed results instead of exceptions in the UI. No caching:
 * detail is always fetched live and reflects the writes the user just made.
 *
 * Write endpoints answer 400 on failure with a body, so we read body-or-errorBody and pass
 * the server's Vietnamese `msg` through (and flag `code=="no_pat"` so the UI can point the
 * user at the PAT screen). Nothing sensitive is stored or logged here (OPSEC §7).
 */
@Singleton
class DetailRepository @Inject constructor(
    private val api: DetailApi,
    private val json: Json,
) {

    suspend fun load(key: String): DetailResult = try {
        val body = api.issueComments(key).bodyOrError<IssueCommentsResponse>()
        val detail = body?.detail
        if (body?.ok == true && detail != null) DetailResult.Success(detail.toDomain())
        else DetailResult.Error
    } catch (e: Exception) {
        DetailResult.Error
    }

    suspend fun transitions(key: String): TransitionsResult = try {
        val body = api.transitions(KeyRequest(key)).bodyOrError<TransitionsResponse>()
        if (body?.ok == true) {
            TransitionsResult.Success(body.transitions.map { Transition(it.id, it.to) })
        } else {
            TransitionsResult.Failure(body?.msg)
        }
    } catch (e: Exception) {
        TransitionsResult.Failure(null)
    }

    suspend fun doTransition(key: String, transitionId: String): WriteResult =
        write { api.doTransition(TransitionRequest(key, transitionId)) }

    suspend fun addComment(key: String, body: String): WriteResult =
        write { api.addComment(CommentRequest(key, body)) }

    /** [date] = "yyyy-MM-dd", or "" to clear the due date. */
    suspend fun setDueDate(key: String, date: String): WriteResult =
        write { api.setDueDate(DueDateRequest(key, date)) }

    suspend fun dueDatePerm(key: String): DueDatePermResult = try {
        val body = api.dueDatePerm(KeyRequest(key)).bodyOrError<DueDatePermResponse>()
        if (body?.ok == true) DueDatePermResult.Success(body.canEdit) else DueDatePermResult.Error
    } catch (e: Exception) {
        DueDatePermResult.Error
    }

    suspend fun toggleCustomStatus(key: String, value: String, summary: String): CustomStatusResult = try {
        val body = api.setCustomStatus(CustomStatusRequest(key, value, summary))
            .bodyOrError<CustomStatusResponse>()
        if (body?.ok == true) {
            CustomStatusResult.Success(body.values.mapNotNull { CustomStatus.ofValue(it) })
        } else {
            CustomStatusResult.Error
        }
    } catch (e: Exception) {
        CustomStatusResult.Error
    }

    // --- helpers -------------------------------------------------------------

    /** Runs a PAT-backed write and maps 200/400 (+ no_pat code) to a [WriteResult]. */
    private suspend fun write(call: suspend () -> Response<MsgResponse>): WriteResult = try {
        val body = call().bodyOrError<MsgResponse>()
        if (body?.ok == true) WriteResult.Success(body.msg)
        else WriteResult.Failure(body?.msg, noPat = body?.code == "no_pat")
    } catch (e: Exception) {
        WriteResult.Failure(null)
    }

    /**
     * The parsed body on success, else the parsed error body (backend sends a JSON body with
     * 400 too). Null when neither parses — the caller then reports a generic failure.
     */
    private inline fun <reified T> Response<T>.bodyOrError(): T? =
        body() ?: errorBody()?.string()?.let { raw ->
            runCatching { json.decodeFromString<T>(raw) }.getOrNull()
        }
}

// --- mappers -----------------------------------------------------------------

private fun DetailDto.toDomain(): TaskDetail = TaskDetail(
    key = key,
    summary = summary,
    description = description,
    status = status,
    assignee = assignee,
    dueDate = duedate?.ifBlank { null },
    updated = updated?.ifBlank { null },
    created = created?.ifBlank { null },
    devs = devs,
    comments = comments.map { Comment(it.author, it.whenAt, it.body) },
    bugs = bugs.map { LinkedBug(it.id, it.summary, it.severity, it.status, it.module) },
    testcases = testcases.map { LinkedTestcase(it.id, it.name, it.count, it.pass, it.fail) },
)
