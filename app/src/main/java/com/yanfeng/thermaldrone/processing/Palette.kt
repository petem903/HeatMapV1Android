package com.yanfeng.thermaldrone.processing

import kotlin.math.roundToInt

/** Thermal palettes as 256-entry packed ARGB LUTs. Pure JVM — unit-testable. */
enum class Palette(val displayName: String) {
    IRON("Iron"), RAINBOW("Rainbow"), INFERNO("Inferno"), GRAYSCALE("Grayscale"),
    ARCTIC("Arctic"), HOT_COLD("Hot-Cold"), LAVA("Lava"), JET("Jet");

    val lut: IntArray by lazy { build(this) }

    companion object {
        fun fromName(n: String): Palette = entries.firstOrNull { it.name == n || it.displayName == n } ?: IRON

        private fun argb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        /** Piecewise-linear gradient through anchor colors at positions 0..1. */
        private fun gradient(anchors: List<Pair<Float, Triple<Int, Int, Int>>>): IntArray {
            val lut = IntArray(256)
            for (i in 0 until 256) {
                val t = i / 255f
                var lo = anchors.first()
                var hi = anchors.last()
                for (j in 0 until anchors.size - 1) {
                    if (t >= anchors[j].first && t <= anchors[j + 1].first) {
                        lo = anchors[j]; hi = anchors[j + 1]; break
                    }
                }
                val span = (hi.first - lo.first).takeIf { it > 0f } ?: 1f
                val f = ((t - lo.first) / span).coerceIn(0f, 1f)
                val r = (lo.second.first + f * (hi.second.first - lo.second.first)).roundToInt()
                val g = (lo.second.second + f * (hi.second.second - lo.second.second)).roundToInt()
                val b = (lo.second.third + f * (hi.second.third - lo.second.third)).roundToInt()
                lut[i] = argb(r, g, b)
            }
            return lut
        }

        private fun c(r: Int, g: Int, b: Int) = Triple(r, g, b)

        private fun build(p: Palette): IntArray = when (p) {
            IRON -> gradient(listOf(
                0f to c(0, 0, 0), 0.15f to c(32, 0, 96), 0.35f to c(128, 0, 128),
                0.55f to c(208, 64, 16), 0.75f to c(255, 160, 0), 0.9f to c(255, 224, 64),
                1f to c(255, 255, 255)))
            RAINBOW -> gradient(listOf(
                0f to c(0, 0, 128), 0.2f to c(0, 0, 255), 0.4f to c(0, 255, 255),
                0.6f to c(0, 255, 0), 0.8f to c(255, 255, 0), 1f to c(255, 0, 0)))
            INFERNO -> gradient(listOf(
                0f to c(0, 0, 4), 0.25f to c(87, 16, 110), 0.5f to c(188, 55, 84),
                0.75f to c(249, 142, 9), 1f to c(252, 255, 164)))
            GRAYSCALE -> gradient(listOf(0f to c(0, 0, 0), 1f to c(255, 255, 255)))
            ARCTIC -> gradient(listOf(
                0f to c(8, 8, 40), 0.3f to c(0, 80, 160), 0.55f to c(0, 180, 220),
                0.8f to c(160, 240, 255), 1f to c(255, 255, 255)))
            HOT_COLD -> gradient(listOf(
                0f to c(0, 64, 255), 0.45f to c(80, 80, 96), 0.55f to c(96, 80, 80),
                1f to c(255, 32, 0)))
            LAVA -> gradient(listOf(
                0f to c(16, 0, 0), 0.35f to c(120, 8, 0), 0.6f to c(220, 60, 0),
                0.85f to c(255, 180, 32), 1f to c(255, 255, 200)))
            JET -> gradient(listOf(
                0f to c(0, 0, 143), 0.125f to c(0, 0, 255), 0.375f to c(0, 255, 255),
                0.625f to c(255, 255, 0), 0.875f to c(255, 0, 0), 1f to c(128, 0, 0)))
        }
    }
}
