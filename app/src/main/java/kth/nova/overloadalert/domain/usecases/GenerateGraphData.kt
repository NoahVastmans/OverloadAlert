package kth.nova.overloadalert.domain.usecases

import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import kth.nova.overloadalert.data.local.Run
import kth.nova.overloadalert.domain.model.GraphData
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class GenerateGraphData(private val analyzeRunData: AnalyzeRunData) {

    operator fun invoke(runs: List<Run>): GraphData {
        if (runs.isEmpty()) return GraphData()

        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)
        val relevantRuns = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysAgo) }

        val dailyLoadBars = mutableListOf<BarEntry>()
        val longestRunThresholdLine = mutableListOf<Entry>()

        for (i in 0..29) {
            val date = thirtyDaysAgo.plusDays(i.toLong() + 1)
            val runsOnThisDay = relevantRuns.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate() == date }
            val dailyLoad = runsOnThisDay.sumOf { it.distance.toDouble() }.toFloat()
            dailyLoadBars.add(BarEntry(i.toFloat(), dailyLoad))

            val runsBeforeThisDay = runs.filter { OffsetDateTime.parse(it.startDateLocal).toLocalDate().isBefore(date) }
            val thirtyDaysBeforeThisDay = date.minusDays(30)
            val relevantPrecedingRuns = runsBeforeThisDay.filter {
                OffsetDateTime.parse(it.startDateLocal).toLocalDate().isAfter(thirtyDaysBeforeThisDay)
            }
            
            val stableBaseline = analyzeRunData.getStableLongestRun(relevantPrecedingRuns)
            val threshold = stableBaseline * 1.1f
            longestRunThresholdLine.add(Entry(i.toFloat(), threshold))
        }

        return GraphData(dailyLoadBars, longestRunThresholdLine)
    }
}