package kth.nova.overloadalert.data.remote

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * An OkHttp [Authenticator] implementation responsible for handling automatic Google API token refreshing.
 *
 * This authenticator intercepts 401 Unauthorized responses from Google API requests. When triggered,
 * it attempts to fetch a fresh access token using the [GoogleAuthRepository] in a blocking manner
 * (via [runBlocking]), saves the new token to the [GoogleTokenManager], and retries the original request
 * with the new "Authorization" header.
 *
 * If the token refresh fails (returns null), the authenticator gives up and returns null, effectively
 * letting the original 401 response propagate to the caller.
 *
 * @property googleAuthRepository The repository used to fetch new access tokens from the remote Google API.
 * @property googleTokenManager The manager used to persist the newly acquired access token.
 */
class GoogleTokenAuthenticator(
    private val googleAuthRepository: GoogleAuthRepository,
    private val googleTokenManager: GoogleTokenManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
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