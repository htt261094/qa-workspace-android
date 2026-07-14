package vn.baokim.qa.data.activity

import vn.baokim.qa.data.auth.AuthManager
import vn.baokim.qa.domain.activity.ActivityItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data access for the notification bell (E7, #11). Network-only, no cache — activities are
 * ephemeral and read-state lives server-side (`/dismiss`). [pollNew] returns the unread
 * activities the app hasn't notified about yet and folds the local dedup set forward;
 * [dismiss] marks ids read for this user.
 */
@Singleton
class ActivityRepository @Inject constructor(
    private val api: ActivityApi,
    private val notified: NotifiedStore,
    private val authManager: AuthManager,
) {

    /**
     * Fetches the feed and returns the unread items not yet surfaced as notifications, then
     * updates the local dedup set: keep only ids still in the feed (bounds growth as events age
     * out of the backend window) plus the ones we're about to notify. Empty when logged out, on
     * a 400 (Jira down — bell is best-effort), or on any error, leaving the dedup set untouched.
     */
    suspend fun pollNew(): List<ActivityItem> {
        if (authManager.token == null) return emptyList()
        val response = try {
            api.activityFeed()
        } catch (e: Exception) {
            return emptyList()
        }
        val body = response.body()
        if (!response.isSuccessful || body == null || !body.ok) return emptyList()

        val items = body.activities.filter { it.id.isNotBlank() }.map { it.toDomain() }
        val feedIds = items.mapTo(HashSet()) { it.id }
        val alreadyNotified = notified.notified()

        val fresh = items.filter { it.unread && it.id !in alreadyNotified }
        // Prune to ids still in the feed, then add the freshly-notified ones.
        notified.setNotified((alreadyNotified intersect feedIds) + fresh.map { it.id })
        return fresh
    }

    /** Mark activity [ids] read server-side (best-effort — a failure just means it stays unread). */
    suspend fun dismiss(ids: List<String>): Boolean {
        if (ids.isEmpty() || authManager.token == null) return false
        return try {
            api.dismiss(DismissRequest(ids)).body()?.ok == true
        } catch (e: Exception) {
            false
        }
    }

    fun clearNotified() = notified.clear()
}

private fun ActivityDto.toDomain(): ActivityItem = ActivityItem(
    id = id,
    kind = kind,
    key = key,
    summary = summary,
    author = author,
    whenAt = whenAt,
    unread = isUnread,
    old = old,
    new = new,
    assignee = assignee,
    body = body,
    mention = mention,
)
