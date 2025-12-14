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

@Composable
fun MainScreen(appComponent: AppComponent) {
    val navController = rememberNavController()
    val navItems = listOf(Screen.Home, Screen.History, Screen.Graphs)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

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
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel(factory = appComponent.homeViewModelFactory))
            }
            composable(Screen.History.route) {
                HistoryScreen(viewModel = viewModel(factory = appComponent.historyViewModelFactory))
            }
            composable(Screen.Graphs.route) {
                GraphsScreen()
            }
        }
    }
}