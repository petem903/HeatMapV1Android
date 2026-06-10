package com.yanfeng.thermaldrone.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Lifecycle-safe SurfaceView renderer for thermal bitmaps.
 * Drawing happens on a dedicated HandlerThread — never on Main.
 * Letterboxes the 640x512 frame, exposes view->frame coordinate mapping
 * for measurement gestures.
 */
class ThermalSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    @Volatile private var surfaceReady = false
    @Volatile private var latest: Bitmap? = null
    @Volatile private var dest = RectF()
    @Volatile private var frameW = 640
    @Volatile private var frameH = 512

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        holder.addCallback(this)
    }

    fun setFrame(bitmap: Bitmap) {
        latest = bitmap
        frameW = bitmap.width
        frameH = bitmap.height
        renderHandler?.post { drawNow() }
    }

    /** Maps view coordinates to frame-pixel coordinates; null if outside the image. */
    fun viewToFrame(vx: Float, vy: Float): Pair<Int, Int>? {
        val d = dest
        if (d.width() <= 0 || !d.contains(vx, vy)) return null
        val fx = ((vx - d.left) / d.width() * frameW).toInt().coerceIn(0, frameW - 1)
        val fy = ((vy - d.top) / d.height() * frameH).toInt().coerceIn(0, frameH - 1)
        return fx to fy
    }

    private fun drawNow() {
        if (!surfaceReady) return
        val bmp = latest ?: return
        val canvas = try { holder.lockCanvas() } catch (t: Throwable) { null } ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val vw = canvas.width.toFloat()
            val vh = canvas.height.toFloat()
            val scale = minOf(vw / bmp.width, vh / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val l = (vw - w) / 2f
            val t = (vh - h) / 2f
            dest = RectF(l, t, l + w, t + h)
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dest, paint)
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) { }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val t = HandlerThread("thermal-render").also { it.start() }
        renderThread = t
        renderHandler = Handler(t.looper)
        surfaceReady = true
        renderHandler?.post { drawNow() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderHandler?.post { drawNow() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
    }
}
