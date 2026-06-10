package com.yanfeng.thermaldrone

import com.yanfeng.thermaldrone.model.ThermalFrame
import com.yanfeng.thermaldrone.processing.MeasurementEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementEngineTest {

    private fun frame(w: Int = 64, h: Int = 48, fill: (Int, Int) -> Float): ThermalFrame {
        val t = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) t[y * w + x] = fill(x, y)
        return ThermalFrame(w, h, t)
    }

    @Test
    fun `spot returns exact pixel and clamps out-of-bounds`() {
        val f = frame { x, y -> (x + y * 100).toFloat() }
        assertEquals(305f, MeasurementEngine.spot(f, 5, 3), 0.001f)
        // clamped
        assertEquals(MeasurementEngine.spot(f, 0, 0), MeasurementEngine.spot(f, -5, -5), 0.001f)
        assertEquals(MeasurementEngine.spot(f, 63, 47), MeasurementEngine.spot(f, 999, 999), 0.001f)
    }

    @Test
    fun `line profile stats on horizontal gradient`() {
        val f = frame { x, _ -> x.toFloat() }
        val s = MeasurementEngine.line(f, 0, 10, 63, 10)
        assertEquals(0f, s.minC, 0.001f)
        assertEquals(63f, s.maxC, 0.001f)
        assertEquals(31.5f, s.avgC, 0.001f)
        assertEquals(64, s.profile.size)
    }

    @Test
    fun `rect stats find hottest pixel and stdev of constant region is zero`() {
        val f = frame { x, y -> if (x == 30 && y == 20) 99f else 10f }
        val s = MeasurementEngine.rect(f, 0, 0, 63, 47)
        assertEquals(99f, s.maxC, 0.001f)
        assertEquals(10f, s.minC, 0.001f)
        assertEquals(30, s.hottest.x)
        assertEquals(20, s.hottest.y)

        val flat = frame { _, _ -> 25f }
        val s2 = MeasurementEngine.rect(flat, 0, 0, 63, 47)
        assertEquals(0f, s2.stdevC, 0.0001f)
        assertEquals(25f, s2.avgC, 0.0001f)
    }

    @Test
    fun `autoMaxima finds the two seeded hot spots`() {
        val f = frame(128, 128) { x, y ->
            var t = 20f
            if (x == 30 && y == 30) t = 80f
            if (x == 100 && y == 90) t = 70f
            t
        }
        val spots = MeasurementEngine.autoMaxima(f, n = 2, thresholdC = 50f)
        assertEquals(2, spots.size)
        assertEquals(80f, spots[0].tempC, 0.001f)
        assertEquals(30, spots[0].point.x)
        assertEquals(70f, spots[1].tempC, 0.001f)
    }

    @Test
    fun `autoMinima finds cold spot and respects threshold`() {
        val f = frame(128, 128) { x, y -> if (x == 64 && y == 64) -5f else 20f }
        val spots = MeasurementEngine.autoMinima(f, n = 5, thresholdC = 0f)
        assertEquals(1, spots.size)
        assertEquals(-5f, spots[0].tempC, 0.001f)
        assertTrue(MeasurementEngine.autoMinima(f, 5, -10f).isEmpty())
    }
}
