package vn.baokim.qa.data.notify

/** Shared keys/ids for the activity notification channel and its intents (E7, #11). */
object NotifyContract {
    const val CHANNEL_ID = "activity"
    const val GROUP_KEY = "vn.baokim.qa.ACTIVITY"

    /** Broadcast action for swipe-away → mark the activity read server-side. */
    const val ACTION_DISMISS = "vn.baokim.qa.action.DISMISS_ACTIVITY"

    /** Extra: the `/dismiss` id of the tapped/swiped activity. */
    const val EXTRA_DISMISS_ID = "dismiss_id"

    /** Extra: the Jira issue key to deep-link into task detail on tap. */
    const val EXTRA_TASK_KEY = "task_key"
}
