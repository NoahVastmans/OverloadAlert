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
        // We need 7 extra days for EWMA warm-up, so we start from 37 days ago.
        val calculationStartDate = today.minusDays(37)

        val rawDailyLoads = mutableListOf<Float>()
        val rawThresholds = mutableListOf<Float>()
        val dateLabels = mutableListOf<String>()

        // First, calculate the raw, unsmoothed data for the last 37 days
        for (i in 0..36) {
            val date = calculationStartDate.plusDays(i.toLong() + 1)
            val runsOnThisDay = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate() == date }
            rawDailyLoads.add(runsOnThisDay.sumOf { it.distance.toDouble() }.toFloat())

            val runsBeforeThisDay = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(date) }
            val thirtyDaysBeforeThisDay = date.minusDays(30)
            val relevantPrecedingRuns = runsBeforeThisDay.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeThisDay)
            }
            
            val stableBaseline = analyzeRunData.getStableLongestRun(relevantPrecedingRuns)
            rawThresholds.add(stableBaseline * 1.1f)

            // Also generate the date label for this day
            dateLabels.add(date.dayOfMonth.toString())
        }

        // Now, smooth the raw threshold data using EWMA over the full 37-day period
        val smoothedThresholds = analyzeRunData.calculateEwma(rawThresholds, 7) // 7-day smoothing span

        // Take only the last 30 days for the UI
        val finalDailyLoads = rawDailyLoads.takeLast(30)
        val finalSmoothedThresholds = smoothedThresholds.takeLast(30)
        val finalDateLabels = dateLabels.takeLast(30)

        val dailyLoadBars = finalDailyLoads.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }
        val longestRunThresholdLine = finalSmoothedThresholds.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        return GraphData(dailyLoadBars, longestRunThresholdLine, finalDateLabels)
    }
}