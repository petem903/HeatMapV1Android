package com.yanfeng.thermaldrone.processing

import com.yanfeng.thermaldrone.model.HotSpot
import com.yanfeng.thermaldrone.model.LineStats
import com.yanfeng.thermaldrone.model.PointPx
import com.yanfeng.thermaldrone.model.RectStats
import com.yanfeng.thermaldrone.model.ThermalFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Pure-JVM thermal measurement math. */
object MeasurementEngine {

    fun spot(frame: ThermalFrame, x: Int, y: Int): Float = frame.tempAt(x, y)

    /** Bresenham line sampling -> temperature profile + stats. */
    fun line(frame: ThermalFrame, x0: Int, y0: Int, x1: Int, y1: Int): LineStats {
        val profile = ArrayList<Float>()
        var cx = x0.coerceIn(0, frame.width - 1)
        var cy = y0.coerceIn(0, frame.height - 1)
        val ex = x1.coerceIn(0, frame.width - 1)
        val ey = y1.coerceIn(0, frame.height - 1)
        val dx = abs(ex - cx); val sx = if (cx < ex) 1 else -1
        val dy = -abs(ey - cy); val sy = if (cy < ey) 1 else -1
        var err = dx + dy
        while (true) {
            profile.add(frame.tempAt(cx, cy))
            if (cx == ex && cy == ey) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; cx += sx }
            if (e2 <= dx) { err += dx; cy += sy }
        }
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE; var sum = 0.0
        for (t in profile) { mn = min(mn, t); mx = max(mx, t); sum += t }
        val avg = if (profile.isEmpty()) 0f else (sum / profile.size).toFloat()
        return LineStats(mn, avg, mx, profile)
    }

    /** ROI stats: min/avg/max/stdev + hottest & coldest pixel. */
    fun rect(frame: ThermalFrame, xa: Int, ya: Int, xb: Int, yb: Int): RectStats {
        val x0 = min(xa, xb).coerceIn(0, frame.width - 1)
        val x1 = max(xa, xb).coerceIn(0, frame.width - 1)
        val y0 = min(ya, yb).coerceIn(0, frame.height - 1)
        val y1 = max(ya, yb).coerceIn(0, frame.height - 1)
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        var hot = PointPx(x0, y0); var cold = PointPx(x0, y0)
        var sum = 0.0; var sumSq = 0.0; var n = 0
        for (y in y0..y1) {
            val row = y * frame.width
            for (x in x0..x1) {
                val t = frame.tempsC[row + x]
                if (t > mx) { mx = t; hot = PointPx(x, y) }
                if (t < mn) { mn = t; cold = PointPx(x, y) }
                sum += t; sumSq += t.toDouble() * t; n++
            }
        }
        val avg = if (n == 0) 0.0 else sum / n
        val variance = if (n == 0) 0.0 else max(0.0, sumSq / n - avg * avg)
        return RectStats(mn, avg.toFloat(), mx, sqrt(variance).toFloat(), hot, cold)
    }

    /** Top-N local maxima above [thresholdC], non-max-suppressed within [minSeparationPx]. */
    fun autoMaxima(
        frame: ThermalFrame, n: Int, thresholdC: Float,
        window: Int = 5, minSeparationPx: Int = 24
    ): List<HotSpot> = extrema(frame, n, window, minSeparationPx) { t, best -> t >= thresholdC && t > best }

    /** Top-N local minima below [thresholdC]. */
    fun autoMinima(
        frame: ThermalFrame, n: Int, thresholdC: Float,
        window: Int = 5, minSeparationPx: Int = 24
    ): List<HotSpot> = extrema(frame, n, window, minSeparationPx, invert = true) { t, best -> t <= thresholdC && t < best }

    private inline fun extrema(
        frame: ThermalFrame, n: Int, window: Int, minSep: Int,
        invert: Boolean = false, accept: (Float, Float) -> Boolean
    ): List<HotSpot> {
        val half = window / 2
        val candidates = ArrayList<HotSpot>()
        val w = frame.width; val h = frame.height
        for (y in half until h - half) {
            val row = y * w
            for (x in half until w - half) {
                val t = frame.tempsC[row + x]
                var isExtreme = true
                loop@ for (dy in -half..half) {
                    val r2 = (y + dy) * w
                    for (dx in -half..half) {
                        if (dx == 0 && dy == 0) continue
                        val o = frame.tempsC[r2 + x + dx]
                        if (if (invert) o < t else o > t) { isExtreme = false; break@loop }
                    }
                }
                if (isExtreme && accept(t, if (invert) Float.MAX_VALUE else -Float.MAX_VALUE)) {
                    candidates.add(HotSpot(PointPx(x, y), t))
                }
            }
        }
        val sorted = if (invert) candidates.sortedBy { it.tempC } else candidates.sortedByDescending { it.tempC }
        val picked = ArrayList<HotSpot>()
        for (cand in sorted) {
            if (picked.size >= n) break
            val tooClose = picked.any {
                abs(it.point.x - cand.point.x) < minSep && abs(it.point.y - cand.point.y) < minSep
            }
            if (!tooClose) picked.add(cand)
        }
        return picked
    }
}
