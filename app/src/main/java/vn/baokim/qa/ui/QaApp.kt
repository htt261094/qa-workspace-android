package vn.baokim.qa.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.baokim.qa.data.auth.Role
import vn.baokim.qa.ui.navigation.TopDestination
import vn.baokim.qa.ui.screens.PlaceholderScreen

@Composable
fun QaApp(role: Role) {
    val navController = rememberNavController()
    // E2.4: only the tabs this role may see. Start (MyWork) is visible to every role.
    val tabs = TopDestination.entries.filter { it.isVisibleTo(role) }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.MyWork.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Dashboard route is registered only for roles that may see it — no dead
            // deep-link into an admin screen for QA/dev (its tab is hidden anyway).
            if (role.canSeeDashboard) {
                composable(TopDestination.Dashboard.route) { PlaceholderScreen("Dashboard team (E6)") }
            }
            composable(TopDestination.MyWork.route) { PlaceholderScreen("Việc của tôi (E4)") }
            composable(TopDestination.Bugs.route) {
                val suffix = if (role.bugLogReadOnly) " · read-only" else ""
                PlaceholderScreen("Bug Log (E8)$suffix")
            }
        }
    }
}
