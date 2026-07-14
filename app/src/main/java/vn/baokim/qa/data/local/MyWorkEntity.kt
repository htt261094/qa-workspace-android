package vn.baokim.qa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache of the latest `/api/my-work` snapshot (E4.4, #7) so the list shows instantly
 * offline / on cold start before the network refresh lands. One device = one logged-in
 * user, so a single flat table is enough; [bucketOrder]/[taskOrder] preserve the exact
 * server ordering (D3 — the app never re-sorts) when regrouping back into buckets.
 *
 * No secrets live here: only public Jira task metadata, never the PAT/token (OPSEC §7).
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
    val assignee: String?,
    val project: String?,
    val url: String?,
    val overdue: Boolean,
)
