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
class MyMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    // This method is called every time the MarkerView is redrawn
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val km = e.y / 1000f
            tvContent.text = String.format("%.2f km", km)
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // This determines the position of the marker view relative to the bar
        return MPPointF(-(width / 2f), -height.toFloat() - 10f)
    }
}