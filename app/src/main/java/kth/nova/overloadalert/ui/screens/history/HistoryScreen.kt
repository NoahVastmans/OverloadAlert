package kth.nova.overloadalert.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.model.CombinedRisk
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Composable function that renders the screen displaying the history of analyzed runs.
 *
 * This screen observes the UI state from the [HistoryViewModel] and displays a list of past runs
 * along with their calculated risk assessments. It handles different UI states including loading,
 * error messages, and empty history scenarios.
 *
 * Features:
 * - Displays a "Run History" title with an information button that explains the screen's purpose.
 * - Shows a [CircularProgressIndicator] while data is loading.
 * - Displays error messages if data fetching fails.
 * - Renders a [LazyColumn] of [RunHistoryItem]s when data is available.
 * - Provides an info dialog explaining the screen context.
 * - Supports clicking on individual risk tags to show a detailed risk assessment dialog.
 *
 * @param viewModel The [HistoryViewModel] that provides the [HistoryUiState] and manages the business logic for this screen.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRiskDialog by remember { mutableStateOf<CombinedRisk?>(null) }

    Scaffold(
            contentWindowInsets = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
            ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- Top Title Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Run History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "History Info"
                    )
                }
            }
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                uiState.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = uiState.errorMessage!!,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                uiState.analyzedRuns.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No run history found.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(uiState.analyzedRuns) { analyzedRun ->
                            RunHistoryItem(
                                analyzedRun = analyzedRun,
                                onRiskClick = { showRiskDialog = it }
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                        }
                    }
                }
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
            title = { Text("Run History Info") },
            text = { Text("This screen shows your past analyzed runs, with the combined risk assessment based on your fitness at that point in time.") }
        )
    }
    showRiskDialog?.let { risk ->
        AlertDialog(
            onDismissRequest = { showRiskDialog = null },
            title = { Text(risk.title) },
            text = { Text(risk.message) },
            confirmButton = {
                TextButton(onClick = { showRiskDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun RunHistoryItem(analyzedRun: AnalyzedRun, onRiskClick: (CombinedRisk) -> Unit) {
    val run = analyzedRun.run
    val risk = analyzedRun.risk

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val runDate = OffsetDateTime.parse(run.startDateLocal).format(dateFormatter)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Date and Risk Tag
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                Text(text = runDate, fontSize = 16.sp)
                RiskTag(risk = risk, onClick = { onRiskClick(risk) })
            }

            Spacer(Modifier.width(16.dp))

            // Distance and Time
            Column(horizontalAlignment = Alignment.End) {
                val distanceInKm = run.distance / 1000f
                Text(text = String.format("%.2f km", distanceInKm), fontSize = 16.sp)

                val hours = run.movingTime / 3600
                val minutes = (run.movingTime % 3600) / 60
                val seconds = run.movingTime % 60
                Text(
                    text = String.format("%d:%02d:%02d", hours, minutes, seconds),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun RiskTag(risk: CombinedRisk, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(risk.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = risk.title.uppercase(),
            color = Color.White,
            fontSize = 10.sp
        )
    }
}
