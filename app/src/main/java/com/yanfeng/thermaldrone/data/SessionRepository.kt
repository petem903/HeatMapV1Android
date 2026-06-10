package com.yanfeng.thermaldrone.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import com.google.gson.GsonBuilder
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.model.CaptureMeta
import com.yanfeng.thermaldrone.model.Measurement
import com.yanfeng.thermaldrone.model.SessionInfo
import com.yanfeng.thermaldrone.model.Telemetry
import com.yanfeng.thermaldrone.model.ThermalFrame
import com.yanfeng.thermaldrone.processing.OverlayRenderer
import com.yanfeng.thermaldrone.processing.TiffCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One stored capture (file group sharing a timestamp). */
data class CaptureEntry(
    val timestampMs: Long,
    val jpg: File,
    val rawTiff: File?,
    val rgbJpg: File?,
    val composite: File?,
    val metaJson: File?
)

/**
 * Files live under [ExternalFilesDir]/HeatMapV1/sessions/[session-name]/.
 * All I/O on Dispatchers.IO. Storage-full and permission errors are caught —
 * a capture failure never crashes the app.
 */
class SessionRepository(private val context: Context) {
    companion object { private const val TAG = "SessionRepository" }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun sessionsRoot(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, AppConfig.SESSIONS_DIR)

