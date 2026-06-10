package com.yanfeng.thermaldrone

import com.yanfeng.thermaldrone.model.ThermalFrame
import com.yanfeng.thermaldrone.processing.Denoiser
import com.yanfeng.thermaldrone.processing.TemporalAverager
import com.yanfeng.thermaldrone.processing.TiffCodec
import com.yanfeng.thermaldrone.util.Format
import com.yanfeng.thermaldrone.util.TokenGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UtilsAndProcessingTest {

    @Test
    fun `token is 6 alphanumeric chars and random`() {
        val tokens = (1..50).map { TokenGenerator.generate() }
        tokens.forEach { t ->
            assertEquals(6, t.length)
            assertTrue(t.all { it.isLetterOrDigit() })
        }
        assertTrue("tokens should vary", tokens.toSet().size > 1)
    }

    @Test
    fun `celsius to fahrenheit conversion`() {
        assertEquals(32f, Format.cToF(0f), 0.001f)
        assertEquals(212f, Format.cToF(100f), 0.001f)
        assertEquals("0.0°C", Format.temp(0f, false))
        assertEquals("32.0°F", Format.temp(0f, true))
    }

    @Test
    fun `temporal averager averages and respects depth`() {
        val avg = TemporalAverager(2)
        val f1 = ThermalFrame(2, 1, floatArrayOf(10f, 20f))
        val f2 = ThermalFrame(2, 1, floatArrayOf(30f, 40f))
        val f3 = ThermalFrame(2, 1, floatArrayOf(50f, 60f))
        avg.push(f1)
        val a2 = avg.push(f2)
        assertEquals(20f, a2[0], 0.001f) // (10+30)/2
        val a3 = avg.push(f3)            // window slides: (30+50)/2
        assertEquals(40f, a3[0], 0.001f)
    }

    @Test
    fun `spatial blur preserves constant field`() {
        val src = FloatArray(64) { 42f }
        for (k in intArrayOf(3, 5, 7)) {
            val out = Denoiser.spatialBlur(src, 8, 8, k)
            out.forEach { assertEquals(42f, it, 0.001f) }
        }
    }

    @Test
    fun `tiff roundtrip preserves temperatures to centidegree`() {
        val temps = FloatArray(16 * 8) { i -> -20f + i * 0.77f }
        val frame = ThermalFrame(16, 8, temps)
        val f = File.createTempFile("roundtrip", ".tiff")
        try {
            TiffCodec.write(f, frame)
            val back = TiffCodec.read(f)!!
            assertEquals(16, back.width)
            assertEquals(8, back.height)
            for (i in temps.indices) {
                assertEquals(temps[i], back.tempsC[i], 0.011f) // centi-°C quantisation
            }
        } finally {
            f.delete()
        }
    }
}
