package kth.nova.overloadalert

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kth.nova.overloadalert.di.AppComponent
import kth.nova.overloadalert.ui.main.MainScreen
import kth.nova.overloadalert.ui.screens.login.AuthViewModel
import kth.nova.overloadalert.ui.screens.login.LoginScreen
import kth.nova.overloadalert.ui.theme.OverloadAlertTheme

class MainActivity : ComponentActivity() {

    private val appComponent by lazy { AppComponent(applicationContext) }

    private val authViewModel by viewModels<AuthViewModel> { appComponent.authViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            OverloadAlertTheme {
                val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

                if (isAuthenticated) {
                    MainScreen(appComponent = appComponent)
                } else {
                    LoginScreen(viewModel = authViewModel)
                }
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Parses the intent for a Strava authorization code and exchanges it for a token.
     */
    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.toString().startsWith("overloadalert://localhost")) {
                uri.getQueryParameter("code")?.let { code ->
                    authViewModel.exchangeCodeForToken(code)
                    // Clear the intent data so we don't process this code again on activity recreation.
                    intent.data = null
                }
            }
        }
    }
}
