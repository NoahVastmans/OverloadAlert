package kth.nova.overloadalert.ui.screens.preferences

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Composable function for the Preferences screen, allowing users to configure training settings.
 *
 * This screen provides a UI for users to:
 * - Set the maximum number of runs per week.
 * - Choose a progression rate for training volume (e.g., Conservative, Moderate, Aggressive).
 * - Select preferred days for long runs.
 * - Specify forbidden days where no runs should be scheduled.
 * - (Premium only) Connect to Google Calendar for plan synchronization.
 *
 * The screen handles validation logic to ensure the selected preferences allow for a viable training plan.
 * It warns the user if the configuration is invalid (e.g., too many runs requested for the available days).
 * It also includes protection against unsaved changes via a discard dialog when navigating back.
 *
 * @param appComponent The [AppComponent] dependency injection container used to retrieve the ViewModel factory.
 * @param onNavigateBack Callback invoked when the user initiates a back navigation or successfully saves changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(appComponent: AppComponent, onNavigateBack: () -> Unit) {
    val viewModel: PreferencesViewModel = viewModel(factory = appComponent.preferencesViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var showInvalidPlanDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                viewModel.handleSignInResult(intent)
            }
        } else {
            Log.e("PreferencesScreen", "Google Sign-In failed or cancelled. ResultCode: ${result.resultCode}")
            result.data?.let { intent ->
                viewModel.handleSignInResult(intent)
            }
        }
    }

    LaunchedEffect(uiState.isPlanValid, uiState.isLoading) {
        if (!uiState.isPlanValid && !uiState.isLoading) {
            showInvalidPlanDialog = true
        }
    }

    BackHandler(enabled = true) {
        if (uiState.preferences != uiState.initialPreferences) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to go back? Any unsaved changes will be lost.") },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false; onNavigateBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") } }
        )
    }

    if (showInvalidPlanDialog) {
        AlertDialog(
            onDismissRequest = { showInvalidPlanDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Invalid Configuration") },
            text = { Text("The current settings prevent a valid training plan from being generated.\n\nPossible reasons:\n• More runs requested than available days\n• All preferred long run days are forbidden\n• Too many forbidden days selected") },
            confirmButton = { TextButton(onClick = { showInvalidPlanDialog = false }) { Text("OK") } }
        )
    }

    infoDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { infoDialogText = null },
            confirmButton = { TextButton(onClick = { infoDialogText = null }) { Text("OK") } },
            title = { Text("Preference Info") },
            text = { Text(text) }
        )
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        if (uiState.isPlanValid) {
                            viewModel.savePreferences(uiState.preferences, onNavigateBack)
                        } else {
                            showInvalidPlanDialog = true
                        }
                    },
                    enabled = uiState.isPlanValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isPlanValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                ) {
                    Icon(if (uiState.isPlanValid) Icons.Default.Check else Icons.Default.Warning, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = if (uiState.isPlanValid) "Save Preferences" else "Invalid Settings", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Training Preferences",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { infoDialogText = "These preferences control how your training plan is generated. They influence weekly volume, progression speed, and rest days." }
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Preferences Info")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (uiState.preferences.isPremium) {
                        PreferenceCard(title = "Google Calendar", onInfoClick = { infoDialogText = "Connect to sync your training plan with your Google Calendar." }) {
                            if (uiState.isGoogleConnected) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Connected to Google Calendar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    OutlinedButton(onClick = { viewModel.signOut() }) { Text("Disconnect") }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { googleSignInLauncher.launch(viewModel.getGoogleSignInIntent()) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Connect to Google Calendar") }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    PreferenceCard(title = "Maximum Runs Per Week: ${uiState.preferences.maxRunsPerWeek}", onInfoClick = { infoDialogText = "Limits how many runs you perform per week. This helps control fatigue and injury risk." }) {
                        Slider(
                            value = uiState.preferences.maxRunsPerWeek.toFloat(),
                            onValueChange = { viewModel.onPreferencesChanged(uiState.preferences.copy(maxRunsPerWeek = it.roundToInt())) },
                            valueRange = 1f..7f,
                            steps = 5
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    PreferenceCard(title = "Progression Rate", onInfoClick = { infoDialogText = "Controls how quickly training volume increases over time. Slower progression reduces injury risk." }) {
                        ProgressionRateSelector(uiState.preferences.progressionRate) { viewModel.onPreferencesChanged(uiState.preferences.copy(progressionRate = it)) }
                    }
                    Spacer(Modifier.height(16.dp))

                    PreferenceCard(title = "Preferred Long Run Days", onInfoClick = { infoDialogText = "Select days where long runs are preferred. The plan will try to schedule long runs on these days." }) {
                        DayOfWeekSelector(selectedDays = uiState.preferences.preferredLongRunDays) { day ->
                            val newDays = uiState.preferences.preferredLongRunDays.toMutableSet()
                            if (day in newDays) newDays.remove(day) else newDays.add(day)
                            viewModel.onPreferencesChanged(uiState.preferences.copy(preferredLongRunDays = newDays))
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    PreferenceCard(title = "Forbidden Run Days", onInfoClick = { infoDialogText = "Runs will never be scheduled on these days. Useful for work, rest, or recovery days." }) {
                        DayOfWeekSelector(selectedDays = uiState.preferences.forbiddenRunDays) { day ->
                            val newDays = uiState.preferences.forbiddenRunDays.toMutableSet()
                            if (day in newDays) newDays.remove(day) else newDays.add(day)
                            viewModel.onPreferencesChanged(uiState.preferences.copy(forbiddenRunDays = newDays))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun PreferenceCard(title: String, onInfoClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onInfoClick) { Icon(Icons.Default.Info, contentDescription = "Info") }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionRateSelector(selectedRate: ProgressionRate, onRateClick: (ProgressionRate) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressionRate.values().forEach { rate ->
            val isSelected = rate == selectedRate
            FilterChip(
                selected = isSelected,
                onClick = { onRateClick(rate) },
                label = { Text(rate.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekSelector(selectedDays: Set<DayOfWeek>, onDayClick: (DayOfWeek) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.values().forEach { day ->
            val isSelected = day in selectedDays
            FilterChip(
                selected = isSelected,
                onClick = { onDayClick(day) },
                label = { Text(day.name.take(3)) },
                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            )
        }
    }
}