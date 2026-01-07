package kth.nova.overloadalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kth.nova.overloadalert.ui.main.MainScreen
import kth.nova.overloadalert.ui.screens.login.AuthViewModel
import kth.nova.overloadalert.ui.screens.login.LoginScreen
import kth.nova.overloadalert.ui.theme.OverloadAlertTheme
import kth.nova.overloadalert.worker.SyncWorker
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

/**
 * The entry point of the OverloadAlert application.
 *
 * This activity serves as the root container for the UI, managing the navigation between the
 * login screen and the main application screen based on the user's authentication state.
 *
 * Key responsibilities include:
 * - **Dependency Injection:** initializes the [AuthViewModel] using the application's Dagger component.
 * - **UI Composition:** Sets up the Jetpack Compose content, switching between [LoginScreen] and [MainScreen].
 * - **Deep Link Handling:** Processes OAuth callback intents (via `handleIntent`) to exchange authorization codes for tokens.
 * - **Background Work:** Schedules periodic background synchronization using [WorkManager] via [scheduleBackgroundSync] upon successful authentication.
 * - **Permissions:** Requests notification permissions on Android 13+ (Tiramisu) via [askForNotificationPermission].
 */
class MainActivity : ComponentActivity() {

    private val appComponent by lazy { (application as OverloadAlertApplication).appComponent }

    private val authViewModel by viewModels<AuthViewModel> { appComponent.authViewModelFactory }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // You can optionally handle the case where the user denies the permission.
    }

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

        authViewModel.isAuthenticated
            .onEach { isAuthenticated ->
                if (isAuthenticated) {
                    scheduleBackgroundSync()
                    askForNotificationPermission()
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.toString().startsWith("overloadalert://localhost")) {
                uri.getQueryParameter("code")?.let { code ->
                    authViewModel.exchangeCodeForToken(code)
                    intent.data = null
                }
            }
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            1, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "strava_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}