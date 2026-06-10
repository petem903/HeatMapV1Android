package com.yanfeng.thermaldrone.processing

import android.graphics.Bitmap
import android.util.Log
import com.yanfeng.thermaldrone.model.ThermalFrame
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Processing settings applied to every live/captured frame. */
data class ProcessSettings(
    val palette: Palette = Palette.IRON,
    val autoRange: Boolean = true,
    val manualMinC: Float = 0f,
    val manualMaxC: Float = 100f,
    val denoiseKernel: Int = 1,          // 1=off, 3/5/7
    val temporalFrames: Int = 1,         // 1..8
    val edgeOverlay: Boolean = false,
    val edgeOpacity: Float = 0.5f        // 0..1
)

/** Result of processing one frame. */
class ProcessedFrame(
    val bitmap: Bitmap,
    val frame: ThermalFrame,             // denoised temps used for measurement
    val minC: Float,
    val maxC: Float,
    val timestampMs: Long
)

/**
 * Converts raw thermal frames to palette bitmaps.
 * All heavy work runs off the main thread (callers dispatch on Default).
 */
class FrameProcessor {

    private val temporal = TemporalAverager(1)

    companion object {
        private const val TAG = "FrameProcessor"

        /** OpenCV native init — guarded; edge overlay disabled when unavailable. */
        val openCvReady: Boolean by lazy {
            try {
                OpenCVLoader.initLocal()
            } catch (t: Throwable) {
                Log.w(TAG, "OpenCV unavailable: ${t.message}")
                false
            }
        }
    }

    fun onLowMemory() = temporal.clear()

    fun process(raw: ThermalFrame, settings: ProcessSettings, rgb: Bitmap? = null): ProcessedFrame {
        temporal.setDepth(settings.temporalFrames)
        var temps = temporal.push(raw)
        if (settings.denoiseKernel >= 3) {
            temps = Denoiser.spatialBlur(temps, raw.width, raw.height, settings.denoiseKernel)
        }
        val frame = ThermalFrame(raw.width, raw.height, temps, raw.timestampMs)

        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        if (settings.autoRange) {
            for (t in temps) { if (t < mn) mn = t; if (t > mx) mx = t }
            if (mn >= mx) { mn = 0f; mx = 1f }
        } else {
            mn = settings.manualMinC; mx = settings.manualMaxC
            if (mn >= mx) mx = mn + 1f
        }

        val lut = settings.palette.lut
        val pixels = IntArray(temps.size)
        val span = mx - mn
        for (i in temps.indices) {
            val idx = (((temps[i] - mn) / span) * 255f).toInt().coerceIn(0, 255)
            pixels[i] = lut[idx]
        }
        val bmp = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, raw.width, 0, 0, raw.width, raw.height)

        if (settings.edgeOverlay && openCvReady) {
            applyEdges(bmp, rgb ?: bmp, settings.edgeOpacity)
        }
        return ProcessedFrame(bmp, frame, mn, mx, raw.timestampMs)
    }

    /** Canny edges from [source] blended onto [target] at [opacity]. */
    private fun applyEdges(target: Bitmap, source: Bitmap, opacity: Float) {
        try {
            val src = Mat()
            org.opencv.android.Utils.bitmapToMat(source, src)
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(3.0, 3.0), 0.0)
            val edges = Mat()
            Imgproc.Canny(gray, edges, 60.0, 150.0)
            if (edges.rows() != target.height || edges.cols() != target.width) {
                Imgproc.resize(edges, edges, org.opencv.core.Size(target.width.toDouble(), target.height.toDouble()))
            }
            val data = ByteArray(edges.rows() * edges.cols())
            edges.get(0, 0, data)
            val w = target.width
            val px = IntArray(w * target.height)
            target.getPixels(px, 0, w, 0, 0, w, target.height)
            val a = (opacity.coerceIn(0f, 1f) * 255).toInt()
            for (i in data.indices) {
                if (data[i].toInt() != 0) {
                    val old = px[i]
                    val r = ((old shr 16 and 0xFF) * (255 - a) + 255 * a) / 255
                    val g = ((old shr 8 and 0xFF) * (255 - a) + 255 * a) / 255
                    val b = ((old and 0xFF) * (255 - a) + 255 * a) / 255
                    px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            target.setPixels(px, 0, w, 0, 0, w, target.height)
            src.release(); gray.release(); edges.release()
        } catch (t: Throwable) {
            Log.w(TAG, "edge overlay failed: ${t.message}")
        }
    }

    /** Synthetic grayscale RGB render of a thermal frame (sim-mode RGB source). */
    fun synthesizeRgb(frame: ThermalFrame): Bitmap {
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        for (t in frame.tempsC) { if (t < mn) mn = t; if (t > mx) mx = t }
        val span = (mx - mn).takeIf { it > 0f } ?: 1f
        val px = IntArray(frame.tempsC.size)
        for (i in px.indices) {
            val v = (((frame.tempsC[i] - mn) / span) * 255f).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, frame.width, 0, 0, frame.width, frame.height)
        return bmp
    }
}
