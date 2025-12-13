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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.sp
import kth.nova.overloadalert.domain.model.RunAnalysis
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

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
                    IconButton(onClick = { viewModel.clearAllData() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Database")
                    }
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
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
                text = combinedRisk.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = combinedRisk.color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = combinedRisk.message,
                style = MaterialTheme.typography.bodyMedium,
                color = combinedRisk.color.copy(alpha = 0.8f)
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