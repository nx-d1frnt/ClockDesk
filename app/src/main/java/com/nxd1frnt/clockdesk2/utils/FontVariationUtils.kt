package com.nxd1frnt.clockdesk2.utils

import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Font Variation Utils
 */
object FontVariationUtils {

    private const val TAG = "FontParser"
    private fun InputStream.skipSafe(n: Long) {
        if (n <= 0) return
        var remaining = n
        while (remaining > 0) {
            val skipped = this.skip(remaining)
            if (skipped <= 0) {
                // if skip is not working (e.g., in some streams)
                val buffer = ByteArray(minOf(remaining, 4096).toInt())
                val read = this.read(buffer)
                if (read == -1) break // End of stream
                remaining -= read
            } else {
                remaining -= skipped
            }
        }
    }

    fun scanAxes(openStream: () -> InputStream?): List<String> {
        val axesList = mutableListOf<String>()
        var fvarOffset = -1

        try {
            // PASS 1: scan fvar
            openStream()?.use { stream ->
                val header = ByteArray(12)
                if (stream.read(header) == 12) {
                    val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                    b.int // sfnt version
                    val numTables = b.short.toInt()
                    //Log.d(TAG, "Tables count: $numTables")

                    for (i in 0 until numTables) {
                        val entry = ByteArray(16)
                        if (stream.read(entry) != 16) break

                        val bb = ByteBuffer.wrap(entry).order(ByteOrder.BIG_ENDIAN)
                        val tag = bb.int
                        val checksum = bb.int
                        val offset = bb.int
                        val length = bb.int

                        // 'fvar' == 0x66766172
                        if (tag == 0x66766172) {
                            fvarOffset = offset
                            //Log.d(TAG, "Found 'fvar' table at offset: $fvarOffset, length: $length")
                            break
                        }
                    }
                }
            }

            if (fvarOffset == -1) {
                //Log.d(TAG, "No 'fvar' table found (Font is likely static)")
                return emptyList()
            }

            // PASS 2: Reading axes from fvar
            openStream()?.use { stream ->
                // Skip all bytes until the start of the fvar table
                stream.skipSafe(fvarOffset.toLong())

                // Read fvar (16 bytes)
                val fvarHeader = ByteArray(16)
                if (stream.read(fvarHeader) == 16) {
                    val bb = ByteBuffer.wrap(fvarHeader).order(ByteOrder.BIG_ENDIAN)
                    val major = bb.short
                    val minor = bb.short
                    val axesArrayOffset = bb.short.toInt() // from start of fvar
                    bb.short // reserved
                    val axisCount = bb.short.toInt()
                    val axisSize = bb.short.toInt()

                    //Log.d(TAG, "fvar header: axisCount=$axisCount, axisSize=$axisSize, axesOffset=$axesArrayOffset")

                    // We have already read 16 bytes of the fvar header.
                    // The offset axesArrayOffset is counted from the start of fvar..
                    val toSkip = axesArrayOffset - 16
                    if (toSkip > 0) {
                        stream.skipSafe(toSkip.toLong())
                    }

                    for (i in 0 until axisCount) {
                        // Reading Axis Record. Usually 20 bytes.
                        // We need the first 4 bytes (Tag).
                        val axisTagBytes = ByteArray(4)
                        if (stream.read(axisTagBytes) != 4) break

                        val tag = String(axisTagBytes, Charsets.US_ASCII)
                        axesList.add(tag)
                        //Log.d(TAG, "Found axis: $tag")

                        // Skip remaining bytes in the axis record
                        val remainingInRecord = axisSize - 4
                        if (remainingInRecord > 0) {
                            stream.skipSafe(remainingInRecord.toLong())
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing font: ${e.message}")
        }

        return axesList
    }
}