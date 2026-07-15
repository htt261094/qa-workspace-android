package vn.baokim.qa.domain.subtask

/**
 * Domain models for creating QA sub-tasks (E9, #14).
 *
 * The app reuses the existing root-level backend handlers (D8-style, no `/api/` prefix):
 * `/search-parents` and `/search-people` (read-only shared PAT) feed the two type-ahead
 * pickers; `/create-subtasks` does the write on the caller's **personal PAT** (reporter =
 * the logged-in user). See CLAUDE.md D11.
 */

/** A Task-PTSP that can parent a QA sub-task (from `/search-parents`). */
data class ParentTask(
    val key: String,
    val summary: String,
    val project: String,
)

/** A Jira user for the Assignee / Leader pickers (from `/search-people`). */
data class Person(
    val name: String,      // Jira username, sent to the backend
    val display: String,   // display name, shown to the user
)

/** One sub-task the backend actually created. */
data class CreatedSubtask(
    val key: String,
    val summary: String,
    val url: String,
)

/** One sub-task the backend refused, with the server's Vietnamese reason. */
data class FailedSubtask(
    val summary: String,
    val msg: String,
)
