package kth.nova.overloadalert.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kth.nova.overloadalert.data.RunningRepository
import kth.nova.overloadalert.data.TokenManager
import kth.nova.overloadalert.domain.usecases.AnalyzeRunData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val runningRepository: RunningRepository,
    private val analyzeRunData: AnalyzeRunData,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Immediately load data from the local database
        loadDataFromDb()

        // Then, trigger a background sync to check if data is stale
        val lastSync = tokenManager.getLastSyncTimestamp()
        val isCacheStale = (System.currentTimeMillis() - lastSync) > 3600 * 1000 // 1 hour
        if (isCacheStale) {
            refreshData()
        }
    }

    private fun loadDataFromDb() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val runs = runningRepository.getRunsForAnalysis()
                val analysis = analyzeRunData(runs)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        runAnalysis = analysis,
                        lastSyncTime = tokenManager.getLastSyncTimestamp()
                    )
                }
            } catch (e: Exception) {
                // This should rarely happen if we are just reading from the DB
                _uiState.update { it.copy(isLoading = false, syncErrorMessage = "Failed to load data from database.") }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) } // Show loading indicator during sync
            val syncResult = runningRepository.syncRuns()
            if (syncResult.isSuccess) {
                // After a successful sync, reload the fresh data from the database
                loadDataFromDb()
            } else {
                // If sync fails, stop loading and show a temporary error message.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        syncErrorMessage = "Sync failed. No internet connection?"
                    )
                }
            }
        }
    }
    
    fun onSyncErrorShown() {
        _uiState.update { it.copy(syncErrorMessage = null) }
    }

    companion object {
        fun provideFactory(
            runningRepository: RunningRepository,
            analyzeRunData: AnalyzeRunData,
            tokenManager: TokenManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(runningRepository, analyzeRunData, tokenManager) as T
            }
        }
    }
}