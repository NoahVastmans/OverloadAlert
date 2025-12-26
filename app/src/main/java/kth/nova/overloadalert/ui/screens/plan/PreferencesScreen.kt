package kth.nova.overloadalert.ui.screens.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kth.nova.overloadalert.di.AppComponent

@Composable
fun PreferencesScreen(appComponent: AppComponent) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("User Preferences Screen Placeholder")
    }
}