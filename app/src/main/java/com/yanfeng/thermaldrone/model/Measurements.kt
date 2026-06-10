package com.yanfeng.thermaldrone.model

/** Measurement primitives. Coordinates are thermal-frame pixels (640x512). */

enum class MeasurementType { SPOT, LINE, RECT, AUTO_MAX, AUTO_MIN }

data class PointPx(val x: Int, val y: Int)

data class LineStats(val minC: Float, val avgC: Float, val maxC: Float, val profile: List<Float>)

data class RectStats(
    val minC: Float,
    val avgC: Float,
    val maxC: Float,
    val stdevC: Float,
    val hottest: PointPx,
    val coldest: PointPx
)

data class HotSpot(val point: PointPx, val tempC: Float)

/** A persisted measurement with its computed result. */
data class Measurement(
    val id: Long,
    val type: MeasurementType,
    val points: List<PointPx>,          // SPOT:1, LINE:2, RECT:2 (corners), AUTO_*: n spots
    val label: String = "",
    val annotation: String = "",
    val spotTempC: Float? = null,
    val lineStats: LineStats? = null,
    val rectStats: RectStats? = null,
    val extrema: List<HotSpot> = emptyList()
)

/** Capture metadata persisted to meta_[ts].json. */
data class CaptureMeta(
    val timestampMs: Long,
    val sessionName: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMslM: Double,
    val altitudeAglM: Double,
    val headingDeg: Double,
    val palette: String,
    val tempMinC: Float,
    val tempMaxC: Float,
    val autoRange: Boolean,
    val useFahrenheit: Boolean,
    val simulated: Boolean,
    val measurements: List<Measurement>,
    /** raw tiff encoding: value = tempC*100 + 10000 (uint16) */
    val rawTiffScale: Float = 100f,
    val rawTiffOffset: Float = 10000f
)

data class SessionInfo(
    val name: String,
    val path: String,
    val captureCount: Int,
    val firstCaptureMs: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailPath: String?
)
