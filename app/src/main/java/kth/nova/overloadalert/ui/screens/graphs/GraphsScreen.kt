package kth.nova.overloadalert.ui.screens.graphs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.models.BarData
import co.yml.charts.ui.barchart.models.BarPlotData
import co.yml.charts.ui.barchart.models.GroupBar
import co.yml.charts.ui.combinedchart.CombinedChart
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle

@Composable
fun GraphsScreen() {
    // --- Bar Chart Data ---
    val originalPoints = listOf(
        Point(1f, 10f), Point(2f, 12f), Point(3f, 8f), Point(4f, 15f),
        Point(5f, 11f), Point(6f, 13f), Point(7f, 9f)
    )
    val pointsWithDummies = listOf(Point(0f, 0f)) + originalPoints + listOf(Point(8f, 0f))
    val barData = pointsWithDummies.mapIndexed { index, point ->
        val isDummyPoint = (index == 0 || index == pointsWithDummies.lastIndex)
        BarData(
            point = point,
            label = if (isDummyPoint) "" else "D${point.x.toInt()}",
            color = if (isDummyPoint) Color.Transparent else MaterialTheme.colorScheme.primary
        )
    }
    val groupBarData = barData.map { GroupBar(label = it.label, barList = listOf(it)) }
    val barPlotData = BarPlotData(groupBarList = groupBarData)

    // --- Line Chart Data (for the threshold line) ---
    val thresholdValue = 15f
    val linePoints = listOf(Point(0f, thresholdValue), Point(8f, thresholdValue))
    val linePlotData = LinePlotData(
        lines = listOf(
            Line(
                dataPoints = linePoints,
                lineStyle = LineStyle(color = MaterialTheme.colorScheme.error)
            )
        )
    )

    // --- Axis Data ---
    val maxVal = 20f
    val xAxisData = AxisData.Builder()
        .axisLabelColor(MaterialTheme.colorScheme.onBackground)
        .steps(barData.size - 1)
        .labelData { i -> barData[i].label }
        .build()
    val yAxisData = AxisData.Builder()
        .steps(4)
        .axisLabelColor(MaterialTheme.colorScheme.onBackground)
        .labelData { i -> (i * (maxVal / 4)).toInt().toString() }
        .build()

    Box(
        modifier = Modifier
            .height(300.dp)
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        CombinedChart(
            modifier = Modifier.fillMaxSize(),
            plots = listOf(barPlotData, linePlotData),
            xAxisData = xAxisData,
            yAxisData = yAxisData
        )
    }
}