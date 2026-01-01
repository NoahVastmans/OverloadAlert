package kth.nova.overloadalert.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kth.nova.overloadalert.data.AuthRepository
import kth.nova.overloadalert.data.LocalDateAdapter
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.data.local.AppDatabase
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.data.remote.GoogleCalendarApiService
import kth.nova.overloadalert.data.remote.GoogleCalendarRepository
import kth.nova.overloadalert.data.remote.GoogleTokenManager
import kth.nova.overloadalert.data.remote.StravaApiService
import kth.nova.overloadalert.data.remote.StravaAuthService
import kth.nova.overloadalert.data.remote.TokenAuthenticator
import kth.nova.overloadalert.domain.plan.WeeklyTrainingPlanGenerator
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.PlanRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kth.nova.overloadalert.domain.usecases.CalendarSyncService
import kth.nova.overloadalert.domain.usecases.HistoricalDataAnalyzer
import kth.nova.overloadalert.ui.screens.graphs.GraphsViewModel
import kth.nova.overloadalert.ui.screens.history.HistoryViewModel
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import kth.nova.overloadalert.ui.screens.login.AuthViewModel
import kth.nova.overloadalert.ui.screens.plan.PlanViewModel
import kth.nova.overloadalert.ui.screens.preferences.PreferencesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppComponent(context: Context) {

    // Create an application-level coroutine scope
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val moshi = Moshi.Builder()
        .add(LocalDateAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    // --- Token Managers ---
    val tokenManager by lazy { TokenManager(context) }
    val googleTokenManager by lazy { GoogleTokenManager(context) }

    // --- Strava Network Stack ---
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

    // --- Google Calendar Network Stack ---
    // We create a separate OkHttpClient/Retrofit for Google Calendar because it uses a different base URL and token.
    private val googleOkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = googleTokenManager.getAccessToken()
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

    private val googleRetrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/") // Base URL for Google APIs
        .client(googleOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val googleCalendarApiService: GoogleCalendarApiService by lazy {
        googleRetrofit.create(GoogleCalendarApiService::class.java)
    }

    // --- Database ---
    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "overload-alert-db"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
    }

    // --- Storage ---
    val planStorage: PlanStorage by lazy { PlanStorage(context, moshi) }

    // --- Repositories ---
    val authRepository: AuthRepository by lazy { AuthRepository(stravaAuthService, tokenManager) }
    val googleAuthRepository: GoogleAuthRepository by lazy { GoogleAuthRepository(context, googleTokenManager) }
    val runningRepository: RunningRepository by lazy { RunningRepository(appDatabase.runDao(), stravaApiService, tokenManager) }
    val googleCalendarRepository: GoogleCalendarRepository by lazy { GoogleCalendarRepository(googleCalendarApiService) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(context) }

    // --- Use Cases ---
    val analyzeRunData: AnalyzeRunData by lazy { AnalyzeRunData() }
    private val historicalDataAnalyzer: HistoricalDataAnalyzer by lazy { HistoricalDataAnalyzer() }
    private val weeklyTrainingPlanGenerator: WeeklyTrainingPlanGenerator by lazy { WeeklyTrainingPlanGenerator() }
    private val calendarSyncService: CalendarSyncService by lazy { 
        CalendarSyncService(appDatabase.calendarSyncDao(), googleCalendarRepository, googleAuthRepository) 
    }

    // --- Composite Repositories ---
    val analysisRepository: AnalysisRepository by lazy { AnalysisRepository(runningRepository, analyzeRunData, appScope) }
    val planRepository: PlanRepository by lazy {
        PlanRepository(
            analysisRepository, 
            preferencesRepository, 
            planStorage, 
            runningRepository, 
            historicalDataAnalyzer, 
            weeklyTrainingPlanGenerator, 
            analyzeRunData, 
            calendarSyncService,
            appScope
        )
    }

    // --- ViewModel Factories ---
    val authViewModelFactory: ViewModelProvider.Factory by lazy { AuthViewModel.provideFactory(authRepository, tokenManager) }
    val homeViewModelFactory: ViewModelProvider.Factory by lazy { HomeViewModel.provideFactory(analysisRepository, runningRepository, tokenManager) }
    val historyViewModelFactory: ViewModelProvider.Factory by lazy { 
        HistoryViewModel.provideFactory(runningRepository, analysisRepository) 
    }
    val graphsViewModelFactory: ViewModelProvider.Factory by lazy { GraphsViewModel.provideFactory(analysisRepository) }
    val planViewModelFactory: ViewModelProvider.Factory by lazy {
        PlanViewModel.provideFactory(planRepository)
    }
    val preferencesViewModelFactory: ViewModelProvider.Factory by lazy {
        // Updated Factory creation for new dependencies
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return PreferencesViewModel(preferencesRepository, googleAuthRepository, googleTokenManager) as T
            }
        }
    }

    init {
        // Eagerly launch a collector for the plan. This acts as a permanent subscriber,
        // keeping the upstream flow active and ensuring the plan is always ready.
        planRepository.latestPlan.launchIn(appScope)
    }
}