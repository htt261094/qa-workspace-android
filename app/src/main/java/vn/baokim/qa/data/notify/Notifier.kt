package vn.baokim.qa.data.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import vn.baokim.qa.MainActivity
import vn.baokim.qa.R
import vn.baokim.qa.domain.activity.ActivityItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns [ActivityItem]s into system notifications (E7.2). One notification per activity, keyed
 * by its stable id so re-posting the same event just updates in place. Tapping opens the task
 * (deep-link to detail) and marks it read; swiping it away also marks it read (E7.3) — both
 * routed through [DismissReceiver] / [MainActivity].
 *
 * Posting is a no-op when the user hasn't granted POST_NOTIFICATIONS (Android 13+) — a Worker
 * can't request runtime permission; the UI asks for it (E7.3).
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(
            NotifyContract.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.notif_channel_activity))
            .setDescription(context.getString(R.string.notif_channel_activity_desc))
            .build()
        manager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    /** Posts a notification for each item. No-op silently if notifications aren't permitted. */
    fun notify(items: List<ActivityItem>) {
        if (items.isEmpty() || !canPost()) return
        ensureChannel()
        for (item in items) {
            manager.notify(item.id.hashCode(), build(item))
        }
    }

    private fun build(item: ActivityItem): android.app.Notification {
        val notifId = item.id.hashCode()
        return NotificationCompat.Builder(context, NotifyContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setGroup(NotifyContract.GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(notifId, item))
            .setDeleteIntent(dismissIntent(notifId, item.id))
            .build()
    }

    /** Tap → open MainActivity, deep-link to the task, and mark read (MainActivity enqueues dismiss). */
    private fun tapIntent(notifId: Int, item: ActivityItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotifyContract.EXTRA_DISMISS_ID, item.id)
            item.key?.let { putExtra(NotifyContract.EXTRA_TASK_KEY, it) }
        }
        return PendingIntent.getActivity(context, notifId, intent, PENDING_FLAGS)
    }

    /** Swipe → broadcast to [DismissReceiver], which marks the activity read server-side. */
    private fun dismissIntent(notifId: Int, id: String): PendingIntent {
        val intent = Intent(context, DismissReceiver::class.java).apply {
            action = NotifyContract.ACTION_DISMISS
            putExtra(NotifyContract.EXTRA_DISMISS_ID, id)
        }
        return PendingIntent.getBroadcast(context, notifId, intent, PENDING_FLAGS)
    }

    private companion object {
        val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
