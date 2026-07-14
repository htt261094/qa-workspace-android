package vn.baokim.qa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import vn.baokim.qa.data.auth.AppAuthLink
import vn.baokim.qa.data.auth.AuthManager
import vn.baokim.qa.data.notify.NotificationScheduler
import vn.baokim.qa.data.notify.Notifier
import vn.baokim.qa.data.notify.NotifyContract
import vn.baokim.qa.ui.QaApp
import vn.baokim.qa.ui.auth.LoginScreen
import vn.baokim.qa.ui.theme.QaWorkspaceTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var scheduler: NotificationScheduler
    @Inject lateinit var notifier: Notifier

    /** Task key delivered by a notification tap, to deep-link into detail once composed (E7.3). */
    private val pendingTaskKey = mutableStateOf<String?>(null)

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result irrelevant — poll still runs, just won't post until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notifier.ensureChannel()
        // Cold-start via App Link (OAuth redirect) or a notification tap lands here.
        handleAuthRedirect(intent)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            QaWorkspaceTheme {
                val loggedIn by authManager.isLoggedIn.collectAsState()
                // Poll only while signed in (E7); stop on logout/401 so we don't poll for nobody.
                LaunchedEffect(loggedIn) {
                    if (loggedIn) {
                        requestNotifPermissionIfNeeded()
                        scheduler.start()
                    } else {
                        scheduler.stop()
                    }
                }
                if (loggedIn) {
                    val role by authManager.role.collectAsState()
                    QaApp(
                        role = role,
                        deepLinkTaskKey = pendingTaskKey.value,
                        onDeepLinkConsumed = { pendingTaskKey.value = null },
                    )
                } else {
                    LoginScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm re-entry (singleTop): App Link redirect or a notification tap.
        setIntent(intent)
        handleAuthRedirect(intent)
        handleNotificationIntent(intent)
    }

    /** Pulls the session token out of `/app/auth#token=` and hands it to [AuthManager] (D2 hướng C). */
    private fun handleAuthRedirect(intent: Intent?) {
        AppAuthLink.extractToken(intent?.data)?.let { authManager.onTokenReceived(it) }
    }

    /** Notification tapped (E7.3): mark it read server-side + remember the task to open. */
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.getStringExtra(NotifyContract.EXTRA_DISMISS_ID)?.let { scheduler.dismiss(it) }
        intent?.getStringExtra(NotifyContract.EXTRA_TASK_KEY)?.let { pendingTaskKey.value = it }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
