package com.yanfeng.thermaldrone.processing

import com.yanfeng.thermaldrone.model.ThermalFrame

/** Pure-JVM spatial + temporal denoising. */
object Denoiser {

    /** Separable mean blur, kernel 1 (off) /3/5/7. Returns new array. */
    fun spatialBlur(src: FloatArray, w: Int, h: Int, kernel: Int): FloatArray {
        if (kernel < 3) return src.copyOf()
        val half = kernel / 2
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        // horizontal
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sum = 0f; var n = 0
                for (k in -half..half) {
                    val xx = x + k
                    if (xx in 0 until w) { sum += src[row + xx]; n++ }
                }
                tmp[row + x] = sum / n
            }
        }
        // vertical
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f; var n = 0
                for (k in -half..half) {
                    val yy = y + k
                    if (yy in 0 until h) { sum += tmp[yy * w + x]; n++ }
                }
                out[y * w + x] = sum / n
            }
        }
        return out
    }
}

/** Rolling temporal average over the last 1..8 frames. Drop buffer on low memory. */
class TemporalAverager(private var depth: Int = 1) {
    private val buffer = ArrayDeque<FloatArray>()

    @Synchronized
    fun setDepth(d: Int) {
        depth = d.coerceIn(1, 8)
        while (buffer.size > depth) buffer.removeFirst()
    }

    @Synchronized
    fun push(frame: ThermalFrame): FloatArray {
        buffer.addLast(frame.tempsC.copyOf())
        while (buffer.size > depth) buffer.removeFirst()
        if (buffer.size == 1) return buffer.first().copyOf()
        val out = FloatArray(frame.tempsC.size)
        for (f in buffer) for (i in out.indices) out[i] += f[i]
        val n = buffer.size.toFloat()
        for (i in out.indices) out[i] /= n
        return out
    }

    /** onLowMemory hook. */
    @Synchronized
    fun clear() = buffer.clear()
}
