package kth.nova.overloadalert.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.errorMessage != null -> {
                    Text(text = uiState.errorMessage!!)
                }
                uiState.runAnalysis != null -> {
                    val analysis = uiState.runAnalysis!!
                    Text("Longest Run (30d): ${analysis.longestRunLast30Days / 1000f} km")
                    Text("Acute Load (7d): ${analysis.acuteLoad / 1000f} km")
                    Text("Chronic Load (avg 3w): ${analysis.chronicLoad / 1000f} km")
                }
            }
        }
    }
}