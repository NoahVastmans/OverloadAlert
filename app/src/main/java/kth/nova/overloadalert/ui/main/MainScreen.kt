package kth.nova.overloadalert.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.ui.navigation.Screen
import kth.nova.overloadalert.ui.screens.graphs.GraphsScreen
import kth.nova.overloadalert.ui.screens.history.HistoryScreen
import kth.nova.overloadalert.ui.screens.home.HomeScreen
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import kth.nova.overloadalert.ui.screens.plan.PlanScreen
import kth.nova.overloadalert.ui.screens.preferences.PreferencesScreen

/**
 * The main composable screen of the application that serves as the root container for the UI.
 *
 * This component sets up the [Scaffold] structure, including the bottom navigation bar and the
 * central navigation host. It manages the app's top-level navigation logic, allowing switching between
 * the primary screens: Home, History, Graphs, and Plan. It also handles navigation to the Preferences screen.
 *
 * The bottom navigation bar is dynamically shown only for top-level destinations found in [navItems].
 *
 * @param appComponent The dependency injection component used to provide ViewModels and dependencies
 * to the child screens.
 */
@Composable
fun MainScreen(appComponent: AppComponent) {
    val navController = rememberNavController()
    val navItems = listOf(Screen.Home, Screen.History, Screen.Graphs, Screen.Plan)
    val homeViewModel: HomeViewModel = viewModel(factory = appComponent.homeViewModelFactory)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = navItems.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToPreferences = { navController.navigate(Screen.Preferences.route) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(viewModel = viewModel(factory = appComponent.historyViewModelFactory))
            }
            composable(Screen.Graphs.route) {
                GraphsScreen(appComponent = appComponent)
            }
            composable(Screen.Plan.route) {
                PlanScreen(appComponent = appComponent)
            }
            composable(Screen.Preferences.route) {
                PreferencesScreen(
                    appComponent = appComponent,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}