package kth.nova.overloadalert.ui.screens.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.ui.screens.preferences.PreferencesViewModel
import java.time.DayOfWeek
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(appComponent: AppComponent, onNavigateBack: () -> Unit) {
    val viewModel: PreferencesViewModel = viewModel(factory = appComponent.preferencesViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    // Intercept back presses to show the confirmation dialog
    BackHandler(enabled = true) {
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to go back? Any unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onNavigateBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Preferences") },
                actions = {
                    IconButton(onClick = { 
                        viewModel.savePreferences(uiState.preferences, onNavigateBack)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save Preferences")
                    }
                }
            )
        }
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(16.dp)
            ) {
                // Max runs per week slider
                PreferenceItem("Maximum Runs Per Week: ${uiState.preferences.maxRunsPerWeek}") {
                    Slider(
                        value = uiState.preferences.maxRunsPerWeek.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.onPreferencesChanged(
                                uiState.preferences.copy(maxRunsPerWeek = newValue.roundToInt())
                            )
                        },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Preferred long run days selector
                PreferenceItem("Preferred Long Run Days") {
                    DayOfWeekSelector(
                        selectedDays = uiState.preferences.preferredLongRunDays,
                        onDayClick = { day ->
                            val newDays = uiState.preferences.preferredLongRunDays.toMutableSet()
                            if (newDays.contains(day)) newDays.remove(day) else newDays.add(day)
                            viewModel.onPreferencesChanged(uiState.preferences.copy(preferredLongRunDays = newDays))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Forbidden run days selector
                PreferenceItem("Forbidden Run Days") {
                    DayOfWeekSelector(
                        selectedDays = uiState.preferences.forbiddenRunDays,
                        onDayClick = { day ->
                            val newDays = uiState.preferences.forbiddenRunDays.toMutableSet()
                            if (newDays.contains(day)) newDays.remove(day) else newDays.add(day)
                            viewModel.onPreferencesChanged(uiState.preferences.copy(forbiddenRunDays = newDays))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceItem(title: String, content: @Composable () -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekSelector(selectedDays: Set<DayOfWeek>, onDayClick: (DayOfWeek) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            val isSelected = selectedDays.contains(day)
            FilterChip(
                selected = isSelected,
                onClick = { onDayClick(day) },
                label = { Text(day.name.take(3)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                }
            )
        }
    }
}