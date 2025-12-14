package kth.nova.overloadalert.domain.model

import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry

/**
 * A data class to hold the prepared data series for the graphs screen.
 */
data class GraphData(
    val dailyLoadBars: List<BarEntry> = emptyList(),
    val longestRunThresholdLine: List<Entry> = emptyList()
)