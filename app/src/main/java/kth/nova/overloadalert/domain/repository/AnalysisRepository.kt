package kth.nova.overloadalert.domain.repository

import kth.nova.overloadalert.domain.model.CachedAnalysis
import kth.nova.overloadalert.domain.model.UiAnalysisData
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the operations for managing analysis data.
 * This belongs in the Domain layer.
 */
interface AnalysisRepository {
    val latestAnalysis: StateFlow<UiAnalysisData?>
    val latestCachedAnalysis: StateFlow<CachedAnalysis?>
}
