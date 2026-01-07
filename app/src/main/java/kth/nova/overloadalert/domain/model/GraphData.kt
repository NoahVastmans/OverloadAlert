package kth.nova.overloadalert.domain.model

import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry

/**
 * A data class that encapsulates the prepared data series required to render visualizations
 * on the graphs screen.
 *
 * This class aggregates data points for multiple chart types, specifically formatted for
 * use with the MPAndroidChart library.
 *
 * @property dailyLoadBars A list of [BarEntry] objects representing the daily workload values for the bar chart (Chart 1).
 * @property longestRunThresholdLine A list of [Entry] objects defining the threshold line for the longest run, used as a reference in Chart 1.
 * @property dateLabels A list of date strings corresponding to the x-axis indices of [dailyLoadBars], used for axis formatting.
 * @property acuteLoadLine A list of [Entry] objects representing the acute (short-term) workload trend for the line chart (Chart 2).
 * @property chronicLoadLine A list of [Entry] objects representing the chronic (long-term) workload trend for the line chart (Chart 2).
 */
data class GraphData(
    // Data for Chart 1
    val dailyLoadBars: List<BarEntry> = emptyList(),
    val longestRunThresholdLine: List<Entry> = emptyList(),
    val dateLabels: List<String> = emptyList(),

    // Data for Chart 2
    val acuteLoadLine: List<Entry> = emptyList(),
    val chronicLoadLine: List<Entry> = emptyList()
)