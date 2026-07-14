package vn.baokim.qa.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles a swipe-away on an activity notification (E7.3): marks that activity read server-side
 * via [NotificationScheduler.dismiss]. Kept thin — the actual network call runs in [DismissWorker]
 * so it survives the receiver's short lifetime and retries on failure.
 */
@AndroidEntryPoint
class DismissReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotifyContract.ACTION_DISMISS) return
        val id = intent.getStringExtra(NotifyContract.EXTRA_DISMISS_ID) ?: return
        scheduler.dismiss(id)
    }
}
