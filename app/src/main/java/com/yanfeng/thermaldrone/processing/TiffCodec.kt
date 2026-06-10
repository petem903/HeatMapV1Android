package com.yanfeng.thermaldrone.processing

import com.yanfeng.thermaldrone.model.ThermalFrame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal uncompressed 16-bit grayscale TIFF writer/reader (little-endian).
 * Encoding: uint16 = tempC * scale + offset (defaults 100 / 10000 => -100..555 °C).
 */
object TiffCodec {
    const val SCALE = 100f
    const val OFFSET = 10000f

    fun write(file: File, frame: ThermalFrame) {
        val w = frame.width; val h = frame.height
        val pixelBytes = w * h * 2
        val numEntries = 8
        val ifdOffset = 8
        val ifdSize = 2 + numEntries * 12 + 4
        val dataOffset = ifdOffset + ifdSize
        val buf = ByteBuffer.allocate(dataOffset + pixelBytes).order(ByteOrder.LITTLE_ENDIAN)
        // header
        buf.put('I'.code.toByte()); buf.put('I'.code.toByte()); buf.putShort(42); buf.putInt(ifdOffset)
        // IFD
        buf.putShort(numEntries.toShort())
        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            buf.putShort(tag.toShort()); buf.putShort(type.toShort()); buf.putInt(count); buf.putInt(value)
        }
        entry(256, 3, 1, w)                 // ImageWidth
        entry(257, 3, 1, h)                 // ImageLength
        entry(258, 3, 1, 16)                // BitsPerSample
        entry(259, 3, 1, 1)                 // Compression: none
        entry(262, 3, 1, 1)                 // Photometric: BlackIsZero
        entry(273, 4, 1, dataOffset)        // StripOffsets
        entry(278, 3, 1, h)                 // RowsPerStrip
        entry(279, 4, 1, pixelBytes)        // StripByteCounts
        buf.putInt(0)                       // next IFD = none
        // pixels
        for (t in frame.tempsC) {
            val v = (t * SCALE + OFFSET).toInt().coerceIn(0, 65535)
            buf.putShort(v.toShort())
        }
        file.outputStream().use { it.write(buf.array()) }
    }

    /** Reads TIFFs produced by [write]. Returns null on malformed input. */
    fun read(file: File): ThermalFrame? = runCatching {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.get() != 'I'.code.toByte() || buf.get() != 'I'.code.toByte()) return null
        if (buf.short != 42.toShort()) return null
        val ifdOffset = buf.int
        buf.position(ifdOffset)
        val n = buf.short.toInt()
        var w = 0; var h = 0; var stripOffset = 0
        repeat(n) {
            val tag = buf.short.toInt() and 0xFFFF
            buf.short // type
            buf.int   // count
            val value = buf.int
            when (tag) {
                256 -> w = value
                257 -> h = value
                273 -> stripOffset = value
            }
        }
        if (w <= 0 || h <= 0 || stripOffset <= 0) return null
        val temps = FloatArray(w * h)
        buf.position(stripOffset)
        for (i in temps.indices) {
            val v = buf.short.toInt() and 0xFFFF
            temps[i] = (v - OFFSET) / SCALE
        }
        ThermalFrame(w, h, temps, file.lastModified())
    }.getOrNull()

    /** In-memory encode (for tests). */
    fun writeToBytes(frame: ThermalFrame): ByteArray {
        val tmp = File.createTempFile("tiff", ".tiff")
        return try { write(tmp, frame); tmp.readBytes() } finally { tmp.delete() }
    }
}
