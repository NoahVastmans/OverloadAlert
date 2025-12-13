package kth.nova.overloadalert.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kth.nova.overloadalert.domain.model.AnalyzedRun
import kth.nova.overloadalert.domain.model.RiskLevel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage!!,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.analyzedRuns.isEmpty() -> {
                    Text(
                        text = "No run history found.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.analyzedRuns) { analyzedRun ->
                            RunHistoryItem(analyzedRun = analyzedRun)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RunHistoryItem(analyzedRun: AnalyzedRun) {
    val run = analyzedRun.run
    val riskLevel = analyzedRun.singleRunRiskAssessment.riskLevel

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val runDate = OffsetDateTime.parse(run.startDateLocal).format(dateFormatter)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date and Risk Tag
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
            Text(text = runDate, fontSize = 16.sp)
            RiskTag(riskLevel = riskLevel)
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

@Composable
fun RiskTag(riskLevel: RiskLevel) {
    val tagColor = when (riskLevel) {
        RiskLevel.NONE -> Color(0xFF2E7D32) // Dark Green
        RiskLevel.MODERATE -> Color(0xFFF9A825) // Amber
        RiskLevel.HIGH -> Color(0xFFEF6C00)     // Orange
        RiskLevel.VERY_HIGH -> Color(0xFFC62828) // Red
    }

    val text = if (riskLevel == RiskLevel.NONE) "NO RISK" else riskLevel.name.replace("_", " ")

    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tagColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}