    fun sessionDir(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "default" }
        return File(sessionsRoot(), safe)
    }

    /** Saves the full capture file group. Returns saved entry or null on failure. */
    suspend fun saveCapture(
        sessionName: String,
        paletteBitmapWithOverlays: Bitmap,
        rawFrame: ThermalFrame?,
        rgbBitmap: Bitmap?,
        telemetry: Telemetry,
        paletteName: String,
        tempMinC: Float,
        tempMaxC: Float,
        autoRange: Boolean,
        useFahrenheit: Boolean,
        simulated: Boolean,
        measurements: List<Measurement>
    ): CaptureEntry? = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            val dir = sessionDir(sessionName).apply { mkdirs() }
            if (!dir.exists()) {
                Log.e(TAG, "cannot create session dir")
                return@withContext null
            }

            val jpg = File(dir, "thermal_$ts.jpg")
            jpg.outputStream().use { paletteBitmapWithOverlays.compress(Bitmap.CompressFormat.JPEG, 92, it) }

            var tiff: File? = null
            if (rawFrame != null) {
                tiff = File(dir, "thermal_${ts}_raw.tiff")
                TiffCodec.write(tiff, rawFrame)
            }

            var rgbFile: File? = null
            if (rgbBitmap != null) {
                rgbFile = File(dir, "rgb_$ts.jpg")
                rgbFile.outputStream().use { rgbBitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }

            // composite: thermal | rgb side-by-side (or thermal alone)
            val composite = File(dir, "composite_$ts.jpg")
            val comp = buildComposite(paletteBitmapWithOverlays, rgbBitmap)
            composite.outputStream().use { comp.compress(Bitmap.CompressFormat.JPEG, 92, it) }

            val meta = CaptureMeta(
                timestampMs = ts,
                sessionName = sessionName,
                latitude = telemetry.latitude,
                longitude = telemetry.longitude,
                altitudeMslM = telemetry.altitudeMslM,
                altitudeAglM = telemetry.altitudeAglM,
                headingDeg = telemetry.headingDeg,
                palette = paletteName,
                tempMinC = tempMinC,
                tempMaxC = tempMaxC,
                autoRange = autoRange,
                useFahrenheit = useFahrenheit,
                simulated = simulated,
                measurements = measurements
            )
            val metaFile = File(dir, "meta_$ts.json")
            metaFile.writeText(gson.toJson(meta))

            CaptureEntry(ts, jpg, tiff, rgbFile, composite, metaFile)
        } catch (t: Throwable) {
            Log.e(TAG, "capture save failed (storage full / IO error?)", t)
            null
        }
    }

    private fun buildComposite(thermal: Bitmap, rgb: Bitmap?): Bitmap {
        if (rgb == null) return thermal
        val h = thermal.height
        val rgbScaled = Bitmap.createScaledBitmap(rgb, rgb.width * h / rgb.height, h, true)
        val out = Bitmap.createBitmap(thermal.width + rgbScaled.width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(rgbScaled, 0f, 0f, null)
        c.drawBitmap(thermal, rgbScaled.width.toFloat(), 0f, null)
        return out
    }

    suspend fun listSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
        val root = sessionsRoot()
        if (!root.exists()) return@withContext emptyList()
        root.listFiles { f -> f.isDirectory }?.map { dir ->
            val captures = listCaptures(dir)
            val firstMeta = captures.firstOrNull()?.metaJson?.let { readMeta(it) }
            SessionInfo(
                name = dir.name,
                path = dir.absolutePath,
                captureCount = captures.size,
                firstCaptureMs = captures.firstOrNull()?.timestampMs,
                latitude = firstMeta?.latitude,
                longitude = firstMeta?.longitude,
                thumbnailPath = captures.firstOrNull()?.jpg?.absolutePath
            )
        }?.sortedByDescending { it.firstCaptureMs ?: 0L } ?: emptyList()
    }

    fun listCaptures(dir: File): List<CaptureEntry> {
        val jpgs = dir.listFiles { f -> f.name.matches(Regex("thermal_\\d+\\.jpg")) } ?: return emptyList()
        return jpgs.mapNotNull { jpg ->
            val ts = jpg.name.removePrefix("thermal_").removeSuffix(".jpg").toLongOrNull() ?: return@mapNotNull null
            CaptureEntry(
                timestampMs = ts,
                jpg = jpg,
                rawTiff = File(dir, "thermal_${ts}_raw.tiff").takeIf { it.exists() },
                rgbJpg = File(dir, "rgb_$ts.jpg").takeIf { it.exists() },
                composite = File(dir, "composite_$ts.jpg").takeIf { it.exists() },
                metaJson = File(dir, "meta_$ts.json").takeIf { it.exists() }
            )
        }.sortedByDescending { it.timestampMs }
    }

    suspend fun listCapturesFor(sessionName: String): List<CaptureEntry> = withContext(Dispatchers.IO) {
        listCaptures(sessionDir(sessionName))
    }

    fun readMeta(file: File): CaptureMeta? = runCatching {
        gson.fromJson(file.readText(), CaptureMeta::class.java)
    }.getOrNull()

    suspend fun updateMeta(file: File, meta: CaptureMeta): Boolean = withContext(Dispatchers.IO) {
        runCatching { file.writeText(gson.toJson(meta)) }.isSuccess
    }

    fun loadRawFrame(entry: CaptureEntry): ThermalFrame? = entry.rawTiff?.let { TiffCodec.read(it) }

    fun loadBitmap(file: File): Bitmap? = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()

    suspend fun deleteSession(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { sessionDir(name).deleteRecursively() }.getOrDefault(false)
    }

    /** Latest composite JPEG across all sessions (for /snapshot). */
    suspend fun latestComposite(): File? = withContext(Dispatchers.IO) {
        sessionsRoot().walkTopDown()
            .filter { it.isFile && it.name.startsWith("composite_") && it.extension == "jpg" }
            .maxByOrNull { it.lastModified() }
    }

    /** Re-render a capture's JPG + composite with updated measurements (Inspect tab edits). */
    suspend fun rerenderCapture(entry: CaptureEntry, meta: CaptureMeta): Boolean = withContext(Dispatchers.IO) {
        try {
            val frame = loadRawFrame(entry) ?: return@withContext false
            val base = loadBitmap(entry.jpg) ?: return@withContext false
            // overlays are baked into jpg; re-render from raw via processor is done by caller.
            val withOverlays = OverlayRenderer.render(base, meta.measurements, meta.useFahrenheit)
            entry.jpg.outputStream().use { withOverlays.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            entry.composite?.outputStream()?.use { withOverlays.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "rerender failed", t)
            false
        }
    }
}
