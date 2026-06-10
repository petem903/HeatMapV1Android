package com.yanfeng.thermaldrone.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.yanfeng.thermaldrone.model.MeasurementType
import com.yanfeng.thermaldrone.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Batch export: ZIP / CSV / PDF report / share intent. All on Dispatchers.IO. */
class ExportManager(private val context: Context, private val sessions: SessionRepository) {
    companion object {
        private const val TAG = "ExportManager"
        private const val AUTHORITY = "com.yanfeng.thermaldrone1.fileprovider"
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun exportDir(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "HeatMapV1/exports").apply { mkdirs() }

    /** Session folder -> ZIP, folder structure preserved. */
    suspend fun exportZip(sessionName: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = sessions.sessionDir(sessionName)
            if (!dir.exists()) return@withContext null
            val out = File(exportDir(), "${sessionName}_${System.currentTimeMillis()}.zip")
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = "${dir.name}/${f.relativeTo(dir).invariantSeparatorsPath}"
                    zip.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "zip export failed", t); null
        }
    }

    /** CSV of every measurement value across the session. */
    suspend fun exportCsv(sessionName: String): File? = withContext(Dispatchers.IO) {
        try {
            val captures = sessions.listCapturesFor(sessionName)
            val out = File(exportDir(), "${sessionName}_measurements_${System.currentTimeMillis()}.csv")
            out.bufferedWriter().use { w ->
                w.appendLine("capture_ts,capture_time,type,label,annotation,points,min_c,avg_c,max_c,stdev_c,spot_c,extrema")
                for (cap in captures) {
                    val meta = cap.metaJson?.let { sessions.readMeta(it) } ?: continue
                    for (m in meta.measurements) {
                        val pts = m.points.joinToString(";") { "${it.x}:${it.y}" }
                        val ext = m.extrema.joinToString(";") { "${it.point.x}:${it.point.y}=${it.tempC}" }
                        w.appendLine(listOf(
                            meta.timestampMs.toString(),
                            dateFmt.format(Date(meta.timestampMs)),
                            m.type.name,
                            csv(m.label),
                            csv(m.annotation),
                            csv(pts),
                            m.lineStats?.minC ?: m.rectStats?.minC ?: "",
                            m.lineStats?.avgC ?: m.rectStats?.avgC ?: "",
                            m.lineStats?.maxC ?: m.rectStats?.maxC ?: "",
                            m.rectStats?.stdevC ?: "",
                            m.spotTempC ?: "",
                            csv(ext)
                        ).joinToString(","))
                    }
                }
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "csv export failed", t); null
        }
    }

    private fun csv(s: String): String = "\"${s.replace("\"", "\"\"")}\""

    /** PDF: cover page (drone + session info) + one page per capture (image + table). */
    suspend fun exportPdf(sessionName: String): File? = withContext(Dispatchers.IO) {
        try {
            val captures = sessions.listCapturesFor(sessionName).sortedBy { it.timestampMs }
            if (captures.isEmpty()) return@withContext null
            val out = File(exportDir(), "${sessionName}_report_${System.currentTimeMillis()}.pdf")
            PdfDocument(PdfWriter(out)).use { pdf ->
                Document(pdf, PageSize.A4).use { doc ->
                    // cover
                    doc.add(Paragraph("Thermal Inspection Report").setFontSize(28f).setBold().setTextAlignment(TextAlignment.CENTER))
                    doc.add(Paragraph("HeatMapV1Android v2.0").setTextAlignment(TextAlignment.CENTER))
                    doc.add(Paragraph("\n"))
                    val firstMeta = captures.firstOrNull { it.metaJson != null }?.metaJson?.let { sessions.readMeta(it) }
                    val cover = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f))).useAllAvailableWidth()
                    fun row(k: String, v: String) {
                        cover.addCell(Cell().add(Paragraph(k).setBold()))
                        cover.addCell(Cell().add(Paragraph(v)))
                    }
                    row("Drone", "Autel Robotics EVO Lite 640T Enterprise")
                    row("Thermal sensor", "640 × 512")
                    row("Session", sessionName)
                    row("Captures", captures.size.toString())
                    row("Generated", dateFmt.format(Date()))
                    firstMeta?.let {
                        row("Location", Format.gps(it.latitude, it.longitude))
                        if (it.simulated) row("Mode", "SIMULATED")
                    }
                    doc.add(cover)

                    // one page per capture
                    for (cap in captures) {
                        doc.add(AreaBreak())
                        val meta = cap.metaJson?.let { sessions.readMeta(it) }
                        doc.add(Paragraph("Capture ${dateFmt.format(Date(cap.timestampMs))}").setFontSize(16f).setBold())
                        val imgFile = cap.composite ?: cap.jpg
                        if (imgFile.exists()) {
                            val img = Image(ImageDataFactory.create(imgFile.absolutePath))
                            img.setAutoScale(true)
                            doc.add(img)
                        }
                        if (meta != null) {
                            doc.add(Paragraph(
                                "GPS ${Format.gps(meta.latitude, meta.longitude)}   " +
                                "Alt ${"%.1f".format(meta.altitudeAglM)} m AGL   " +
                                "Hdg ${"%.0f".format(meta.headingDeg)}°   " +
                                "Palette ${meta.palette}   " +
                                "Range ${Format.temp(meta.tempMinC, meta.useFahrenheit)} – ${Format.temp(meta.tempMaxC, meta.useFahrenheit)}"
                            ).setFontSize(9f))
                            if (meta.measurements.isNotEmpty()) {
                                val table = Table(UnitValue.createPercentArray(floatArrayOf(14f, 20f, 16f, 16f, 16f, 18f))).useAllAvailableWidth()
                                listOf("Type", "Label", "Min", "Avg", "Max", "Other").forEach {
                                    table.addHeaderCell(Cell().add(Paragraph(it).setBold().setFontSize(9f)).setBackgroundColor(ColorConstants.LIGHT_GRAY))
                                }
                                val f = meta.useFahrenheit
                                for (m in meta.measurements) {
                                    fun t(v: Float?) = v?.let { Format.temp(it, f) } ?: "—"
                                    val (mn, av, mx, other) = when (m.type) {
                                        MeasurementType.SPOT -> listOf("—", "—", "—", "spot ${t(m.spotTempC)}")
                                        MeasurementType.LINE -> listOf(t(m.lineStats?.minC), t(m.lineStats?.avgC), t(m.lineStats?.maxC), "${m.lineStats?.profile?.size ?: 0} px")
                                        MeasurementType.RECT -> listOf(t(m.rectStats?.minC), t(m.rectStats?.avgC), t(m.rectStats?.maxC), "σ ${"%.2f".format(m.rectStats?.stdevC ?: 0f)}")
                                        MeasurementType.AUTO_MAX, MeasurementType.AUTO_MIN ->
                                            listOf("—", "—", "—", m.extrema.joinToString(" ") { t(it.tempC) })
                                    }
                                    listOf(m.type.name, m.label.ifBlank { m.annotation }, mn, av, mx, other).forEach {
                                        table.addCell(Cell().add(Paragraph(it).setFontSize(8f)))
                                    }
                                }
                                doc.add(table)
                            }
                        }
                    }
                }
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "pdf export failed", t); null
        }
    }

    /** Share exported files via ACTION_SEND_MULTIPLE. */
    fun share(files: List<File>) {
        if (files.isEmpty()) return
        try {
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, AUTHORITY, it) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            Log.e(TAG, "share failed", t)
        }
    }
}
