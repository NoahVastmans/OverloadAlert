package kth.nova.overloadalert.ui.screens.graphs

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kth.nova.overloadalert.di.AppComponent

@Composable
fun GraphsScreen(appComponent: AppComponent) {
    val viewModel: GraphsViewModel = viewModel(factory = appComponent.graphsViewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val graphData = uiState.graphData

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(
                color = androidx.compose.ui.graphics.Color.LightGray,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp), // inner padding for axes
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoading || graphData == null) {
            // Show a loading indicator or placeholder
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CombinedChart(context).apply {
                        // Interaction setup
                        setHighlightPerTapEnabled(true) // Enable tap-to-show value

                        // Common setup
                        setDrawGridBackground(false)
                        setDrawBarShadow(false)
                        description.isEnabled = false
                        setBackgroundColor(Color.TRANSPARENT)
                        setExtraOffsets(16f, 16f, 16f, 24f)
                        axisRight.isEnabled = false
                        axisLeft.apply {
                            axisMinimum = 0f
                            textColor = Color.DKGRAY
                            gridColor = Color.LTGRAY
                        }
                        legend.apply {
                            isEnabled = true
                            textColor = Color.DKGRAY
                            form = Legend.LegendForm.LINE
                        }
                        drawOrder = arrayOf(
                            CombinedChart.DrawOrder.LINE, // fills behind
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
                    val barDataSet = BarDataSet(graphData.dailyLoadBars, "Daily Load").apply {
                        color = Color.BLUE
                        setDrawValues(false) // Hide values by default
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
}

private fun createFillLineData(lineEntries: List<Entry>, barWidth: Float): LineData {
    val halfBar = barWidth / 2f
    val firstX = lineEntries.firstOrNull()?.x ?: 0f
    val lastX = lineEntries.lastOrNull()?.x ?: 0f

    val extendedEntries = mutableListOf<Entry>().apply {
        if (lineEntries.isNotEmpty()) {
            add(Entry(firstX - halfBar, lineEntries.first().y))
            addAll(lineEntries)
            add(Entry(lastX + halfBar, lineEntries.last().y))
        }
    }

    val upperFill = LineDataSet(extendedEntries, "Safe Zone").apply {
        setDrawFilled(true)
        fillColor = Color.GREEN
        fillAlpha = 60
        color = Color.TRANSPARENT
        lineWidth = 0f
        setDrawCircles(false)
        setDrawValues(false)
        isHighlightEnabled = false // Make this layer un-clickable
        fillFormatter = IFillFormatter { _, dataProvider -> dataProvider.yChartMax }
    }

    val lowerFill = LineDataSet(extendedEntries, "Risk Zone").apply {
        setDrawFilled(true)
        fillColor = Color.RED
        fillAlpha = 60
        color = Color.TRANSPARENT
        lineWidth = 0f
        setDrawCircles(false)
        setDrawValues(false)
        isHighlightEnabled = false // Make this layer un-clickable
        fillFormatter = IFillFormatter { _, dataProvider -> 0f }
    }

    return LineData(listOf(upperFill, lowerFill))
}