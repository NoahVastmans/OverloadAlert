package kth.nova.overloadalert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            uiState.runRecommendation?.let { recommendation ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Today's Recommendation: ${recommendation.today / 1000} km")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Next 7 Days: ${recommendation.nextSevenDays / 1000} km")
                }
            }

            uiState.workloadRatio?.let {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Workload Ratio: ${it.ratio}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Risk Level: ${it.riskLevel}", color = when(it.riskLevel) {
                        kth.nova.overloadalert.domain.model.WorkloadRatio.RiskLevel.LOW -> Color.Green
                        kth.nova.overloadalert.domain.model.WorkloadRatio.RiskLevel.OPTIMAL -> Color.Green
                        kth.nova.overloadalert.domain.model.WorkloadRatio.RiskLevel.HIGH -> Color.Yellow
                        kth.nova.overloadalert.domain.model.WorkloadRatio.RiskLevel.VERY_HIGH -> Color.Red
                    })
                }
            }
        }
    }
}