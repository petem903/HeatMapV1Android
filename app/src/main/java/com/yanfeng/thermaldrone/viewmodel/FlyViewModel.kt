package com.yanfeng.thermaldrone.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanfeng.thermaldrone.App
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.data.AppSettings
import com.yanfeng.thermaldrone.model.Measurement
import com.yanfeng.thermaldrone.model.MeasurementType
import com.yanfeng.thermaldrone.model.PointPx
import com.yanfeng.thermaldrone.model.ThermalFrame
import com.yanfeng.thermaldrone.processing.MeasurementEngine
import com.yanfeng.thermaldrone.processing.OverlayRenderer
import com.yanfeng.thermaldrone.processing.Palette
import com.yanfeng.thermaldrone.processing.ProcessSettings
import com.yanfeng.thermaldrone.processing.ProcessedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

enum class ViewerMode { THERMAL_ONLY, SIDE_BY_SIDE, OVERLAY }
enum class ActiveTool { NONE, SPOT, LINE, RECT, AUTO_MAX, AUTO_MIN }

/** Live frame stats for the collapsible panel. */
data class LiveStats(val minC: Float = 0f, val maxC: Float = 0f, val avgC: Float = 0f, val centerC: Float = 0f)

/**
 * FLY tab pipeline: raw frames -> FrameProcessor (Default dispatcher) ->
 * overlays -> StateFlow<Bitmap> for the SurfaceView + JPEG feed for /stream.
 */
class FlyViewModel(private val app: App) : ViewModel() {
    companion object { private const val TAG = "FlyViewModel" }

    val connectionState = app.drone.state
    val telemetry = app.drone.telemetry
    val banner = app.drone.banner
    val streaming = app.drone.streaming

    private val _processSettings = MutableStateFlow(ProcessSettings())
    val processSettings: StateFlow<ProcessSettings> = _processSettings

    private val _viewerMode = MutableStateFlow(ViewerMode.THERMAL_ONLY)
    val viewerMode: StateFlow<ViewerMode> = _viewerMode

    private val _activeTool = MutableStateFlow(ActiveTool.NONE)
    val activeTool: StateFlow<ActiveTool> = _activeTool

    private val _measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val measurements: StateFlow<List<Measurement>> = _measurements

    private val _displayBitmap = MutableStateFlow<Bitmap?>(null)
    val displayBitmap: StateFlow<Bitmap?> = _displayBitmap

    private val _liveStats = MutableStateFlow(LiveStats())
    val liveStats: StateFlow<LiveStats> = _liveStats

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _statsExpanded = MutableStateFlow(true)
    val statsExpanded: StateFlow<Boolean> = _statsExpanded

    private val _captureToast = MutableStateFlow<String?>(null)
    val captureToast: StateFlow<String?> = _captureToast

    private val _useFahrenheit = MutableStateFlow(false)
    val useFahrenheit: StateFlow<Boolean> = _useFahrenheit

    var sessionName: String = "default"

    @Volatile private var lastProcessed: ProcessedFrame? = null
    @Volatile private var lastRawFrame: ThermalFrame? = null
    @Volatile private var lastRgb: Bitmap? = null
    private val measurementId = AtomicLong(1)
    private var fpsCount = 0
    private var fpsWindowStart = System.currentTimeMillis()
    private var autoSettings = AppSettings()

    init {
        // settings -> processing defaults
        viewModelScope.launch(Dispatchers.Default) {
            app.settingsRepo.settings.collect { s ->
                autoSettings = s
                _useFahrenheit.value = s.useFahrenheit
                _processSettings.value = _processSettings.value.copy(
                    denoiseKernel = s.denoiseKernel,
                    temporalFrames = s.temporalFrames,
                    edgeOpacity = s.edgeOpacity
                )
            }
        }
        // frame pipeline — never on Main
        viewModelScope.launch(Dispatchers.Default) {
            app.drone.frames.collect { raw ->
                try {
                    processFrame(raw)
                } catch (t: Throwable) {
                    Log.e(TAG, "frame processing failed", t)
                }
            }
        }
    }

    /** Stream URL currently configured in Settings. */
    val streamUrl: String get() = autoSettings.streamUrl

    fun connect() = app.drone.connect(autoSettings.streamUrl)
    fun disconnect() = app.drone.disconnect()
    fun clearBanner() = app.drone.clearBanner()

