package kth.nova.overloadalert.ui.screens.graphs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.graphics.Color
import androidx.compose.foundation.background


import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape

import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider


@Composable
fun GraphsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(
                color = androidx.compose.ui.graphics.Color.LightGray,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp) // inner padding for axes
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CombinedChart(context).apply {

                    // --- Appearance ---
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

                    // --- Bar & Line data ---
                    val barData = createBarData()
                    val lineEntries = listOf(
                        Entry(0f, 50f),
                        Entry(1f, 50f),
                        Entry(2f, 50f),
                        Entry(3f, 60f),
                        Entry(4f, 60f)
                    )

                    val barWidth = barData.barWidth
                    val dataSet = barData.dataSets.first() as BarDataSet
                    val firstX = dataSet.getEntryForIndex(0).x
                    val lastX = dataSet.getEntryForIndex(dataSet.entryCount - 1).x

                    // --- X axis ---
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        axisMinimum = firstX - barWidth / 2f
                        axisMaximum = lastX + barWidth / 2f
                        textColor = Color.DKGRAY
                        gridColor = Color.LTGRAY
                    }

                    // --- Combine data ---
                    val combinedData = CombinedData().apply {
                        setData(barData)
                        setData(createFillLineData(lineEntries, barWidth))
                    }

                    data = combinedData
                    invalidate()
                }
            }
        )
    }
}

private fun createBarData(): BarData {
    val entries = listOf(
        BarEntry(0f, 40f),
        BarEntry(1f, 55f),
        BarEntry(2f, 30f),
        BarEntry(3f, 70f),
        BarEntry(4f, 60f)
    )

    return BarData(BarDataSet(entries, "Bar Values").apply { color = Color.BLUE }).apply {
        barWidth = 0.4f
    }
}

// Create only the fills, line is transparent (not drawn)
private fun createFillLineData(lineEntries: List<Entry>, barWidth: Float): LineData {
    val halfBar = barWidth / 2f
    val firstX = lineEntries.first().x
    val lastX = lineEntries.last().x

    val extendedEntries = mutableListOf<Entry>().apply {
        add(Entry(firstX - halfBar, lineEntries.first().y))
        addAll(lineEntries)
        add(Entry(lastX + halfBar, lineEntries.last().y))
    }

    val upperFill = LineDataSet(extendedEntries, "").apply {
        setDrawFilled(true)
        fillColor = Color.GREEN
        fillAlpha = 60
        color = Color.TRANSPARENT
        lineWidth = 0f
        setDrawCircles(false)
        setDrawValues(false)
        fillFormatter = IFillFormatter { _, dataProvider -> dataProvider.yChartMax }
    }

    val lowerFill = LineDataSet(extendedEntries, "").apply {
        setDrawFilled(true)
        fillColor = Color.RED
        fillAlpha = 60
        color = Color.TRANSPARENT
        lineWidth = 0f
        setDrawCircles(false)
        setDrawValues(false)
        fillFormatter = IFillFormatter { _, dataProvider -> dataProvider.yChartMin }
    }

    // Only fills, line itself is not drawn
    return LineData(upperFill, lowerFill)
}
