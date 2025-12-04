package kth.nova.overloadalert.data.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val tokenData = runBlocking { authRepository.tokenData.first() }
        val request = chain.request()

        val newRequest = if (tokenData != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer ${tokenData.accessToken}")
                .build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}