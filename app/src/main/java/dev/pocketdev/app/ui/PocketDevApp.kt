package dev.pocketdev.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pocketdev.app.ui.screens.DashboardScreen
import dev.pocketdev.app.ui.screens.ProjectsScreen
import dev.pocketdev.app.ui.screens.SettingsScreen

private data class TopLevelRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelRoutes = listOf(
    TopLevelRoute("dashboard", "Dashboard", Icons.Filled.Dashboard),
    TopLevelRoute("projects", "Projects", Icons.Filled.Folder),
    TopLevelRoute("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun PocketDevApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { route ->
                    NavigationBarItem(
                        icon = { Icon(route.icon, contentDescription = route.label) },
                        label = { Text(route.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == route.route } == true,
                        onClick = {
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") { DashboardScreen() }
            composable("projects") { ProjectsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}