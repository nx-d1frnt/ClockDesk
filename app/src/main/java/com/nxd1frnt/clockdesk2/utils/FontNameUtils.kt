package com.nxd1frnt.clockdesk2.utils

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

object FontNameUtils {

    fun getFontName(file: File): String? {
        return try {
            FileInputStream(file).use { extractName(it) }
        } catch (e: Exception) { null }
    }

    fun getFontName(openStream: () -> InputStream): String? {
        return try {
            openStream().use { extractName(it) }
        } catch (e: Exception) { null }
    }

    private fun extractName(stream: InputStream): String? {
        // Helper to read Big Endian types
        fun readShort(s: InputStream): Int {
            val b = ByteArray(2)
            if (s.read(b) != 2) throw Exception("EOF")
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).short.toInt()
        }
        fun readInt(s: InputStream): Int {
            val b = ByteArray(4)
            if (s.read(b) != 4) throw Exception("EOF")
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).int
        }
        fun skip(s: InputStream, n: Long) {
            var remaining = n
            while (remaining > 0) {
                val skipped = s.skip(remaining)
                if (skipped <= 0) {
                    // Fallback read
                    if (s.read() == -1) break
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }

        //Offset Table
        val header = ByteArray(12)
        if (stream.read(header) != 12) return null
        val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        b.int // scaler
        val numTables = b.short.toInt()

        // Find name table
        var nameTableOffset = -1

        // Current stream position
        var currentPos = 12L

        for (i in 0 until numTables) {
            val entry = ByteArray(16)
            if (stream.read(entry) != 16) return null
            currentPos += 16

            val bb = ByteBuffer.wrap(entry).order(ByteOrder.BIG_ENDIAN)
            val tag = bb.int

            if (tag == 0x6E616D65) { // 'name'
                bb.int // checksum
                nameTableOffset = bb.int
                break
            }
        }

        if (nameTableOffset == -1) return null

        //Jumping to 'name' table
        val toSkip = nameTableOffset - currentPos
        if (toSkip < 0) return null //The table was behind? Strange font.
        skip(stream, toSkip)

        // Parse 'name' table header
        // Format selector (2), Number of name records (2), Offset to string storage (2)
        val format = readShort(stream)
        val count = readShort(stream)
        val stringOffset = readShort(stream) // From start of name table

        // We need to read the records now
        // Each record is 12 bytes.
        var bestName: String? = null
        var bestPriority = 0 // Higher is better

        // String storage starts stringOffset bytes from the start of the table.
        // Records follow immediately after the header (6 bytes).
        // We will read the records, but the strings themselves are further ahead.
        // Reading InputStream "back and forth" is not possible.
        // Solution: Read all records into memory (count * 12 bytes - it's not much),
        // choose the best one, calculate its offset and jump there.

        data class NameRecord(val platformID: Int, val encodingID: Int, val languageID: Int, val nameID: Int, val length: Int, val offset: Int)

        val records = mutableListOf<NameRecord>()
        for (i in 0 until count) {
            val platformID = readShort(stream)
            val encodingID = readShort(stream)
            val languageID = readShort(stream)
            val nameID = readShort(stream)
            val length = readShort(stream)
            val offset = readShort(stream) // From string storage start

            records.add(NameRecord(platformID, encodingID, languageID, nameID, length, offset))
        }

        // Current position = 6 + count*12
        val currentOffsetInTable = 6 + count * 12

        // We are looking for the best name (Family Name = 1, Full Name = 4)
        // Priority: Windows (3) + Unicode (1) + English (1033)
        var targetRecord: NameRecord? = null

        // Simple selection heuristic
        for (rec in records) {
            if (rec.nameID == 4 || (rec.nameID == 1 && targetRecord?.nameID != 4)) {
                // Windows Platform
                if (rec.platformID == 3 && rec.languageID == 0x0409) { // English US
                    targetRecord = rec
                    break // Ideal match
                }
                // Mac Platform (Roman)
                if (rec.platformID == 1 && rec.languageID == 0) {
                    if (targetRecord == null || targetRecord.platformID != 3) {
                        targetRecord = rec
                    }
                }
            }
        }

        if (targetRecord == null) return null // No suitable name found

        // Reading the string
        // String offset from the start of the table = stringOffset + targetRecord.offset
        // We are currently at currentOffsetInTable
        val jumpToString = (stringOffset + targetRecord.offset) - currentOffsetInTable

        if (jumpToString < 0) return null
        skip(stream, jumpToString.toLong())

        val nameBytes = ByteArray(targetRecord.length)
        if (stream.read(nameBytes) != targetRecord.length) return null

        return try {
            if (targetRecord.platformID == 3) {
                String(nameBytes, Charset.forName("UTF-16BE"))
            } else {
                String(nameBytes, Charset.forName("UTF-8")) // Mac Roman is close enough to UTF-8 for our purposes
            }
        } catch (e: Exception) {
            null
        }
    }
}