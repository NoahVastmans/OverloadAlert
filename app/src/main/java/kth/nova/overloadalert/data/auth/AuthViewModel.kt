package kth.nova.overloadalert.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.tokenData.map {
        if (it != null) AuthState.Authenticated else AuthState.Unauthenticated
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    fun login() {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://www.strava.com/oauth/authorize"),
            Uri.parse("https://www.strava.com/oauth/token")
        )

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            // Replace with your actual client ID
            "YOUR_CLIENT_ID",
            ResponseTypeValues.CODE,
            Uri.parse("overloadalert://callback")
        ).setScope("activity:read_all").build()

        val authService = AuthorizationService(context)
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        authIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(authIntent)
    }
}

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}