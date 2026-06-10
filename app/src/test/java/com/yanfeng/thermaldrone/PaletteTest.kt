package com.yanfeng.thermaldrone

import com.yanfeng.thermaldrone.processing.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    @Test
    fun `every palette has exactly 256 entries`() {
        Palette.entries.forEach { p ->
            assertEquals("${p.name} LUT size", 256, p.lut.size)
        }
    }

    @Test
    fun `all LUT entries are opaque ARGB`() {
        Palette.entries.forEach { p ->
            p.lut.forEach { c ->
                assertEquals("${p.name} alpha", 0xFF, (c ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `grayscale is monotonic`() {
        val lut = Palette.GRAYSCALE.lut
        for (i in 1 until 256) {
            val prev = lut[i - 1] and 0xFF
            val cur = lut[i] and 0xFF
            assertTrue("monotonic at $i", cur >= prev)
        }
        assertEquals(0x00, lut[0] and 0xFF)
        assertEquals(0xFF, lut[255] and 0xFF)
    }

    @Test
    fun `fromName resolves display names and enum names`() {
        assertEquals(Palette.IRON, Palette.fromName("Iron"))
        assertEquals(Palette.HOT_COLD, Palette.fromName("Hot-Cold"))
        assertEquals(Palette.JET, Palette.fromName("JET"))
        assertEquals(Palette.IRON, Palette.fromName("nonsense"))
    }
}
