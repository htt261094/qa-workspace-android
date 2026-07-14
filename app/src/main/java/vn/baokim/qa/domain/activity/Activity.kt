package vn.baokim.qa.domain.activity

/**
 * One item from the notification bell feed (`/activity-feed`, E7, #11). The backend
 * reconstructs these from Jira changelog/comments + the internal custom-status overlay,
 * and already drops the current user's own events (`_drop_own_activities`) — so the app
 * notifies for everything it receives, no self-filter needed (spec §6, CLAUDE.md E7).
 *
 * [id] is stable and unique per event (`key#…` shapes from the backend) — the dedup key
 * for "did we already notify?" and the value passed to `/dismiss` when the user reads it.
 * [kind] drives the human-readable notification text ([title]/[text]).
 */
data class ActivityItem(
    val id: String,
    val kind: String,
    val key: String?,
    val summary: String,
    val author: String,
    val whenAt: String?,
    val unread: Boolean,
    val old: String?,
    val new: String?,
    val assignee: String?,
    val body: String?,
    val mention: Boolean,
) {
    /** Notification title: the issue summary, falling back to its key. */
    val title: String get() = summary.ifBlank { key.orEmpty() }.ifBlank { "Cập nhật" }

    /**
     * One-line description keyed off [kind]. Mirrors the wording the web bell shows for
     * each event type; the backend fills [old]/[new]/[body] per kind (see `_compute_activity_feed`).
     */
    val text: String
        get() {
            val who = author.ifBlank { "Ai đó" }
            return when (kind) {
                "created" -> if (!assignee.isNullOrBlank()) "$who tạo task mới · giao $assignee"
                else "$who tạo task mới"
                "status" -> "$who đổi trạng thái: ${old ?: "—"} → ${new ?: "—"}"
                "assignee" -> "$who giao cho ${new ?: "—"}"
                "duedate" -> "$who đổi hạn: ${old ?: "—"} → ${new ?: "—"}"
                "priority" -> "$who đổi ưu tiên: ${old ?: "—"} → ${new ?: "—"}"
                "comment" -> {
                    val head = if (mention) "$who nhắc đến bạn" else "$who bình luận"
                    if (!body.isNullOrBlank()) "$head: $body" else head
                }
                // custom_status events carry the label change in `new` (e.g. "✕ Chờ QC" / "— (gỡ hết nhãn)")
                "custom_status" -> "$who: ${new ?: "cập nhật nhãn"}"
                else -> "$who cập nhật"
            }
        }
}
