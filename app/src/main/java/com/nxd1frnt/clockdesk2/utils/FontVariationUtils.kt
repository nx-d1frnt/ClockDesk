package com.nxd1frnt.clockdesk2.utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FontAxis(
    val tag: String,
    val minValue: Float,
    val defaultValue: Float,
    val maxValue: Float
)

/** * Font Variation Utils
 */
object FontVariationUtils {

    private const val TAG = "FontParser"
    private fun InputStream.skipSafe(n: Long) {
        if (n <= 0) return
        var remaining = n
        while (remaining > 0) {
            val skipped = this.skip(remaining)
            if (skipped <= 0) {
                val buffer = ByteArray(minOf(remaining, 4096).toInt())
                val read = this.read(buffer)
                if (read == -1) break
                remaining -= read
            } else {
                remaining -= skipped
            }
        }
    }

    fun scanAxes(openStream: () -> InputStream?): List<String> {
        return scanAxesDetails(openStream).map { it.tag }
    }

    fun scanAxesDetails(openStream: () -> InputStream?): List<FontAxis> {
        val axesList = mutableListOf<FontAxis>()
        var fvarOffset = -1

        try {
            openStream()?.use { stream ->
                val header = ByteArray(12)
                if (stream.read(header) == 12) {
                    val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                    b.int // sfnt version
                    val numTables = b.short.toInt()

                    for (i in 0 until numTables) {
                        val entry = ByteArray(16)
                        if (stream.read(entry) != 16) break

                        val bb = ByteBuffer.wrap(entry).order(ByteOrder.BIG_ENDIAN)
                        val tag = bb.int
                        bb.int // checksum
                        val offset = bb.int
                        bb.int // length

                        // 'fvar' == 0x66766172
                        if (tag == 0x66766172) {
                            fvarOffset = offset
                            break
                        }
                    }
                }
            }

            if (fvarOffset == -1) return emptyList()

            openStream()?.use { stream ->
                stream.skipSafe(fvarOffset.toLong())

                val fvarHeader = ByteArray(16)
                if (stream.read(fvarHeader) == 16) {
                    val bb = ByteBuffer.wrap(fvarHeader).order(ByteOrder.BIG_ENDIAN)
                    bb.short // major
                    bb.short // minor
                    val axesArrayOffset = bb.short.toInt()
                    bb.short // reserved
                    val axisCount = bb.short.toInt()
                    val axisSize = bb.short.toInt()

                    val toSkip = axesArrayOffset - 16
                    if (toSkip > 0) {
                        stream.skipSafe(toSkip.toLong())
                    }

                    for (i in 0 until axisCount) {
                        val axisRecordBytes = ByteArray(axisSize)
                        if (stream.read(axisRecordBytes) != axisSize) break

                        val recordBuf = ByteBuffer.wrap(axisRecordBytes).order(ByteOrder.BIG_ENDIAN)

                        // Tag (4 bytes)
                        val tagBytes = ByteArray(4)
                        recordBuf.get(tagBytes)
                        val tag = String(tagBytes, Charsets.US_ASCII)

                        // OpenType Fixed (16.16) point numbers -> Int / 65536.0f
                        val minValue = recordBuf.int / 65536f
                        val defaultValue = recordBuf.int / 65536f
                        val maxValue = recordBuf.int / 65536f

                        axesList.add(FontAxis(tag, minValue, defaultValue, maxValue))
                    }
                }
            }

        } catch (e: Exception) {
            Logger.e(TAG){"Error parsing font: ${e.message}"}
        }

        return axesList
    }
}