    /** Video surface from the FLY screen's TextureView. */
    fun setVideoSurface(surface: android.view.Surface?) = app.drone.setSurface(surface)

    /** Sampled video frame -> pseudo-radiometric pipeline. */
    fun submitVideoBitmap(bmp: Bitmap) =
        app.drone.submitVideoFrame(bmp, autoSettings.streamTempMinC, autoSettings.streamTempMaxC)

    private fun processFrame(raw: ThermalFrame) {
        lastRawFrame = raw
        val settings = _processSettings.value
        val rgb = app.frameProcessor.synthesizeRgb(raw).also { lastRgb = it }
        val processed = app.frameProcessor.process(raw, settings, rgb)
        lastProcessed = processed

        // refresh measurement values against the new frame
        val refreshed = _measurements.value.map { recompute(it, processed.frame) }
        _measurements.value = refreshed

        val withOverlays = OverlayRenderer.render(processed.bitmap, refreshed, _useFahrenheit.value)
        // compose for the active viewer mode here, on Dispatchers.Default — never on Main
        _displayBitmap.value = composeForViewer(withOverlays)

        // live stats
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE; var sum = 0.0
        val temps = processed.frame.tempsC
        for (t in temps) { if (t < mn) mn = t; if (t > mx) mx = t; sum += t }
        _liveStats.value = LiveStats(
            minC = mn, maxC = mx,
            avgC = (sum / temps.size).toFloat(),
            centerC = processed.frame.tempAt(raw.width / 2, raw.height / 2)
        )

        // FPS
        fpsCount++
        val now = System.currentTimeMillis()
        if (now - fpsWindowStart >= 1000) {
            _fps.value = fpsCount
            fpsCount = 0
            fpsWindowStart = now
        }

        // JPEG for /stream (~10fps is fine; encode every other frame to cut CPU)
        if (fpsCount % 2 == 0) {
            try {
                val baos = ByteArrayOutputStream()
                withOverlays.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                app.latestJpeg.set(baos.toByteArray())
            } catch (t: Throwable) {
                Log.w(TAG, "jpeg encode failed: ${t.message}")
            }
        }
    }

    private fun recompute(m: Measurement, frame: ThermalFrame): Measurement = when (m.type) {
        MeasurementType.SPOT -> m.copy(spotTempC = MeasurementEngine.spot(frame, m.points[0].x, m.points[0].y))
        MeasurementType.LINE -> m.copy(lineStats = MeasurementEngine.line(frame, m.points[0].x, m.points[0].y, m.points[1].x, m.points[1].y))
        MeasurementType.RECT -> m.copy(rectStats = MeasurementEngine.rect(frame, m.points[0].x, m.points[0].y, m.points[1].x, m.points[1].y))
        MeasurementType.AUTO_MAX -> m.copy(extrema = MeasurementEngine.autoMaxima(frame, autoSettings.autoMaxCount.coerceIn(1, AppConfig.MAX_AUTO_EXTREMA), autoSettings.autoMaxThresholdC))
        MeasurementType.AUTO_MIN -> m.copy(extrema = MeasurementEngine.autoMinima(frame, autoSettings.autoMaxCount.coerceIn(1, AppConfig.MAX_AUTO_EXTREMA), autoSettings.autoMinThresholdC))
    }

    // ---- controls -------------------------------------------------------

    fun setPalette(p: Palette) { _processSettings.value = _processSettings.value.copy(palette = p) }
    fun setAutoRange(v: Boolean) { _processSettings.value = _processSettings.value.copy(autoRange = v) }
    fun setManualRange(minC: Float, maxC: Float) {
        _processSettings.value = _processSettings.value.copy(manualMinC = minC, manualMaxC = maxC, autoRange = false)
    }
    fun setDenoise(kernel: Int) { _processSettings.value = _processSettings.value.copy(denoiseKernel = kernel) }
    fun setTemporal(n: Int) { _processSettings.value = _processSettings.value.copy(temporalFrames = n.coerceIn(1, AppConfig.MAX_TEMPORAL_FRAMES)) }
    fun setEdgeOverlay(on: Boolean) { _processSettings.value = _processSettings.value.copy(edgeOverlay = on) }
    fun setViewerMode(m: ViewerMode) { _viewerMode.value = m }
    fun setTool(t: ActiveTool) { _activeTool.value = t }
    fun toggleStats() { _statsExpanded.value = !_statsExpanded.value }
    fun clearToast() { _captureToast.value = null }

