package kth.nova.overloadalert.ui.screens.graphs

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
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

    // State for showing info dialogs
    var showTopChartInfo by remember { mutableStateOf(false) }
    var showBottomChartInfo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Chart
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.LightGray,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (uiState.isLoading || graphData == null) {
                // Loading...
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Distance vs. Longest Run Threshold",
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { showTopChartInfo = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }
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
                                setExtraOffsets(5f, 0f, 5f, 5f)
                                axisRight.isEnabled = false
                                axisLeft.apply {
                                    axisMinimum = 0f
                                    textColor = Color.DKGRAY
                                    gridColor = Color.LTGRAY
                                    valueFormatter = IntValueFormatter()
                                }
                                legend.apply {
                                    isEnabled = true
                                    textColor = Color.DKGRAY
                                    form = Legend.LegendForm.SQUARE
                                    isWordWrapEnabled = true
                                }
                                drawOrder = arrayOf(CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.BAR)
                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    granularity = 1f
                                    textColor = Color.DKGRAY
                                    gridColor = Color.TRANSPARENT
                                }
                            }
                        },
                        update = { chart ->
                            chart.xAxis.valueFormatter = DayAxisValueFormatter(graphData.dateLabels)
                            val barDataSet = BarDataSet(graphData.dailyLoadBars, "Daily Load").apply {
                                color = Color.rgb(52, 132, 212)
                                setDrawValues(false)
                            }
                            val barData = BarData(barDataSet).apply { barWidth = 0.4f }
                            chart.xAxis.axisMinimum = (graphData.dailyLoadBars.firstOrNull()?.x ?: 0f) - barData.barWidth / 2f
                            chart.xAxis.axisMaximum = (graphData.dailyLoadBars.lastOrNull()?.x ?: 0f) + barData.barWidth / 2f
                            chart.data = CombinedData().apply {
                                setData(barData)
                                setData(createLongestRunChartData(graphData.longestRunThresholdLine, barData.barWidth))
                            }
                            chart.invalidate()
                        }
                    )
                }
            }
        }

        // Bottom Chart
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.LightGray,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (uiState.isLoading || graphData == null) {
                // Loading...
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acute vs. Chronic Workload",
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { showBottomChartInfo = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            LineChart(context).apply {
                                val acwrMarker = MyMarkerView(context, R.layout.custom_marker_view, graphData.chronicLoadLine)
                                acwrMarker.chartView = this
                                marker = acwrMarker
                                setHighlightPerTapEnabled(true)

                                setDrawGridBackground(false)
                                description.isEnabled = false
                                setBackgroundColor(Color.TRANSPARENT)
                                setExtraOffsets(5f, 0f, 5f, 5f)
                                axisRight.isEnabled = false
                                axisLeft.apply {
                                    axisMinimum = 0f
                                    textColor = Color.DKGRAY
                                    gridColor = Color.LTGRAY
                                    valueFormatter = IntValueFormatter()
                                }
                                legend.apply {
                                    isEnabled = true
                                    textColor = Color.DKGRAY
                                    form = Legend.LegendForm.SQUARE
                                    isWordWrapEnabled = true
                                }
                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    granularity = 1f
                                    textColor = Color.DKGRAY
                                    gridColor = Color.TRANSPARENT
                                }
                            }
                        },
                        update = { chart ->
                            chart.xAxis.valueFormatter = DayAxisValueFormatter(graphData.dateLabels)
                            chart.xAxis.axisMinimum = graphData.acuteLoadLine.firstOrNull()?.x ?: 0f
                            chart.xAxis.axisMaximum = graphData.acuteLoadLine.lastOrNull()?.x ?: 0f
                            chart.data = createAcwrChartData(graphData.acuteLoadLine, graphData.chronicLoadLine)
                            chart.invalidate()
                        }
                    )
                }
            }
        }
    }
    // Top chart info dialog
    if (showTopChartInfo) {
        AlertDialog(
            onDismissRequest = { showTopChartInfo = false },
            confirmButton = {
                TextButton(onClick = { showTopChartInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text("Top Chart Info") },
            text = { Text("This chart shows daily distance vs. the longest run threshold. Bars represent daily load, and the colored zones show risk levels.") }
        )
    }

    // Bottom chart info dialog
    if (showBottomChartInfo) {
        AlertDialog(
            onDismissRequest = { showBottomChartInfo = false },
            confirmButton = {
                TextButton(onClick = { showBottomChartInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text("Bottom Chart Info") },
            text = { Text("This chart shows the acute vs chronic workload ratio (ACWR). Colored zones indicate different workload risk levels.") }
        )
    }
}

// --- Helper for Top Chart ---
private class DayAxisValueFormatter(private val labels: List<String>) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val index = value.toInt()
        return if (index >= 0 && index < labels.size) labels[index] else ""
    }
}

private class IntValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return (value / 1000f).roundToInt().toString()
    }
}

