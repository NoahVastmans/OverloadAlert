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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kth.nova.overloadalert.domain.model.RiskLevel
import kth.nova.overloadalert.domain.model.RunAnalysis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                            val minutesAgo = (System.currentTimeMillis() - it) / 60000
                            Text(
                                text = "Last synced: $minutesAgo min ago",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
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
            if (uiState.isLoading && uiState.runAnalysis == null) {
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
    // Risk Assessment Card
    analysis.riskAssessment?.let { assessment ->
        val cardColor = when (assessment.riskLevel) {
            RiskLevel.NONE -> Color(0xFFC8E6C9) // Light Green
            RiskLevel.MODERATE -> Color(0xFFFFF9C4) // Light Yellow
            RiskLevel.HIGH -> Color(0xFFFFE0B2) // Light Orange
            RiskLevel.VERY_HIGH -> Color(0xFFFFCDD2) // Light Red
        }
        val textColor = when (assessment.riskLevel) {
            RiskLevel.NONE -> Color(0xFF2E7D32)
            RiskLevel.MODERATE -> Color(0xFFF9A825)
            RiskLevel.HIGH -> Color(0xFFEF6C00)
            RiskLevel.VERY_HIGH -> Color(0xFFC62828)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = assessment.riskLevel.name.replace("_", " "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(8.dp))
                Text(text = assessment.message, style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // Data Details Card
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last 30 Days Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            DataRow("Safe Longest Run", String.format("%.2f km", analysis.longestRunLast30Days / 1000f))
            DataRow("Acute Load (7d)", String.format("%.2f km", analysis.acuteLoad / 1000f))
            DataRow("Chronic Load (avg 3w)", String.format("%.2f km", analysis.chronicLoad / 1000f))
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