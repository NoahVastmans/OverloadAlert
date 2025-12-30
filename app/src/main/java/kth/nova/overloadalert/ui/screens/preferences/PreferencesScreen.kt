package kth.nova.overloadalert.ui.screens.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.domain.plan.ProgressionRate
import java.time.DayOfWeek
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(appComponent: AppComponent, onNavigateBack: () -> Unit) {
    val viewModel: PreferencesViewModel = viewModel(factory = appComponent.preferencesViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var showInvalidPlanDialog by remember { mutableStateOf(false) }

    // Check plan validity when uiState changes
    LaunchedEffect(uiState.isPlanValid, uiState.isLoading) {
        if (!uiState.isPlanValid && !uiState.isLoading) {
            showInvalidPlanDialog = true
        }
    }

    BackHandler(enabled = true) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to go back? Any unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInvalidPlanDialog) {
        AlertDialog(
            onDismissRequest = { showInvalidPlanDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Invalid Configuration") },
            text = { 
                Text(
                    "The current settings prevent a valid training plan from being generated.\n\n" +
                    "Possible reasons:\n" +
                    "• All preferred long run days are forbidden\n" +
                    "• Too many forbidden days selected for the chosen maximal days per week"
                ) 
            },
            confirmButton = {
                TextButton(onClick = { showInvalidPlanDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    infoDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { infoDialogText = null },
            confirmButton = {
                TextButton(onClick = { infoDialogText = null }) {
                    Text("OK")
                }
            },
            title = { Text("Preference Info") },
            text = { Text(text) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Training Preferences",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                infoDialogText =
                                    "These preferences control how your training plan is generated. " +
                                            "They influence weekly volume, progression speed, and rest days."
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Preferences Info",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // ---------- SAVE BUTTON ----------
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        if (uiState.isPlanValid) {
                            viewModel.savePreferences(
                                uiState.preferences,
                                onNavigateBack
                            )
                        } else {
                            showInvalidPlanDialog = true
                        }
                    },
                    enabled = uiState.isPlanValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isPlanValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    if (uiState.isPlanValid) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Save Preferences",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Invalid Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) {paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                PreferenceHeader(
                    title = "Maximum Runs Per Week: ${uiState.preferences.maxRunsPerWeek}",
                    onInfoClick = {
                        infoDialogText =
                            "Limits how many runs you perform per week. " +
                                    "This helps control fatigue and injury risk."
                    }
                )

                Slider(
                    value = uiState.preferences.maxRunsPerWeek.toFloat(),
                    onValueChange = {
                        viewModel.onPreferencesChanged(
                            uiState.preferences.copy(
                                maxRunsPerWeek = it.roundToInt()
                            )
                        )
                    },
                    valueRange = 1f..7f,
                    steps = 5
                )

                Spacer(Modifier.height(24.dp))

                PreferenceHeader(
                    title = "Progression Rate",
                    onInfoClick = {
                        infoDialogText =
                            "Controls how quickly training volume increases over time. " +
                                    "Slower progression reduces injury risk."
                    }
                )

                ProgressionRateSelector(uiState.preferences.progressionRate) {
                    viewModel.onPreferencesChanged(
                        uiState.preferences.copy(progressionRate = it)
                    )
                }

                Spacer(Modifier.height(24.dp))

                PreferenceHeader(
                    title = "Preferred Long Run Days",
                    onInfoClick = {
                        infoDialogText =
                            "Select days where long runs are preferred. " +
                                    "The plan will try to schedule long runs on these days."
                    }
                )

                DayOfWeekSelector(
                    selectedDays = uiState.preferences.preferredLongRunDays,
                    onDayClick = { day ->
                        val newDays =
                            uiState.preferences.preferredLongRunDays.toMutableSet()
                        if (day in newDays) newDays.remove(day) else newDays.add(day)
                        viewModel.onPreferencesChanged(
                            uiState.preferences.copy(
                                preferredLongRunDays = newDays
                            )
                        )
                    }
                )

                Spacer(Modifier.height(24.dp))

                PreferenceHeader(
                    title = "Forbidden Run Days",
                    onInfoClick = {
                        infoDialogText =
                            "Runs will never be scheduled on these days. " +
                                    "Useful for work, rest, or recovery days."
                    }
                )

                DayOfWeekSelector(
                    selectedDays = uiState.preferences.forbiddenRunDays,
                    onDayClick = { day ->
                        val newDays =
                            uiState.preferences.forbiddenRunDays.toMutableSet()
                        if (day in newDays) newDays.remove(day) else newDays.add(day)
                        viewModel.onPreferencesChanged(
                            uiState.preferences.copy(
                                forbiddenRunDays = newDays
                            )
                        )
                    }
                )
                Spacer(Modifier.height(80.dp))
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
private fun ProgressionRateSelector(selectedRate: ProgressionRate, onRateClick: (ProgressionRate) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressionRate.values().forEach { rate ->
            val isSelected = selectedRate == rate
            FilterChip(
                selected = isSelected,
                onClick = { onRateClick(rate) },
                label = { Text(rate.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
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

@Composable
fun PreferenceHeader(
    title: String,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color.Gray
            )
        }
    }
}
