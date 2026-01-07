package kth.nova.overloadalert.ui.screens.graphs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kth.nova.overloadalert.R

/**
 * A custom [MarkerView] implementation used to display details when a user interacts with a chart entry.
 *
 * This view is designed to work with MPAndroidChart and provides a tooltip-like popup showing specific
 * data values at the selected point. It supports two modes of operation based on the provided data:
 * 1. **ACWR Chart Mode**: If [chronicLoadData] is provided, it displays both "Acute" and "Chronic" load values in kilometers.
 * 2. **Default Mode**: If [chronicLoadData] is null, it displays a single primary value in kilometers.
 *
 * @property context The UI context used to inflate the layout.
 * @property layoutResource The resource ID of the layout file to use for this marker view.
 * @property chronicLoadData An optional list of [Entry] objects representing chronic load data, used for the dual-value display.
 */
@SuppressLint("ViewConstructor")
class MyMarkerView(
    context: Context,
    layoutResource: Int,
    private val chronicLoadData: List<Entry>? = null
) : MarkerView(context, layoutResource) {

    private val tvPrimary: TextView = findViewById(R.id.tvContentPrimary)
    private val tvSecondary: TextView = findViewById(R.id.tvContentSecondary)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val primaryValueKm = e.y / 1000f

            if (chronicLoadData != null) {
                // ACWR Chart: Show both Acute and Chronic
                val index = e.x.toInt()
                if (index >= 0 && index < chronicLoadData.size) {
                    val chronicValueKm = chronicLoadData[index].y / 1000f
                    tvPrimary.text = String.format("Acute: %.2f km", primaryValueKm)
                    tvSecondary.text = String.format("Chronic: %.2f km", chronicValueKm)
                } else {
                    tvPrimary.text = String.format("%.2f km", primaryValueKm)
                    tvSecondary.text = ""
                }
            } else {
                // Default Chart: Show single value
                tvPrimary.text = String.format("%.2f km", primaryValueKm)
                tvSecondary.text = ""
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 10f)
    }
}