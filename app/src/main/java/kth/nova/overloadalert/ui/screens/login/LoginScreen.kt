package kth.nova.overloadalert.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kth.nova.overloadalert.R

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---------- TITLE ----------
            Text(
                text = "Connect with Strava",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ---------- EXPLANATION ----------
            Text(
                text = "Overload Alert analyzes your training load, recovery, and injury risk using your Strava activity data.\n\n" +
                        "The app cannot function without a Strava connection, as all insights and recommendations are based entirely on your recorded runs.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ---------- ACTION BUTTON ----------
            Image(
                painter = painterResource(id = R.drawable.btn_strava_connect_orange),
                contentDescription = "Connect with Strava",
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .clickable {
                        if (uiState.authUrl.isNotBlank()) {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                uiState.authUrl.toUri()
                            )
                            context.startActivity(intent)
                        }
                    }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MaterialTheme {
        Scaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Connect with Strava",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Overload Alert analyzes your training load, recovery, and injury risk using your Strava activity data.\n\n" +
                            "The app cannot function without a Strava connection, as all insights and recommendations are based entirely on your recorded runs.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Image(
                    painter = painterResource(id = R.drawable.btn_strava_connect_orange),
                    contentDescription = "Connect with Strava",
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp)
                        .clickable { }
                )
            }
        }
    }
}

