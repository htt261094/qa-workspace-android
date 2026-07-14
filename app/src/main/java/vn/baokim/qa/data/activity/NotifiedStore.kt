package vn.baokim.qa.data.activity

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which activity ids we've already raised a system notification for, so the periodic
 * poll (E7.1) doesn't re-post the same event every cycle in the window between showing it and
 * the user reading it. Not a security surface — just activity ids, no PAT/token — so plain
 * (unencrypted) SharedPreferences is fine (OPSEC §7: only secrets go to EncryptedSharedPreferences).
 *
 * Server-side `dismissed` (via `/dismiss`) is the real read-state source of truth; this is only
 * a local anti-duplicate guard. It's pruned each poll to the ids still present in the feed
 * (backend caps the feed to a rolling window) so it stays bounded.
 */
@Singleton
class NotifiedStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun notified(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()

    /** Persist [ids] as the full notified set (already pruned by the caller). */
    fun setNotified(ids: Set<String>) {
        // Defensive copy: getStringSet returns a shared instance that must not be mutated/reused.
        prefs.edit().putStringSet(KEY_IDS, HashSet(ids)).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val FILE_NAME = "qa_notified_activities"
        const val KEY_IDS = "notified_ids"
    }
}
