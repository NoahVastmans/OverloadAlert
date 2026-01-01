package kth.nova.overloadalert.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleAuthRepository(
    private val context: Context,
    private val googleTokenManager: GoogleTokenManager
) {

    // Scopes for Google Calendar API
    private val scopeCalendar = Scope("https://www.googleapis.com/auth/calendar")
    private val scopeCalendarEvents = Scope("https://www.googleapis.com/auth/calendar.events")

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(scopeCalendar, scopeCalendarEvents)
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(intent: Intent): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.await()
            Log.d("GoogleAuthRepo", "Sign-in successful for: ${account.email}")
            handleAccount(account)
            true
        } catch (e: ApiException) {
            Log.e("GoogleAuthRepo", "SignIn Result Failed: ${e.statusCode}", e)
            false
        } catch (e: Exception) {
            Log.e("GoogleAuthRepo", "SignIn Handler Error", e)
            false
        }
    }

    private suspend fun handleAccount(account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            try {
                val scopes = "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events"
                val accountObj = account.account ?: return@withContext
                
                val token = GoogleAuthUtil.getToken(context, accountObj, scopes)
                Log.d("GoogleAuthRepo", "Access Token retrieved successfully")
                
                googleTokenManager.saveAccessToken(token)
                
            } catch (e: Exception) {
                Log.e("GoogleAuthRepo", "Failed to get token during handleAccount", e)
            }
        }
    }

    suspend fun getNewAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount() ?: return@withContext null
        try {
            val scopes = "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events"
            val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)
            Log.d("GoogleAuthRepo", "Access Token refreshed successfully")
            return@withContext token
        } catch (e: UserRecoverableAuthException) {
            Log.e("GoogleAuthRepo", "Could not refresh token, user action required.", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e("GoogleAuthRepo", "Error refreshing access token", e)
            return@withContext null
        }
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            googleTokenManager.clearTokens()
        } catch (e: Exception) {
            Log.e("GoogleAuthRepo", "Error during sign out", e)
        }
    }
    
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}

// Custom await extension to avoid dependency on play-services-tasks-ktx
private suspend fun <T> Task<T>.await(): T {
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw kotlinx.coroutines.CancellationException("Task $this was cancelled normally.")
            } else {
                result as T
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener {
            val e = exception
            if (e == null) {
                if (isCanceled) {
                    cont.cancel()
                } else {
                    cont.resume(result as T)
                }
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}