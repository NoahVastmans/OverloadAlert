package kth.nova.overloadalert.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kth.nova.overloadalert.data.remote.StravaAuthRepository
import kth.nova.overloadalert.domain.usecases.CalendarSyncService
import kth.nova.overloadalert.data.adapter.LocalDateAdapter
import kth.nova.overloadalert.data.adapter.CombinedRiskAdapter
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.remote.StravaTokenManager
import kth.nova.overloadalert.data.local.AnalysisStorage
import kth.nova.overloadalert.data.local.AppDatabase
import kth.nova.overloadalert.data.local.PlanStorage
import kth.nova.overloadalert.data.remote.GoogleAuthRepository
import kth.nova.overloadalert.data.remote.GoogleCalendarApiService
import kth.nova.overloadalert.data.remote.GoogleCalendarRepository
import kth.nova.overloadalert.data.remote.GoogleTokenAuthenticator
import kth.nova.overloadalert.data.remote.GoogleTokenManager
import kth.nova.overloadalert.data.remote.StravaApiService
import kth.nova.overloadalert.data.remote.StravaAuthService
import kth.nova.overloadalert.data.remote.StravaTokenAuthenticator
import kth.nova.overloadalert.domain.usecases.WeeklyTrainingPlanGenerator
import kth.nova.overloadalert.domain.repository.AnalysisRepository
import kth.nova.overloadalert.domain.repository.PlanRepository
import kth.nova.overloadalert.domain.repository.PreferencesRepository
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
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

/**
 * A manual dependency injection component responsible for creating, holding, and managing
 * the singletons and scoped dependencies for the entire application.
 *
 * This component initializes core services such as:
 * - Network clients (OkHttp, Retrofit) for Strava and Google APIs.
 * - Local storage (Room database, SharedPreferences wrappers).
 * - Repositories for data access.
 * - Use cases and domain logic generators.
 * - ViewModel factories for UI consumption.
 *
 * It acts as a service locator, allowing the rest of the application to retrieve
 * configured dependencies lazily.
 *
 * @property context The application context used for initializing system services and database/file storage.
 */
class AppComponent(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val moshi = Moshi.Builder()
        .add(LocalDateAdapter())
        .add(CombinedRiskAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    val stravaTokenManager by lazy { StravaTokenManager(context) }
    val googleTokenManager by lazy { GoogleTokenManager(context) }

    private val unauthenticatedRetrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/api/v3/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val stravaAuthService: StravaAuthService by lazy {
        unauthenticatedRetrofit.create(StravaAuthService::class.java)
    }

    private val authenticator by lazy { StravaTokenAuthenticator(stravaTokenManager, stravaAuthService) }

    private val authenticatedOkHttpClient by lazy {
        OkHttpClient.Builder()
            .authenticator(authenticator)
            .addInterceptor { chain ->
                val token = stravaTokenManager.getAccessToken()
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

    val googleAuthRepository: GoogleAuthRepository by lazy { GoogleAuthRepository(context, googleTokenManager) }

    private val googleTokenAuthenticator by lazy { GoogleTokenAuthenticator(googleAuthRepository, googleTokenManager) }

    private val googleOkHttpClient by lazy {
        OkHttpClient.Builder()
            .authenticator(googleTokenAuthenticator)
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
        .baseUrl("https://www.googleapis.com/calendar/v3/")
        .client(googleOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val googleCalendarApiService: GoogleCalendarApiService by lazy {
        googleRetrofit.create(GoogleCalendarApiService::class.java)
    }

    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "overload-alert-db"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
    }

    val planStorage: PlanStorage by lazy { PlanStorage(context, moshi) }
    val analysisStorage by lazy { AnalysisStorage(context, moshi) }

    val stravaAuthRepository: StravaAuthRepository by lazy { StravaAuthRepository(stravaAuthService, stravaTokenManager) }
    val runningRepository: RunningRepository by lazy { RunningRepository(appDatabase.runDao(), stravaApiService, stravaTokenManager) }
    val googleCalendarRepository: GoogleCalendarRepository by lazy { GoogleCalendarRepository(googleCalendarApiService) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(context, googleTokenManager, appScope) }

    val analyzeRunData: AnalyzeRunData by lazy { AnalyzeRunData() }
    private val historicalDataAnalyzer: HistoricalDataAnalyzer by lazy { HistoricalDataAnalyzer() }
    private val weeklyTrainingPlanGenerator: WeeklyTrainingPlanGenerator by lazy { WeeklyTrainingPlanGenerator() }
    private val calendarSyncService: CalendarSyncService by lazy { 
        CalendarSyncService(appDatabase.calendarSyncDao(), googleCalendarRepository, googleAuthRepository, planStorage) 
    }

    val analysisRepository: AnalysisRepository by lazy { AnalysisRepository(runningRepository, analyzeRunData, analysisStorage, appScope) }
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

    val authViewModelFactory: ViewModelProvider.Factory by lazy { AuthViewModel.provideFactory(stravaAuthRepository, stravaTokenManager) }
    val homeViewModelFactory: ViewModelProvider.Factory by lazy { HomeViewModel.provideFactory(analysisRepository, runningRepository, stravaTokenManager) }
    val historyViewModelFactory: ViewModelProvider.Factory by lazy { 
        HistoryViewModel.provideFactory(runningRepository, analysisRepository) 
    }
    val graphsViewModelFactory: ViewModelProvider.Factory by lazy { GraphsViewModel.provideFactory(analysisRepository) }
    val planViewModelFactory: ViewModelProvider.Factory by lazy {
        PlanViewModel.provideFactory(planRepository, preferencesRepository)
    }
    val preferencesViewModelFactory: ViewModelProvider.Factory by lazy {
        PreferencesViewModel.provideFactory(
            preferencesRepository,
            googleAuthRepository
        )
    }

    init {
        planRepository.latestPlan.launchIn(appScope)
    }
}