package vn.baokim.qa.domain.mywork

/**
 * UI-facing model for the personal "Việc của tôi" lens (E4, #7).
 *
 * Backend `/api/my-work` returns a **flat** task list (no server-side buckets), so the
 * grouping into the personal-lens buckets + sort-by-due lives in [MyWorkBuckets] — one
 * place, thin logic. If the backend later returns pre-bucketed data (see D6) that's the
 * only thing that changes; these types and the UI stay put.
 */
data class TaskBucket(
    val key: String,
    val label: String,
    val tasks: List<MyWorkTask>,
)

data class MyWorkTask(
    val key: String,              // Jira issue key, e.g. "PSIT1H26-123"
    val summary: String,
    val status: String,           // raw Jira status (backend `jira`), e.g. "In Progress"
    val statusCategory: StatusCategory,
    val dueDate: String?,         // ISO yyyy-MM-dd (backend `due`), or null
    val overdue: Boolean,         // backend-computed (`overdue`) — app doesn't re-derive
    val url: String?,             // Jira browse URL
    val customs: List<String> = emptyList(), // custom-status slugs, seeds the detail overlay (E5.5)
)

/**
 * Jira statusCategory (spec §2): drives the status pill colour. Derived from the raw
 * status string since the endpoint doesn't send a category. Unknown → [NEW] so a new
 * Jira status never crashes an old app build.
 */
enum class StatusCategory {
    NEW,            // "TO DO"
    INDETERMINATE,  // "In Progress", "PENDING"
    DONE;           // "DONE", "CANCELLED"

    companion object {
        fun fromStatus(status: String?): StatusCategory = when (status?.trim()?.uppercase()) {
            "DONE", "CANCELLED" -> DONE
            "IN PROGRESS", "PENDING" -> INDETERMINATE
            else -> NEW
        }

        /** Safe parse of a persisted enum name (Room cache). Unknown → [NEW]. */
        fun ofName(name: String?): StatusCategory =
            entries.firstOrNull { it.name == name } ?: NEW
    }
}

/**
 * Groups the flat task list into the personal-lens buckets (spec §3.4:
 * "Active / Đang làm / TO DO / Done") and sorts each bucket by due date ascending
 * (tasks without a due date go last). Empty buckets are dropped.
 *
 * NOTE: this is the one bit of client-side derivation the flat endpoint forces on us
 * (D3 says logic belongs on the backend). Kept trivial and centralised on purpose.
 */
object MyWorkBuckets {

    private data class Def(val key: String, val label: String)

    private val ACTIVE = Def("active", "Active")
    private val DOING = Def("doing", "Đang làm")
    private val TODO = Def("todo", "TO DO")
    private val DONE = Def("done", "Done")

    /** Display order = spec §3.4. */
    private val ORDER = listOf(ACTIVE, DOING, TODO, DONE)

    private fun defFor(status: String): Def = when (status.trim().uppercase()) {
        "IN PROGRESS" -> DOING
        "TO DO" -> TODO
        "DONE", "CANCELLED" -> DONE
        else -> ACTIVE // PENDING + any unmapped in-flight status
    }

    // ISO yyyy-MM-dd sorts correctly as plain strings; nulls (no due) sink to the bottom.
    private val byDue = compareBy<MyWorkTask>({ it.dueDate == null }, { it.dueDate })

    fun group(tasks: List<MyWorkTask>): List<TaskBucket> {
        val byBucket = tasks.groupBy { defFor(it.status).key }
        return ORDER.mapNotNull { def ->
            val items = byBucket[def.key]?.sortedWith(byDue)
            if (items.isNullOrEmpty()) null else TaskBucket(def.key, def.label, items)
        }
    }
}
