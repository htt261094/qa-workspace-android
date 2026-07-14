package vn.baokim.qa.data.mywork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.baokim.qa.data.local.MyWorkDao
import vn.baokim.qa.data.local.MyWorkTaskEntity
import vn.baokim.qa.domain.mywork.MyWorkBuckets
import vn.baokim.qa.domain.mywork.MyWorkTask
import vn.baokim.qa.domain.mywork.StatusCategory
import vn.baokim.qa.domain.mywork.TaskBucket
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a network refresh; the cache (and thus the UI) is untouched on [Error]. */
sealed interface RefreshResult {
    data object Success : RefreshResult
    data object Error : RefreshResult
}

/**
 * Single source of truth for "Việc của tôi" (E4, #7): the UI observes [observeBuckets]
 * (backed by Room), and [refresh] pulls `/api/my-work` and replaces the cache. Cache-first
 * so the list renders offline / instantly on cold start, then updates when the network
 * answers (E4.3/E4.4).
 *
 * The endpoint is a flat list; grouping into buckets + sort lives in [MyWorkBuckets] (D6).
 * The cache stores the already-grouped result (bucket/task order columns) so reads just
 * regroup by key without re-deriving.
 */
@Singleton
class MyWorkRepository @Inject constructor(
    private val api: MyWorkApi,
    private val dao: MyWorkDao,
) {

    fun observeBuckets(): Flow<List<TaskBucket>> =
        dao.observeAll().map { rows -> rows.toBuckets() }

    /** Fetches fresh data and replaces the cache. On any failure the cache is kept. */
    suspend fun refresh(): RefreshResult = try {
        val response = api.myWork()
        if (!response.ok) {
            RefreshResult.Error
        } else {
            val buckets = MyWorkBuckets.group(response.tasks.map { it.toDomain() })
            dao.replaceAll(buckets.toEntities())
            RefreshResult.Success
        }
    } catch (e: Exception) {
        RefreshResult.Error
    }
}

// --- mappers -----------------------------------------------------------------

private fun TaskDto.toDomain(): MyWorkTask = MyWorkTask(
    key = key,
    summary = summary,
    status = jira,
    statusCategory = StatusCategory.fromStatus(jira),
    dueDate = due,
    overdue = overdue,
    url = jiraUrl,
    customs = customs,
)

private fun List<TaskBucket>.toEntities(): List<MyWorkTaskEntity> =
    flatMapIndexed { bucketOrder, bucket ->
        bucket.tasks.mapIndexed { taskOrder, task ->
            MyWorkTaskEntity(
                key = task.key,
                bucketKey = bucket.key,
                bucketLabel = bucket.label,
                bucketOrder = bucketOrder,
                taskOrder = taskOrder,
                summary = task.summary,
                status = task.status,
                statusCategory = task.statusCategory.name,
                dueDate = task.dueDate,
                overdue = task.overdue,
                url = task.url,
                customs = task.customs.joinToString(","),
            )
        }
    }

private fun List<MyWorkTaskEntity>.toBuckets(): List<TaskBucket> =
    groupBy { it.bucketKey }
        .map { (_, rows) ->
            val head = rows.first()
            TaskBucket(head.bucketKey, head.bucketLabel, rows.map { it.toDomain() })
        }
    // rows arrive ordered by bucketOrder,taskOrder (DAO query) and groupBy is stable,
    // so buckets and tasks keep the grouped order without re-sorting.

private fun MyWorkTaskEntity.toDomain(): MyWorkTask = MyWorkTask(
    key = key,
    summary = summary,
    status = status,
    statusCategory = StatusCategory.ofName(statusCategory),
    dueDate = dueDate,
    overdue = overdue,
    url = url,
    customs = if (customs.isBlank()) emptyList() else customs.split(","),
)
