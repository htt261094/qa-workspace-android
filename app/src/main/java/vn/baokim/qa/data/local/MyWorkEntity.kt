package vn.baokim.qa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache of the latest `/api/my-work` snapshot (E4.4, #7) so the list shows instantly
 * offline / on cold start before the network refresh lands. One device = one logged-in
 * user, so a single flat table is enough. Rows are stored already grouped+sorted into
 * buckets ([bucketOrder]/[taskOrder]) so reads regroup without re-running bucket logic.
 *
 * No secrets here: only public Jira task metadata, never the PAT/token (OPSEC §7).
 */
@Entity(tableName = "my_work_tasks")
data class MyWorkTaskEntity(
    @PrimaryKey val key: String,
    val bucketKey: String,
    val bucketLabel: String,
    val bucketOrder: Int,
    val taskOrder: Int,
    val summary: String,
    val status: String,
    val statusCategory: String,
    val dueDate: String?,
    val overdue: Boolean,
    val url: String?,
    val customs: String = "", // custom-status slugs as CSV (E5.5); "" = none
)
