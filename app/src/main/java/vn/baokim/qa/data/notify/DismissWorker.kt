package vn.baokim.qa.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import vn.baokim.qa.data.activity.ActivityRepository

/**
 * Marks activity ids read server-side (`/dismiss`, E7.3), off the main thread with retry so a
 * swipe/tap survives a transient network blip. Enqueued by [DismissReceiver] (swipe) and
 * [MainActivity][vn.baokim.qa.MainActivity] (tap).
 */
@HiltWorker
class DismissWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ActivityRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getStringArray(KEY_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return Result.success()
        if (repository.dismiss(ids)) return Result.success()
        // Best-effort: local dedup already prevents re-notifying, so /dismiss only syncs read-state
        // to the web bell. Retry transient failures a few times, then give up (a permanent 400 must
        // not loop forever).
        return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_IDS = "ids"
        private const val MAX_ATTEMPTS = 5
    }
}
