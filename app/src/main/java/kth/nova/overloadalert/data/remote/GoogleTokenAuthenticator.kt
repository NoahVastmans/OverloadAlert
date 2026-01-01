package kth.nova.overloadalert.data.remote

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class GoogleTokenAuthenticator(
    private val googleAuthRepository: GoogleAuthRepository,
    private val googleTokenManager: GoogleTokenManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to run a suspending function from a synchronous one.
        // runBlocking is generally discouraged, but it is the standard way to do this in an Authenticator.
        val newAccessToken = runBlocking {
            googleAuthRepository.getNewAccessToken()
        }

        return if (newAccessToken != null) {
            googleTokenManager.saveAccessToken(newAccessToken)
            response.request().newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } else {
            Log.e("GoogleTokenAuthenticator", "Failed to refresh Google token.")
            null // Give up if we can't refresh the token
        }
    }
}