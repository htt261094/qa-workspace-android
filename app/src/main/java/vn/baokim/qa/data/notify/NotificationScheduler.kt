package vn.baokim.qa.data.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the WorkManager wiring for notifications (E7). [start] on login, [stop] on logout so we
 * never poll (or leak notifications) for a signed-out user. [dismiss] fires a one-time job when
 * a notification is read.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Enqueue the periodic poll (idempotent — KEEP preserves an already-scheduled cycle) plus a
     * one-time kick that runs as soon as constraints allow. Periodic work is deferred within its
     * first 15-min window, so without the immediate kick the very first notification could take up
     * to 15 min after login — the kick surfaces it in seconds (and makes the feature observable in
     * a quick test).
     */
    fun start() {
        val netConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<ActivityPollWorker>(POLL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(netConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(POLL_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)

        val kick = OneTimeWorkRequestBuilder<ActivityPollWorker>()
            .setConstraints(netConstraint)
            .build()
        workManager.enqueueUniqueWork(POLL_KICK_WORK, ExistingWorkPolicy.REPLACE, kick)
    }

    fun stop() {
        workManager.cancelUniqueWork(POLL_WORK)
        workManager.cancelUniqueWork(POLL_KICK_WORK)
    }

    /** Mark one activity read (tap or swipe). Retries on network failure. */
    fun dismiss(id: String) {
        val request = OneTimeWorkRequestBuilder<DismissWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(DismissWorker.KEY_IDS to arrayOf(id)))
            .build()
        // APPEND so rapid dismisses queue instead of clobbering each other.
        workManager.enqueueUniqueWork(DISMISS_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val POLL_WORK = "activity-poll"
        const val POLL_KICK_WORK = "activity-poll-kick"
        const val DISMISS_WORK = "activity-dismiss"
        const val POLL_MINUTES = 15L // WorkManager periodic minimum (D9)
    }
}
