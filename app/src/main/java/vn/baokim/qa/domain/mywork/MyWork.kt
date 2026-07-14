package vn.baokim.qa.domain.mywork

/**
 * UI-facing model for the personal "Việc của tôi" lens (E4, #7).
 *
 * The backend owns all business logic (D3): it decides which buckets exist, in what
 * order, how tasks are grouped, and their sort (spec §3.4 = sort theo due date). The
 * app renders buckets and tasks in the exact order the server returns — no bucket
 * derivation or re-sorting here, so there's a single source of truth and no Python↔Kotlin
 * parity to keep. These types are what the repository maps DTO + Room cache into.
 */
data class TaskBucket(
    val key: String,
    val label: String,
    val tasks: List<MyWorkTask>,
)

data class MyWorkTask(
    val key: String,              // Jira issue key, e.g. "PSIT1H26-123"
    val summary: String,
    val status: String,           // raw Jira status, e.g. "In Progress" (spec §2 exact case)
    val statusCategory: StatusCategory,
    val dueDate: String?,         // ISO yyyy-MM-dd as returned by backend, or null
    val assignee: String?,
    val project: String?,
    val url: String?,             // Jira browse URL (detail screen is E5; unused for now)
    val overdue: Boolean,         // backend-computed (D3) — app doesn't re-derive from dueDate
)

/**
 * Jira statusCategory (spec §2): drives the status pill colour only. Unknown values from
 * the server fall back to [NEW] so a new Jira category never crashes an old app build.
 */
enum class StatusCategory {
    NEW,            // "TO DO"
    INDETERMINATE,  // "In Progress", "PENDING"
    DONE;           // "DONE", "CANCELLED"

    companion object {
        fun from(raw: String?): StatusCategory = when (raw?.trim()?.lowercase()) {
            "indeterminate" -> INDETERMINATE
            "done" -> DONE
            else -> NEW
        }
    }
}
