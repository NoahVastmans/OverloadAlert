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
        // Removed requestServerAuthCode. 
        // For client-side flow (GoogleAuthUtil), we do not need to request a server code.
        // The app's SHA-1 fingerprint registered in the Google Cloud Console is sufficient 
        // to authorize the Android Client ID.
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
            // Error 10 (DEVELOPER_ERROR) usually means SHA-1 mismatch in Google Cloud Console
            Log.e("GoogleAuthRepo", "SignIn Result Failed. Status Code: ${e.statusCode}", e)
            false
        } catch (e: Exception) {
            Log.e("GoogleAuthRepo", "SignIn Handler Error", e)
            e.printStackTrace()
            false
        }
    }

    private suspend fun handleAccount(account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch the token directly using GoogleAuthUtil (Synchronous call, must be on BG thread)
                val scopes = "oauth2:https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events"
                val accountObj = account.account ?: return@withContext
                
                val token = GoogleAuthUtil.getToken(context, accountObj, scopes)
                Log.d("GoogleAuthRepo", "Access Token retrieved successfully")
                
                // Store the token
                googleTokenManager.saveAccessToken(token)
                
            } catch (e: UserRecoverableAuthException) {
                // This exception is thrown when the user needs to grant consent (again) or take action.
                Log.e("GoogleAuthRepo", "UserRecoverableAuthException: ${e.message}")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e("GoogleAuthRepo", "Failed to get token", e)
                e.printStackTrace()
            }
        }
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            googleTokenManager.clearTokens()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}

// Extension function to replace kotlinx-coroutines-play-services functionality
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