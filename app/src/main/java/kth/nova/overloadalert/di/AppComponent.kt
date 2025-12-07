package kth.nova.overloadalert.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.squareup.moshi.Moshi
import kth.nova.overloadalert.data.AuthRepository
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.data.local.AppDatabase
import kth.nova.overloadalert.data.remote.StravaApiService
import kth.nova.overloadalert.data.remote.StravaAuthService
import kth.nova.overloadalert.domain.usecases.AnalyzeRunDataUseCase
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import kth.nova.overloadalert.ui.screens.login.AuthViewModel
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppComponent(context: Context) {

    private val moshi = Moshi.Builder()
        .build()

    private val tokenManager by lazy { TokenManager(context) }

    private val authenticatedOkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = tokenManager.getAccessToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    private val authenticatedRetrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/api/v3/")
        .client(authenticatedOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val unauthenticatedRetrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/api/v3/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val stravaApiService: StravaApiService by lazy {
        authenticatedRetrofit.create(StravaApiService::class.java)
    }

    private val stravaAuthService: StravaAuthService by lazy {
        unauthenticatedRetrofit.create(StravaAuthService::class.java)
    }

    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "overload-alert-db"
        ).build()
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(stravaAuthService, tokenManager)
    }

    private val runningRepository: RunningRepository by lazy {
        RunningRepository(appDatabase.runDao(), stravaApiService)
    }

    private val analyzeRunDataUseCase: AnalyzeRunDataUseCase by lazy {
        AnalyzeRunDataUseCase()
    }

    val homeViewModelFactory: ViewModelProvider.Factory by lazy {
        HomeViewModel.provideFactory(runningRepository, analyzeRunDataUseCase)
    }

    val authViewModelFactory: ViewModelProvider.Factory by lazy {
        AuthViewModel.provideFactory(authRepository)
    }
}