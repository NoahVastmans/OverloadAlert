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
import kth.nova.overloadalert.data.remote.TokenAuthenticator
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlanGenerator
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kth.nova.overloadalert.ui.screens.graphs.GraphsViewModel
import kth.nova.overloadalert.ui.screens.history.HistoryViewModel
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import kth.nova.overloadalert.ui.screens.login.AuthViewModel
import kth.nova.overloadalert.ui.screens.plan.PlanViewModel
import kth.nova.overloadalert.ui.screens.preferences.PreferencesViewModel
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppComponent(context: Context) {

    private val moshi = Moshi.Builder().build()

    val tokenManager by lazy { TokenManager(context) }

    private val unauthenticatedRetrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/api/v3/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val stravaAuthService: StravaAuthService by lazy {
        unauthenticatedRetrofit.create(StravaAuthService::class.java)
    }

    private val authenticator by lazy { TokenAuthenticator(tokenManager, stravaAuthService) }

    private val authenticatedOkHttpClient by lazy {
        OkHttpClient.Builder()
            .authenticator(authenticator)
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

    private val stravaApiService: StravaApiService by lazy {
        authenticatedRetrofit.create(StravaApiService::class.java)
    }

    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "overload-alert-db"
        ).build()
    }

    val authRepository: AuthRepository by lazy { AuthRepository(stravaAuthService, tokenManager) }
    val runningRepository: RunningRepository by lazy { RunningRepository(appDatabase.runDao(), stravaApiService, tokenManager) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(context) }

    val analyzeRunData: AnalyzeRunData by lazy { AnalyzeRunData() }
    private val historicalDataAnalyzer: HistoricalDataAnalyzer by lazy { HistoricalDataAnalyzer() }
    private val weeklyTrainingPlanGenerator: WeeklyTrainingPlanGenerator by lazy { WeeklyTrainingPlanGenerator() }

    val analysisRepository: AnalysisRepository by lazy { AnalysisRepository(runningRepository, analyzeRunData) }

    val authViewModelFactory: ViewModelProvider.Factory by lazy { AuthViewModel.provideFactory(authRepository, tokenManager) }
    val homeViewModelFactory: ViewModelProvider.Factory by lazy { HomeViewModel.provideFactory(analysisRepository, runningRepository, tokenManager) }
    val historyViewModelFactory: ViewModelProvider.Factory by lazy { HistoryViewModel.provideFactory(runningRepository, analyzeRunData) }
    val graphsViewModelFactory: ViewModelProvider.Factory by lazy { GraphsViewModel.provideFactory(analysisRepository) }
    val planViewModelFactory: ViewModelProvider.Factory by lazy {
        PlanViewModel.provideFactory(analysisRepository, preferencesRepository, historicalDataAnalyzer, weeklyTrainingPlanGenerator, runningRepository, analyzeRunData)
    }
    val preferencesViewModelFactory: ViewModelProvider.Factory by lazy {
        PreferencesViewModel.provideFactory(preferencesRepository)
    }
}