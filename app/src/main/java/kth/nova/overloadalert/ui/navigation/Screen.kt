package kth.nova.overloadalert.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the navigation screens within the application.
 *
 * This sealed class defines the available routes, labels, and icons associated with each screen
 * in the bottom navigation bar or navigation drawer. It encapsulates the data necessary
 * to navigate to and render the UI for specific app destinations.
 *
 * @property route The unique string identifier used by the navigation controller to navigate to this screen.
 * @property label The user-visible name of the screen, typically used in UI elements like tabs or titles.
 * @property icon The [ImageVector] icon representing the screen visually.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.History)
    object Graphs : Screen("graphs", "Graphs", Icons.Default.BarChart)
    object Plan : Screen("plan", "Plan", Icons.Default.CalendarMonth) 
    object Preferences : Screen("preferences", "Preferences", Icons.Default.Settings) 
}