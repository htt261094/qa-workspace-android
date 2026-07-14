package vn.baokim.qa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import vn.baokim.qa.data.auth.AppAuthLink
import vn.baokim.qa.data.auth.AuthManager
import vn.baokim.qa.ui.QaApp
import vn.baokim.qa.ui.auth.LoginScreen
import vn.baokim.qa.ui.theme.QaWorkspaceTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold-start via App Link (server redirect after OAuth) lands here.
        handleAuthRedirect(intent)
        enableEdgeToEdge()
        setContent {
            QaWorkspaceTheme {
                val loggedIn by authManager.isLoggedIn.collectAsState()
                if (loggedIn) QaApp() else LoginScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm App Link (Custom Tab still open in background) re-enters singleTop.
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    /** Pulls the session token out of `/app/auth#token=` and hands it to [AuthManager] (D2 hướng C). */
    private fun handleAuthRedirect(intent: Intent?) {
        AppAuthLink.extractToken(intent?.data)?.let { authManager.onTokenReceived(it) }
    }
}
