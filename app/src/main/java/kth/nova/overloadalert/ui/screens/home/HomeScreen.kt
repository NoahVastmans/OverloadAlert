package kth.nova.overloadalert.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kth.nova.overloadalert.domain.model.RunAnalysis
import kotlinx.coroutines.delay
import kth.nova.overloadalert.domain.model.CombinedRisk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigateToPreferences: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L) // Update every minute
            currentTime = System.currentTimeMillis()
        }
    }

    uiState.syncErrorMessage?.let {
        LaunchedEffect(it) {
            snackbarHostState.showSnackbar(message = it)
            viewModel.onSyncErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Overload Alert")
                        uiState.lastSyncTime?.let {
                            if (it > 0) {
                                val minutesAgo = (currentTime - it) / 60000
                                val syncText = when {
                                    minutesAgo < 1 -> "Last synced: just now"
                                    minutesAgo == 1L -> "Last synced: 1 min ago"
                                    else -> "Last synced: $minutesAgo min ago"
                                }
                                Text(
                                    text = syncText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPreferences) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
                    }
                    IconButton(onClick = { showInfoDialog = true }) { // <-- Info button
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.runAnalysis != null) {
                RunAnalysisCard(uiState.runAnalysis!!)
            } else {
                Text("No data available. Please sync with Strava.")
            }
        }
    }
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("Home Screen Info") },
            text = { Text("This screen shows your latest overload analysis. Use the refresh button to update data and settings to configure your preferences.") }
        )
    }
}

@Composable
fun RunAnalysisCard(analysis: RunAnalysis) {
    val combinedRisk = analysis.combinedRisk

    // Main Combined Risk Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = combinedRisk.color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = convertCombinedRisk(combinedRisk).first,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = combinedRisk.color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = convertCombinedRisk(combinedRisk).second,
                style = MaterialTheme.typography.bodyMedium,
                color = combinedRisk.color.copy(alpha = 0.9f)
            )
        }
    }
    Spacer(Modifier.height(24.dp))

    // Prescriptive Metrics Card
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            DataRow("Maximal Run Today", String.format("%.2f km", analysis.recommendedTodaysRun / 1000f))
            DataRow("Minimal Run Today", String.format("%.2f km", analysis.minRecommendedTodaysRun / 1000f))
            DataRow("Maximal Volume This Week", String.format("%.2f km", analysis.maxWeeklyLoad / 1000f))
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 16.sp, color = Color.Gray)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

fun convertCombinedRisk(combinedRisk: CombinedRisk): Pair<String, AnnotatedString> {
    // 1. Extract load-related title (ACWR part)
    val loadTitle = combinedRisk.title
        .split("·", "-", "|")
        .first()
        .trim()

    // 2. Map load title to ACWR-only message
    val acwrMessage: AnnotatedString = when {
        loadTitle.contains("Low", ignoreCase = true) ->
            buildAnnotatedString {
                append("Your recent training load is low, indicating detraining/recovery and reduced stimulation of muscles, tendons, and bones. While short-term injury risk may appear low, detrained tissues tolerate sudden increases poorly.\n \n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Guidance:") }
                append(" Rebuild volume gradually and avoid rapid increases in weekly distance or intensity.")
            }

        loadTitle.contains("Optimal", ignoreCase = true) ->
            buildAnnotatedString {
                append("Your recent training load is well balanced, indicating good adaptation to training stress. This range is generally associated with the lowest injury risk when progression is controlled.\n \n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Guidance:") }
                append(" Maintain steady progression and avoid unnecessary spikes in volume or intensity.")
            }

        loadTitle.contains("Elevated", ignoreCase = true) ->
            buildAnnotatedString {
                append("Your recent training load is above the optimal range, suggesting accumulating fatigue. Injury risk increases when elevated load is maintained without sufficient recovery.\n \n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Guidance:") }
                append(" Reduce training load slightly and prioritize recovery before further progression.")
            }

        loadTitle.contains("High", ignoreCase = true) ->
            buildAnnotatedString {
                append("Your recent training load is far above optimal, indicating significant accumulated fatigue and elevated injury risk. Continued loading at this level greatly increases the chance of injury.\n \n")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Guidance:") }
                append(" Significantly reduce training load and allow focused recovery before resuming progression.")
            }

        else ->
            buildAnnotatedString { append("Your recent training load could not be classified. Use caution when progressing training volume.") }
    }

    return loadTitle to acwrMessage
}
