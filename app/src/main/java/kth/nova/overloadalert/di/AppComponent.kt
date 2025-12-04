package kth.nova.overloadalert.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.local.AppDatabase
import kth.nova.overloadalert.data.remote.StravaApiService
import kth.nova.overloadalert.domain.usecases.AnalyzeRunDataUseCase
import kth.nova.overloadalert.ui.screens.home.HomeViewModel
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * A simple, manual dependency injection container.
 * In a real-world app, this would be replaced by a library like Hilt or Koin.
 */
class AppComponent(context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // For now, the base URL is a placeholder. This will be updated.
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.strava.com/api/v3/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val stravaApiService: StravaApiService by lazy {
        retrofit.create(StravaApiService::class.java)
    }

    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "overload-alert-db"
        ).build()
    }

    private val runningRepository: RunningRepository by lazy {
        RunningRepository(appDatabase.runDao(), stravaApiService)
    }

    private val analyzeRunDataUseCase: AnalyzeRunDataUseCase by lazy {
        AnalyzeRunDataUseCase()
    }

    // ViewModel Factory
    val homeViewModelFactory: ViewModelProvider.Factory by lazy {
        HomeViewModel.provideFactory(runningRepository, analyzeRunDataUseCase)
    }
}