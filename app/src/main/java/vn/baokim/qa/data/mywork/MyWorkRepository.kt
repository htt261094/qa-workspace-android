package vn.baokim.qa.data.mywork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.baokim.qa.data.local.MyWorkDao
import vn.baokim.qa.data.local.MyWorkTaskEntity
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
 * (backed by Room), and [refresh] pulls `/api/my-work` and replaces the cache. This
 * cache-first shape means the list renders offline / instantly on cold start, then
 * updates when the network answers (E4.3/E4.4).
 *
 * All grouping/ordering is preserved exactly as the server sent it (D3) via the
 * bucket/task order columns; the repository only maps shapes, it decides nothing.
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
            dao.replaceAll(response.buckets.toEntities())
            RefreshResult.Success
        }
    } catch (e: Exception) {
        RefreshResult.Error
    }
}

// --- mappers -----------------------------------------------------------------

private fun List<TaskBucketDto>.toEntities(): List<MyWorkTaskEntity> =
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
                statusCategory = task.statusCategory,
                dueDate = task.dueDate,
                assignee = task.assignee,
                project = task.project,
                url = task.url,
                overdue = task.overdue,
            )
        }
    }

private fun List<MyWorkTaskEntity>.toBuckets(): List<TaskBucket> =
    groupBy { it.bucketKey }
        .map { (_, rows) ->
            val head = rows.first()
            TaskBucket(
                key = head.bucketKey,
                label = head.bucketLabel,
                tasks = rows.map { it.toDomain() },
            )
        }
    // rows already arrive ordered by bucketOrder,taskOrder (DAO query); groupBy is stable,
    // so buckets and tasks keep the server's order without re-sorting.

private fun MyWorkTaskEntity.toDomain(): MyWorkTask = MyWorkTask(
    key = key,
    summary = summary,
    status = status,
    statusCategory = StatusCategory.from(statusCategory),
    dueDate = dueDate,
    assignee = assignee,
    project = project,
    url = url,
    overdue = overdue,
)
