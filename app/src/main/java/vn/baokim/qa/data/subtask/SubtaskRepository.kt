package vn.baokim.qa.data.subtask

import kotlinx.serialization.json.Json
import retrofit2.Response
import vn.baokim.qa.domain.subtask.CreatedSubtask
import vn.baokim.qa.domain.subtask.FailedSubtask
import vn.baokim.qa.domain.subtask.ParentTask
import vn.baokim.qa.domain.subtask.Person
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a `/create-subtasks` write (E9.2). */
sealed interface CreateSubtasksResult {
    /**
     * The backend attempted the create. [created] and [failed] together cover every summary
     * sent — either list may be empty (all-ok, all-failed, or partial).
     */
    data class Done(
        val created: List<CreatedSubtask>,
        val failed: List<FailedSubtask>,
    ) : CreateSubtasksResult

    /** Early failure before any create (bad parent, missing field, transport). */
    data class Failure(val message: String?, val noPat: Boolean = false) : CreateSubtasksResult
}

/**
 * Single point for the create-QA-sub-task screen (E9, #14) over [SubtaskApi]. Every call is
 * guarded so transport failures become empty results / typed failures instead of exceptions in
 * the UI. Nothing is cached — searches are live type-ahead and the create is a one-shot write.
 * Nothing sensitive is stored or logged (OPSEC §7).
 */
@Singleton
class SubtaskRepository @Inject constructor(
    private val api: SubtaskApi,
    private val json: Json,
) {

    /** Type-ahead parents. Returns [] on any failure or a too-short query (backend needs ≥2). */
    suspend fun searchParents(query: String): List<ParentTask> = try {
        if (query.trim().length < 2) emptyList()
        else api.searchParents(query.trim()).bodyOrError<SearchParentsResponse>()
            ?.takeIf { it.ok }
            ?.results
            ?.map { ParentTask(it.key, it.summary, it.project) }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Type-ahead users for Assignee / Leader. Returns [] on any failure or short query. */
    suspend fun searchPeople(query: String): List<Person> = try {
        if (query.trim().length < 2) emptyList()
        else api.searchPeople(query.trim()).bodyOrError<SearchPeopleResponse>()
            ?.takeIf { it.ok }
            ?.results
            ?.map { Person(it.name, it.display.ifBlank { it.name }) }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Create sub-task(s). [assignee]/[leader] are Jira usernames or "" for none. Maps the
     * backend's created/failed lists to a [CreateSubtasksResult.Done], and a bare-msg 400 (or
     * transport error) to [CreateSubtasksResult.Failure] (with [no_pat][code] flagged).
     */
    suspend fun createSubtasks(
        parent: String,
        summaries: List<String>,
        startDate: String,
        dueDate: String,
        assignee: String,
        leader: String,
    ): CreateSubtasksResult = try {
        val body = api.createSubtasks(
            CreateSubtasksRequest(parent, summaries, startDate, dueDate, assignee, leader)
        ).bodyOrError<CreateSubtasksResponse>()
        when {
            body == null -> CreateSubtasksResult.Failure(null)
            body.created.isNotEmpty() || body.failed.isNotEmpty() -> CreateSubtasksResult.Done(
                created = body.created.map { CreatedSubtask(it.key, it.summary, it.url) },
                failed = body.failed.map { FailedSubtask(it.summary, it.msg) },
            )
            else -> CreateSubtasksResult.Failure(body.msg, noPat = body.code == "no_pat")
        }
    } catch (e: Exception) {
        CreateSubtasksResult.Failure(null)
    }

    /**
     * The parsed body on success, else the parsed error body (backend sends JSON with 400 too).
     * Null when neither parses — the caller then reports a generic failure / empty list.
     */
    private inline fun <reified T> Response<T>.bodyOrError(): T? =
        body() ?: errorBody()?.string()?.let { raw ->
            runCatching { json.decodeFromString<T>(raw) }.getOrNull()
        }
}
