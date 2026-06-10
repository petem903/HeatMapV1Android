package com.yanfeng.thermaldrone.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanfeng.thermaldrone.App
import com.yanfeng.thermaldrone.data.CaptureEntry
import com.yanfeng.thermaldrone.model.CaptureMeta
import com.yanfeng.thermaldrone.model.Measurement
import com.yanfeng.thermaldrone.model.MeasurementType
import com.yanfeng.thermaldrone.model.PointPx
import com.yanfeng.thermaldrone.model.ThermalFrame
import com.yanfeng.thermaldrone.processing.MeasurementEngine
import com.yanfeng.thermaldrone.processing.OverlayRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** INSPECT tab: measurements + annotations on captured frames. */
class InspectViewModel(private val app: App) : ViewModel() {

    private val _captures = MutableStateFlow<List<CaptureEntry>>(emptyList())
    val captures: StateFlow<List<CaptureEntry>> = _captures

    private val _selected = MutableStateFlow<CaptureEntry?>(null)
    val selected: StateFlow<CaptureEntry?> = _selected

    private val _meta = MutableStateFlow<CaptureMeta?>(null)
    val meta: StateFlow<CaptureMeta?> = _meta

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap

    private val _tool = MutableStateFlow(ActiveTool.NONE)
    val tool: StateFlow<ActiveTool> = _tool

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private var rawFrame: ThermalFrame? = null
    private var baseBitmap: Bitmap? = null
    private val nextId = AtomicLong(1000)

    fun loadSession(sessionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _captures.value = app.sessionRepo.listCapturesFor(sessionName)
        }
    }

    fun select(entry: CaptureEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            _selected.value = entry
            val m = entry.metaJson?.let { app.sessionRepo.readMeta(it) }
            _meta.value = m
            rawFrame = app.sessionRepo.loadRawFrame(entry)
            baseBitmap = app.sessionRepo.loadBitmap(entry.jpg)
            redraw()
            val ids = m?.measurements?.maxOfOrNull { it.id } ?: 0L
            nextId.set(maxOf(1000L, ids + 1))
        }
    }

    fun setTool(t: ActiveTool) { _tool.value = t }

    fun onTap(x: Int, y: Int) {
        val frame = rawFrame ?: run { _status.value = "No raw data for this capture"; return }
        when (_tool.value) {
            ActiveTool.SPOT -> addMeasurement(
                Measurement(nextId.getAndIncrement(), MeasurementType.SPOT, listOf(PointPx(x, y)),
                    spotTempC = MeasurementEngine.spot(frame, x, y))
            )
            ActiveTool.AUTO_MAX -> addMeasurement(
                Measurement(nextId.getAndIncrement(), MeasurementType.AUTO_MAX, emptyList(),
                    extrema = MeasurementEngine.autoMaxima(frame, 3, 40f))
            )
            ActiveTool.AUTO_MIN -> addMeasurement(
                Measurement(nextId.getAndIncrement(), MeasurementType.AUTO_MIN, emptyList(),
                    extrema = MeasurementEngine.autoMinima(frame, 3, 15f))
            )
            else -> Unit
        }
    }

    fun onDragComplete(x0: Int, y0: Int, x1: Int, y1: Int) {
        val frame = rawFrame ?: return
        when (_tool.value) {
            ActiveTool.LINE -> addMeasurement(
                Measurement(nextId.getAndIncrement(), MeasurementType.LINE, listOf(PointPx(x0, y0), PointPx(x1, y1)),
                    lineStats = MeasurementEngine.line(frame, x0, y0, x1, y1))
            )
            ActiveTool.RECT -> addMeasurement(
                Measurement(nextId.getAndIncrement(), MeasurementType.RECT, listOf(PointPx(x0, y0), PointPx(x1, y1)),
                    rectStats = MeasurementEngine.rect(frame, x0, y0, x1, y1))
            )
            else -> Unit
        }
    }

    /** Text annotation pinned to a point. */
    fun annotate(x: Int, y: Int, text: String) {
        val frame = rawFrame ?: return
        addMeasurement(
            Measurement(nextId.getAndIncrement(), MeasurementType.SPOT, listOf(PointPx(x, y)),
                annotation = text, spotTempC = MeasurementEngine.spot(frame, x, y))
        )
    }

    private fun addMeasurement(m: Measurement) {
        val cur = _meta.value ?: return
        updateMeta(cur.copy(measurements = cur.measurements + m))
    }

    fun removeMeasurement(id: Long) {
        val cur = _meta.value ?: return
        updateMeta(cur.copy(measurements = cur.measurements.filterNot { it.id == id }))
    }

    private fun updateMeta(meta: CaptureMeta) {
        _meta.value = meta
        viewModelScope.launch(Dispatchers.IO) {
            _selected.value?.metaJson?.let { app.sessionRepo.updateMeta(it, meta) }
            redraw()
        }
    }

    private fun redraw() {
        val base = baseBitmap ?: return
        val m = _meta.value
        _bitmap.value = if (m == null) base
        else OverlayRenderer.render(base, m.measurements, m.useFahrenheit)
    }

    fun clearStatus() { _status.value = null }
}
