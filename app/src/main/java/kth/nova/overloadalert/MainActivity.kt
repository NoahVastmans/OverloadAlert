package kth.nova.overloadalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kth.nova.overloadalert.data.auth.AuthViewModel
import kth.nova.overloadalert.data.auth.LoginScreen
import kth.nova.overloadalert.ui.home.HomeScreen
import kth.nova.overloadalert.ui.theme.OverloadAlertTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OverloadAlertTheme {
                val authState by authViewModel.authState.collectAsState()
                when (authState) {
                    is kth.nova.overloadalert.data.auth.AuthState.Authenticated -> HomeScreen()
                    is kth.nova.overloadalert.data.auth.AuthState.Unauthenticated -> LoginScreen()
                }
            }
        }
    }
}