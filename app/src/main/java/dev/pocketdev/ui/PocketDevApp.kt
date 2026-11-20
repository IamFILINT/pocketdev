package dev.pocketdev.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pocketdev.R
import dev.pocketdev.ui.dashboard.DashboardRoute
import dev.pocketdev.ui.dashboard.DashboardViewModel
import dev.pocketdev.ui.projects.ProjectsRoute
import dev.pocketdev.ui.projects.ProjectsViewModel
import dev.pocketdev.ui.settings.SettingsRoute
import dev.pocketdev.ui.settings.SettingsViewModel

private enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard("dashboard", R.string.nav_dashboard, Icons.Filled.Home),
    Projects("projects", R.string.nav_projects, Icons.Filled.List),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketDevApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val projectsViewModel: ProjectsViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()

    val projectsState by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val runtimeState by dashboardViewModel.runtimeState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = destinationForRoute(currentRoute)?.labelRes?.let { stringResource(it) }
                            ?: stringResource(R.string.app_name),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Dashboard.route) {
                DashboardRoute(
                    projectCount = projectsState.projects.size,
                    runtimeState = runtimeState,
                )
            }
            composable(Destination.Projects.route) {
                ProjectsRoute(viewModel = projectsViewModel)
            }
            composable(Destination.Settings.route) {
                SettingsRoute(viewModel = settingsViewModel)
            }
        }
    }
}

private fun destinationForRoute(route: String?): Destination? =
    Destination.entries.firstOrNull { it.route == route }