    // ---- measurement gestures (frame-pixel coordinates) ------------------

    fun onTap(x: Int, y: Int) {
        val frame = lastProcessed?.frame ?: return
        when (_activeTool.value) {
            ActiveTool.SPOT -> addMeasurement(
                Measurement(measurementId.getAndIncrement(), MeasurementType.SPOT, listOf(PointPx(x, y)),
                    spotTempC = MeasurementEngine.spot(frame, x, y))
            )
            ActiveTool.AUTO_MAX -> addMeasurement(
                Measurement(measurementId.getAndIncrement(), MeasurementType.AUTO_MAX, emptyList(),
                    extrema = MeasurementEngine.autoMaxima(frame, autoSettings.autoMaxCount, autoSettings.autoMaxThresholdC))
            )
            ActiveTool.AUTO_MIN -> addMeasurement(
                Measurement(measurementId.getAndIncrement(), MeasurementType.AUTO_MIN, emptyList(),
                    extrema = MeasurementEngine.autoMinima(frame, autoSettings.autoMaxCount, autoSettings.autoMinThresholdC))
            )
            else -> Unit
        }
    }

    fun onDragComplete(x0: Int, y0: Int, x1: Int, y1: Int) {
        val frame = lastProcessed?.frame ?: return
        when (_activeTool.value) {
            ActiveTool.LINE -> addMeasurement(
                Measurement(measurementId.getAndIncrement(), MeasurementType.LINE,
                    listOf(PointPx(x0, y0), PointPx(x1, y1)),
                    lineStats = MeasurementEngine.line(frame, x0, y0, x1, y1))
            )
            ActiveTool.RECT -> addMeasurement(
                Measurement(measurementId.getAndIncrement(), MeasurementType.RECT,
                    listOf(PointPx(x0, y0), PointPx(x1, y1)),
                    rectStats = MeasurementEngine.rect(frame, x0, y0, x1, y1))
            )
            else -> Unit
        }
    }

    private fun addMeasurement(m: Measurement) { _measurements.value = _measurements.value + m }
    fun removeMeasurement(id: Long) { _measurements.value = _measurements.value.filterNot { it.id == id } }
    fun clearMeasurements() { _measurements.value = emptyList() }

    // ---- capture ---------------------------------------------------------

    fun capture() {
        val processed = lastProcessed ?: run { _captureToast.value = "No frame yet"; return }
        val bitmap = _displayBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val s = _processSettings.value
            val entry = app.sessionRepo.saveCapture(
                sessionName = sessionName,
                paletteBitmapWithOverlays = bitmap,
                rawFrame = lastRawFrame,
                rgbBitmap = lastRgb,
                telemetry = telemetry.value,
                paletteName = s.palette.displayName,
                tempMinC = processed.minC,
                tempMaxC = processed.maxC,
                autoRange = s.autoRange,
                useFahrenheit = _useFahrenheit.value,
                simulated = false,
                measurements = _measurements.value
            )
            _captureToast.value = if (entry != null) "Saved ${entry.jpg.name}" else "Capture failed (storage?)"
        }
    }

    /** Side-by-side / overlay composition for the viewer (called on Default). */
    private fun composeForViewer(thermal: Bitmap): Bitmap {
        val rgb = lastRgb ?: return thermal
        return when (_viewerMode.value) {
            ViewerMode.THERMAL_ONLY -> thermal
            ViewerMode.SIDE_BY_SIDE -> {
                val out = Bitmap.createBitmap(thermal.width * 2, thermal.height, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out)
                c.drawBitmap(Bitmap.createScaledBitmap(rgb, thermal.width, thermal.height, true), 0f, 0f, null)
                c.drawBitmap(thermal, thermal.width.toFloat(), 0f, null)
                out
            }
            ViewerMode.OVERLAY -> {
                val out = Bitmap.createScaledBitmap(rgb, thermal.width, thermal.height, true)
                    .copy(Bitmap.Config.ARGB_8888, true)
                val c = android.graphics.Canvas(out)
                val paint = android.graphics.Paint().apply { alpha = 160 }
                c.drawBitmap(thermal, 0f, 0f, paint)
                out
            }
        }
    }
}
