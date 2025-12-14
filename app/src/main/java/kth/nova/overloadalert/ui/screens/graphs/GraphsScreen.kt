package kth.nova.overloadalert.ui.screens.graphs

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kth.nova.overloadalert.R
import kth.nova.overloadalert.di.AppComponent
import kotlin.math.roundToInt

@Composable
fun GraphsScreen(appComponent: AppComponent) {
    val viewModel: GraphsViewModel = viewModel(factory = appComponent.graphsViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val graphData = uiState.graphData

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Chart
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp) // Reduced outer padding
                .background(
                    color = androidx.compose.ui.graphics.Color.LightGray,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp), // Reduced inner padding
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading || graphData == null) {
                // Show a loading indicator or placeholder
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        CombinedChart(context).apply {
                            val mv = MyMarkerView(context, R.layout.custom_marker_view)
                            mv.chartView = this
                            marker = mv

                            setHighlightPerTapEnabled(true)
                            setDrawGridBackground(false)
                            setDrawBarShadow(false)
                            description.isEnabled = false
                            setBackgroundColor(Color.TRANSPARENT)
                            setExtraOffsets(5f, 5f, 5f, 15f) // Reduced offsets
                            axisRight.isEnabled = false
                            axisLeft.apply {
                                axisMinimum = 0f
                                textColor = Color.DKGRAY
                                gridColor = Color.LTGRAY
                                valueFormatter = IntValueFormatter() // Set integer formatter
                            }
                            legend.apply {
                                isEnabled = true
                                textColor = Color.DKGRAY
                                form = Legend.LegendForm.SQUARE
                                isWordWrapEnabled = true
                            }
                            drawOrder = arrayOf(
                                CombinedChart.DrawOrder.LINE,
                                CombinedChart.DrawOrder.BAR
                            )
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                granularity = 1f
                                textColor = Color.DKGRAY
                                gridColor = Color.LTGRAY
                            }
                        }
                    },
                    update = { chart ->
                        chart.xAxis.valueFormatter = DayAxisValueFormatter(graphData.dateLabels)

                        val barDataSet = BarDataSet(graphData.dailyLoadBars, "Daily Load").apply {
                            color = Color.BLUE
                            setDrawValues(false)
                        }
                        val barData = BarData(barDataSet).apply { barWidth = 0.4f }

                        val barWidth = barData.barWidth
                        val firstX = graphData.dailyLoadBars.firstOrNull()?.x ?: 0f
                        val lastX = graphData.dailyLoadBars.lastOrNull()?.x ?: 0f

                        chart.xAxis.axisMinimum = firstX - barWidth / 2f
                        chart.xAxis.axisMaximum = lastX + barWidth / 2f

                        val combinedData = CombinedData().apply {
                            setData(barData)
                            setData(createFillLineData(graphData.longestRunThresholdLine, barWidth))
                        }
                        chart.data = combinedData
                        chart.invalidate()
                    }
                )
            }
        }

        // Bottom Chart Placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Second graph placeholder")
        }
    }
}

private class DayAxisValueFormatter(private val labels: List<String>) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val index = value.toInt()
        return if (index >= 0 && index < labels.size) {
            labels[index]
        } else {
            ""
        }
    }
}

private class IntValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return (value / 1000f).roundToInt().toString()
    }
}

private fun createFillLineData(baseThresholdEntries: List<Entry>, barWidth: Float): LineData {
    if (baseThresholdEntries.isEmpty()) return LineData()

    val halfBar = barWidth / 2f
    val firstX = baseThresholdEntries.firstOrNull()?.x ?: 0f
    val lastX = baseThresholdEntries.lastOrNull()?.x ?: 0f

    val extendedBaseEntries = mutableListOf<Entry>().apply {
        if (baseThresholdEntries.isNotEmpty()) {
            add(Entry(firstX - halfBar, baseThresholdEntries.first().y))
            addAll(baseThresholdEntries)
            add(Entry(lastX + halfBar, baseThresholdEntries.last().y))
        }
    }

    val moderateRiskEntries = extendedBaseEntries.map { Entry(it.x, it.y / 1.1f * 1.3f) }
    val highRiskEntries = extendedBaseEntries.map { Entry(it.x, it.y / 1.1f * 2.0f) }

    val safeColor = Color.rgb(200, 230, 201)
    val moderateColor = Color.rgb(255, 183, 77)
    val highColor = Color.rgb(255, 205, 210)
    val veryHighColor = Color.rgb(198, 40, 40)

    val safeFill = createFillDataSet(extendedBaseEntries, "Safe Zone", safeColor, true)
    val moderateFill = createFillDataSet(extendedBaseEntries, "Moderate Risk", moderateColor, false)
    val highFill = createFillDataSet(moderateRiskEntries, "High Risk", highColor, false)
    val veryHighFill = createFillDataSet(highRiskEntries, "Very High Risk", veryHighColor, false)

    return LineData(listOf(safeFill, moderateFill, highFill, veryHighFill))
}

private fun createFillDataSet(entries: List<Entry>, label: String, color: Int, fillDown: Boolean): LineDataSet {
    return LineDataSet(entries, label).apply {
        setDrawFilled(true)
        fillColor = color
        fillAlpha = 150
        this.color = color
        lineWidth = 0.5f
        setDrawCircles(false)
        setDrawValues(false)
        isHighlightEnabled = false
        mode = LineDataSet.Mode.CUBIC_BEZIER
        fillFormatter = IFillFormatter { _, dataProvider ->
            if (fillDown) 0f else dataProvider.yChartMax
        }
    }
}
