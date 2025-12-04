package kth.nova.overloadalert.data.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

@AndroidEntryPoint
class RedirectActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null) {
            val authService = AuthorizationService(this)
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                coroutineScope.launch {
                    if (tokenResponse != null) {
                        val tokenData = TokenData(
                            accessToken = tokenResponse.accessToken.orEmpty(),
                            refreshToken = tokenResponse.refreshToken.orEmpty(),
                            accessTokenExpirationTime = tokenResponse.accessTokenExpirationTime ?: 0
                        )
                        authRepository.saveTokens(tokenData)
                    } else {
                        // Handle error
                        exception?.printStackTrace()
                    }
                    // TODO: Navigate to the main activity
                    finish()
                }
            }
        } else {
            ex?.printStackTrace()
            finish()
        }
    }
}