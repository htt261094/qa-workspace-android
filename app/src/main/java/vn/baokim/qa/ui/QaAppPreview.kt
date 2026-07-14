package vn.baokim.qa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import vn.baokim.qa.data.auth.Role
import vn.baokim.qa.ui.theme.QaWorkspaceTheme

/**
 * Design-time previews for the role gate (E2.4, #4). Render each [Role] without an
 * emulator, login, or backend — open this file in Android Studio and use Split/Design
 * to see which bottom-nav tabs each role gets:
 *
 *   ADMIN → Dashboard · Việc của tôi · Bugs   (3 tabs)
 *   QA    → Việc của tôi · Bugs               (2 tabs, no Dashboard)
 *   DEV   → Việc của tôi · Bugs (read-only)   (2 tabs, Bug Log marked read-only)
 *
 * These are preview-only; they don't ship any behaviour into the app.
 */
@Preview(name = "ADMIN — 3 tabs", showBackground = true)
@Composable
private fun QaAppAdminPreview() {
    QaWorkspaceTheme { QaApp(role = Role.ADMIN) }
}

@Preview(name = "QA — no Dashboard", showBackground = true)
@Composable
private fun QaAppQaPreview() {
    QaWorkspaceTheme { QaApp(role = Role.QA) }
}

@Preview(name = "DEV — Bug Log read-only", showBackground = true)
@Composable
private fun QaAppDevPreview() {
    QaWorkspaceTheme { QaApp(role = Role.DEV) }
}
