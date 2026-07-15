package vn.baokim.qa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache of the latest `/api/bug-log` snapshot (E8.6, #13) so the Bug Log renders
 * instantly on cold start / offline before the network refresh lands — same replace-all,
 * flat-table shape as [MyWorkTaskEntity] (D7 OPSEC §7: no secrets, only public bug metadata).
 *
 * The rows are stored in backend order via [rowOrder] so the month tabs and lists come back
 * in the same order without re-sorting. `editable`/`syncedAt` are transient (role- and
 * refresh-derived) so they're not cached — the ViewModel holds them from the last refresh.
 */
@Entity(tableName = "bug_log")
data class BugEntity(
    @PrimaryKey val key: String,
    val rowOrder: Int,
    val id: String,
    val summary: String,
    val module: String,
    val severity: String,
    val status: String,
    val project: String,
    val month: String,
    val tester: String,
    val dev: String,
    val created: String,
    val tasks: String = "", // linked Jira task keys as CSV; "" = none
)
