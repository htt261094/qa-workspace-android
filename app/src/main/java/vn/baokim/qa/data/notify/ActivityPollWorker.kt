package vn.baokim.qa.data.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import vn.baokim.qa.data.activity.ActivityRepository

/**
 * Periodic poll of `/activity-feed` → notifications (E7.1). WorkManager's minimum periodic
 * interval is 15 min (not the 60s the spec mentions — that's impossible for deferrable periodic
 * work and would drain battery); the scheduler uses that (D9). [ActivityRepository.pollNew]
 * already swallows network/auth failures and returns empty, so the worker just posts what it
 * gets and reports success — periodic work reschedules regardless.
 */
@HiltWorker
class ActivityPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ActivityRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notifier.notify(repository.pollNew())
        return Result.success()
    }
}
