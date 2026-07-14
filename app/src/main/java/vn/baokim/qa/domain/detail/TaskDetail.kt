package vn.baokim.qa.domain.detail

/**
 * UI-facing model for the task detail screen (E5, #10). Sourced from `/issue-comments`
 * (`fetch_issue_detail`, read-only shared PAT) plus the linked bug/testcase reverse-lookups
 * the backend bundles into the same response (drawer parity with the web).
 *
 * Detail is always fetched fresh (network-only, no Room cache) — it's a focused single-task
 * read that also reflects writes the user just made (comment/status/due).
 */
data class TaskDetail(
    val key: String,
    val summary: String,
    val description: String,     // plain-text snippet (backend truncates to ~1200 chars)
    val status: String,          // raw Jira status name
    val assignee: String,        // display name, blank when unassigned
    val dueDate: String?,        // ISO yyyy-MM-dd, or null
    val updated: String?,        // yyyy-MM-dd
    val created: String?,        // yyyy-MM-dd
    val devs: List<String>,      // dev(s) in charge (sibling sub-task assignees under the parent)
    val comments: List<Comment>, // newest first (backend reverses)
    val bugs: List<LinkedBug>,
    val testcases: List<LinkedTestcase>,
)

data class Comment(
    val author: String,
    val whenIso: String?,        // Jira timestamp, e.g. "2026-07-10T09:12:00.000+0700"
    val body: String,
)

data class LinkedBug(
    val id: String,
    val summary: String,
    val severity: String,
    val status: String,
    val module: String,
)

data class LinkedTestcase(
    val id: String,
    val name: String,
    val count: Int,
    val pass: Int,
    val fail: Int,
)

/**
 * A Jira workflow transition available for the task right now (`/jira-transitions`).
 * [to] is the destination status name; [id] is what `/do-transition` needs.
 */
data class Transition(
    val id: String,
    val to: String,
)

/**
 * The internal custom-status overlay (E5.5): QA labels a task's real situation on top of
 * Jira's poor status set. A task can carry several at once; toggling one calls
 * `/set-custom-status` (author = logged-in user, no PAT needed) which returns the new full
 * list. The catalog below MUST stay in sync with the backend `CUSTOM_STATUSES` (value→label);
 * the backend rejects any value not in it (`is_valid`).
 */
enum class CustomStatus(val value: String, val label: String) {
    DEV_FIXING("dev_fixing", "Dev fix bug"),
    WAIT_BA("wait_ba", "Chờ BA confirm"),
    DEV_BUSY("dev_busy", "Dev có priority cao hơn"),
    PM_PAUSED("pm_paused", "PM tạm dừng"),
    WAIT_DEPLOY("wait_deploy", "Chờ deploy test"),
    WAIT_REVIEW("wait_review", "Chờ review/UAT"),
    WAIT_DATA("wait_data", "Chờ data test"),
    REOPENED("reopened", "Bug reopen");

    companion object {
        /** Resolve a persisted value to its enum; unknown → null (drop, don't crash). */
        fun ofValue(value: String?): CustomStatus? = entries.firstOrNull { it.value == value }
    }
}
