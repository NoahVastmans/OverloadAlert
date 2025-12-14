package kth.nova.overloadalert.ui.screens.graphs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kth.nova.overloadalert.R

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