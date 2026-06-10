package com.yanfeng.thermaldrone.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.yanfeng.thermaldrone.model.Measurement
import com.yanfeng.thermaldrone.model.MeasurementType
import com.yanfeng.thermaldrone.util.Format
import kotlin.math.max
import kotlin.math.min

/** Draws measurement overlays onto thermal bitmaps (live HUD + capture export). */
object OverlayRenderer {

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 22f; setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    /** Returns a copy of [base] with all [measurements] drawn. */
    fun render(base: Bitmap, measurements: List<Measurement>, useFahrenheit: Boolean): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        for (m in measurements) draw(c, m, useFahrenheit)
        return out
    }

    fun draw(c: Canvas, m: Measurement, f: Boolean) {
        when (m.type) {
            MeasurementType.SPOT -> {
                val p = m.points.first()
                val paint = stroke(Color.WHITE)
                c.drawCircle(p.x.toFloat(), p.y.toFloat(), 10f, paint)
                c.drawLine(p.x - 16f, p.y.toFloat(), p.x + 16f, p.y.toFloat(), paint)
                c.drawLine(p.x.toFloat(), p.y - 16f, p.x.toFloat(), p.y + 16f, paint)
                m.spotTempC?.let { c.drawText(Format.temp(it, f), p.x + 14f, p.y - 14f, textPaint) }
            }
            MeasurementType.LINE -> {
                val a = m.points[0]; val b = m.points[1]
                c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), stroke(Color.CYAN))
                m.lineStats?.let {
                    c.drawText(
                        "min ${Format.temp(it.minC, f)}  avg ${Format.temp(it.avgC, f)}  max ${Format.temp(it.maxC, f)}",
                        min(a.x, b.x).toFloat(), min(a.y, b.y) - 8f, textPaint
                    )
                }
            }
            MeasurementType.RECT -> {
                val a = m.points[0]; val b = m.points[1]
                val r = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
                c.drawRect(r, stroke(Color.YELLOW))
                m.rectStats?.let { s ->
                    c.drawCircle(s.hottest.x.toFloat(), s.hottest.y.toFloat(), 8f, stroke(Color.RED))
                    c.drawText(
                        "min ${Format.temp(s.minC, f)} avg ${Format.temp(s.avgC, f)} max ${Format.temp(s.maxC, f)} σ ${"%.1f".format(s.stdevC)}",
                        r.left.toFloat(), r.top - 8f, textPaint
                    )
                }
            }
            MeasurementType.AUTO_MAX, MeasurementType.AUTO_MIN -> {
                val color = if (m.type == MeasurementType.AUTO_MAX) Color.RED else Color.BLUE
                for ((i, s) in m.extrema.withIndex()) {
                    c.drawCircle(s.point.x.toFloat(), s.point.y.toFloat(), 12f, stroke(color))
                    c.drawText("${i + 1}: ${Format.temp(s.tempC, f)}", s.point.x + 14f, s.point.y + 6f, textPaint)
                }
            }
        }
        if (m.annotation.isNotBlank() && m.points.isNotEmpty()) {
            val p = m.points.first()
            c.drawText(m.annotation, p.x.toFloat(), p.y + 34f, textPaint)
        }
    }
}
