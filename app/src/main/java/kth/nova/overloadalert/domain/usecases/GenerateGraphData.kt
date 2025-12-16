package kth.nova.overloadalert.domain.usecases

import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.GraphData
import java.time.LocalDate
import java.time.OffsetDateTime

class GenerateGraphData(private val analyzeRunData: AnalyzeRunData) {

    operator fun invoke(runs: List<Run>): GraphData {
        if (runs.isEmpty()) return GraphData()

        val today = LocalDate.now()
        // Use the same logic as AnalyzeRunData: find the earliest run to start calculations.
        val calculationStartDate = runs.minOf { OffsetDateTime.parse(it.startDateLocal).toLocalDate() }

        // Use the shared helper function to get a full series of daily loads from the very beginning.
        val dailyLoads = analyzeRunData.createDailyLoadSeries(runs, calculationStartDate, today)

        // --- Generate Data for Chart 2 (ACWR) ---
        val capValue = analyzeRunData.getIqrCapValue(dailyLoads)
        val cappedDailyLoads = dailyLoads.map { it.coerceAtMost(capValue) }

        val acuteLoadSeries = analyzeRunData.calculateRollingSum(dailyLoads, 7)
        val cappedAcuteLoadSeries = analyzeRunData.calculateRollingSum(cappedDailyLoads, 7)
        val chronicLoadSeries = analyzeRunData.calculateEwma(cappedAcuteLoadSeries, 28)

        // --- Generate Data for Chart 1 (Longest Run) ---
        val longestRunThresholds = (0 until dailyLoads.size).map { i ->
            val date = calculationStartDate.plusDays(i.toLong())
            val thirtyDaysBefore = date.minusDays(30)
            val precedingRuns = runs.filter { run ->
                val runDate = OffsetDateTime.parse(run.startDateLocal).toLocalDate()
                runDate.isAfter(thirtyDaysBefore) && !runDate.isAfter(date)
            }
            analyzeRunData.getStableLongestRun(precedingRuns) * 1.1f
        }
        val smoothedLongestRunThresholds = analyzeRunData.calculateEwma(longestRunThresholds, 7)
        
        // --- Prepare final 30-day data for the UI ---
        val finalDateLabels = (0..29).map { today.minusDays(29L - it).dayOfMonth.toString() }

        // Chart 1 data
        val dailyLoadBars = dailyLoads.takeLast(30).mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
        val longestRunThresholdLine = smoothedLongestRunThresholds.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        
        // Chart 2 data
        val acuteLoadLine = acuteLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val chronicLoadLine = chronicLoadSeries.takeLast(30).mapIndexed { index, value -> Entry(index.toFloat(), value) }

        return GraphData(dailyLoadBars, longestRunThresholdLine, finalDateLabels, acuteLoadLine, chronicLoadLine)
    }
}