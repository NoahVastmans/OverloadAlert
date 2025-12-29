package kth.nova.overloadalert.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.domain.plan.DailyPlan
import kth.nova.overloadalert.domain.plan.ProgressionRate
import kth.nova.overloadalert.domain.plan.RiskPhase
import kth.nova.overloadalert.domain.plan.RunType
import java.time.LocalDate

@Composable
fun PlanScreen(appComponent: AppComponent) {
    val viewModel: PlanViewModel = viewModel(factory = appComponent.planViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    val plan = uiState.trainingPlan
    val planTitle = PlanTitle(plan?.riskPhase, plan?.progressionRate)


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }

            uiState.trainingPlan != null -> {
                val today = LocalDate.now().dayOfWeek
                val todayIndex = plan!!.days.indexOfFirst { it.dayOfWeek == today }
                val rotatedDays =
                    if (todayIndex != -1) {
                        plan.days.subList(todayIndex, plan.days.size) +
                                plan.days.subList(0, todayIndex)
                    } else {
                        plan.days
                    }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = planTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Plan Info",
                                tint = Color.Gray
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(rotatedDays) { dailyPlan ->
                            DailyPlanItem(
                                plan = dailyPlan,
                                isToday = dailyPlan.dayOfWeek == today,
                                isRestWeek = dailyPlan.isRestWeek
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            } else -> {
                Text("Not enough data to generate a plan.")
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
            title = { Text("Training Plan Info") },
            text = {
                Text(
                    "This screen shows your personalized training plan. "
                            + "Days are ordered starting from today. "
                            + "The plan adapts based on your recent training load and recovery."
                )
            }
        )
    }
}

@Composable
fun DailyPlanItem(plan: DailyPlan, isToday: Boolean, isRestWeek: Boolean) {
    val displayRunType = if (isRestWeek) {
        when (plan.runType) {
            RunType.LONG,
            RunType.MODERATE -> RunType.EASY
            else -> plan.runType
        }
    } else {
        plan.runType
    }

    val (icon, color, label) = when (displayRunType) {
        RunType.LONG -> Triple(Icons.Default.Straight, Color(0xFFE57373), "Long Run")
        RunType.MODERATE -> Triple(Icons.Default.TrendingUp, Color(0xFFFFA726), "Moderate Run")
        RunType.EASY -> Triple(Icons.Default.DirectionsRun, Color(0xFF81C784), "Easy Run")
        RunType.REST -> Triple(Icons.Default.Hotel, Color.Gray, "Rest Day")
    }

    val dayLabel = if (isToday) "Today" else plan.dayOfWeek.toString()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = label, tint = color)
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(dayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
            if (plan.runType != RunType.REST) {
                Text(
                    text = String.format("%.1f km", plan.plannedDistance / 1000f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun ProgressionRate.label(): String = when (this) {
    ProgressionRate.FAST -> "Fast Building"
    ProgressionRate.SLOW -> "Slow Building"
    ProgressionRate.RETAIN -> "Retaining"
}

fun PlanTitle(riskPhase: RiskPhase?, progressionRate: ProgressionRate?): String {
    val rateLabel = progressionRate?.label() ?: "Standard"

    return when (riskPhase) {
        null -> "Optimal $rateLabel Training Plan"
        RiskPhase.DELOAD -> "Recovery after overtraining"
        RiskPhase.REBUILDING -> "Rebuilding after detraining"
        RiskPhase.COOLDOWN ->
            if (progressionRate == ProgressionRate.FAST)
                "Building slowly after recovery/rebuilding"
            else
                "Optimal $rateLabel Training Plan"
        RiskPhase.LONG_RUN_LIMITED -> "Optimal $rateLabel Training Plan with limited long run"
    }
}
