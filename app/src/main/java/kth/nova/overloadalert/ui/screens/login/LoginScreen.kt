package kth.nova.overloadalert.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                if (uiState.authUrl.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.authUrl))
                    context.startActivity(intent)
                }
            }) {
                Text("Connect with Strava")
            }
        }
    }
}