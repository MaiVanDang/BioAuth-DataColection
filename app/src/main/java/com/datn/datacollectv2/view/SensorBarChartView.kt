package com.datn.datacollectv2.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList

class SensorBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxBars = 40
    private val values  = LinkedList<Float>()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26A69A")  // teal_400
        style = Paint.Style.FILL
    }

    /** Push a new magnitude value; triggers redraw. */
    fun push(value: Float) {
        if (values.size >= maxBars) values.poll()
        values.add(value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barWidth  = (w / maxBars) - 2f
        val maxValue  = values.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f

        values.forEachIndexed { index, v ->
            val barH = (v / maxValue) * h * 0.9f
            val left = index * (barWidth + 2f)
            val top  = h - barH
            canvas.drawRoundRect(left, top, left + barWidth, h, 2f, 2f, barPaint)
        }
    }
}