private fun createLongestRunChartData(baseThresholdEntries: List<Entry>, barWidth: Float): LineData {
    if (baseThresholdEntries.isEmpty()) return LineData()

    val halfBar = barWidth / 2f
    val firstX = baseThresholdEntries.firstOrNull()?.x ?: 0f
    val lastX = baseThresholdEntries.lastOrNull()?.x ?: 0f

    val extendedBaseEntries = mutableListOf<Entry>().apply {
        add(Entry(firstX - halfBar, baseThresholdEntries.first().y))
        addAll(baseThresholdEntries)
        add(Entry(lastX + halfBar, baseThresholdEntries.last().y))
    }

    val moderateRiskEntries = extendedBaseEntries.map { Entry(it.x, it.y / 1.1f * 1.3f) }
    val highRiskEntries = extendedBaseEntries.map { Entry(it.x, it.y / 1.1f * 2.0f) }

    val safeColor = Color.rgb(200, 230, 201)
    val moderateColor = Color.rgb(255, 249, 196)
    val highColor = Color.rgb(255, 205, 210)
    val veryHighColor = Color.rgb(200, 100, 100)

    val safeFill = createFillDataSet(extendedBaseEntries, "Safe Zone", safeColor, true)
    val moderateFill = createFillDataSet(extendedBaseEntries, "Moderate Risk", moderateColor, false)
    val highFill = createFillDataSet(moderateRiskEntries, "High Risk", highColor, false)
    val veryHighFill = createFillDataSet(highRiskEntries, "Very High Risk", veryHighColor, false)

    return LineData(listOf(safeFill, moderateFill, highFill, veryHighFill))
}

// --- Helper for Bottom Chart ---
private fun createAcwrChartData(acuteLoadEntries: List<Entry>, chronicLoadEntries: List<Entry>): LineData {
    if (chronicLoadEntries.isEmpty()) return LineData()

    val detrainingLine = chronicLoadEntries.map { Entry(it.x, it.y * 0.8f) }
    val overtrainingLine = chronicLoadEntries.map { Entry(it.x, it.y * 1.3f) }
    val highOvertrainingLine = chronicLoadEntries.map { Entry(it.x, it.y * 2.0f) }

    val blueColor = Color.rgb(179, 229, 252)  // De-training
    val greenColor = Color.rgb(200, 230, 201) // Optimal
    val yellowColor = Color.rgb(255, 249, 196) // Overtraining
    val redColor = Color.rgb(255, 205, 210)    // High Overtraining

    val detrainingFill = createFillDataSet(detrainingLine, "De-training/Recovery", blueColor, true)
    val optimalFill = createFillDataSet(detrainingLine, "Optimal", greenColor, false)
    val overtrainingFill = createFillDataSet(overtrainingLine, "Moderate Overtraining", yellowColor, false)
    val highOvertrainingFill = createFillDataSet(highOvertrainingLine, "High Overtraining", redColor, false)

    val acuteLoadLine = LineDataSet(acuteLoadEntries, "Acute Load").apply {
        color = Color.BLACK
        lineWidth = 2f
        setDrawCircles(false)
        setDrawValues(false)
        isHighlightEnabled = true
        mode = LineDataSet.Mode.HORIZONTAL_BEZIER
    }

    return LineData(listOf(detrainingFill, optimalFill, overtrainingFill, highOvertrainingFill, acuteLoadLine))
}

// --- Common Helper ---
private fun createFillDataSet(entries: List<Entry>, label: String, color: Int, fillDown: Boolean): LineDataSet {
    return LineDataSet(entries, label).apply {
        setDrawFilled(true)
        fillColor = color
        fillAlpha = 255
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
