package com.nxd1frnt.clockdesk2.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPixelManagerTest {

    @Test
    fun testBayer4x4MatrixValidity() {
        val matrix = SmartPixelManager.BAYER_4X4
        assertEquals(16, matrix.size)

        // Must contain all numbers from 0 to 15 with no duplicates
        val distinctValues = matrix.toSet()
        assertEquals(16, distinctValues.size)
        for (i in 0..15) {
            assertTrue("Matrix should contain $i", distinctValues.contains(i))
        }
    }

    @Test
    fun testBayerPatternCellDistributionAtPresets() {
        // Test 25% (should turn off 4 out of 16 cells)
        val black25 = Math.round((25f * 16f) / 100f).coerceIn(1, 15)
        assertEquals(4, black25)

        // Test 50% (should turn off 8 out of 16 cells)
        val black50 = Math.round((50f * 16f) / 100f).coerceIn(1, 15)
        assertEquals(8, black50)

        // Test 75% (should turn off 12 out of 16 cells)
        val black75 = Math.round((75f * 16f) / 100f).coerceIn(1, 15)
        assertEquals(12, black75)
    }

    @Test
    fun testCheckerboardEquivalenceAt50Percent() {
        val matrix = SmartPixelManager.BAYER_4X4
        val blackThreshold = 8 // 50% = 8 cells

        // Verify that at 50%, the pattern matches an alternating checkerboard
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val value = matrix[y * 4 + x]
                val isBlack = value < blackThreshold
                val expectedCheckerboard = (x + y) % 2 == 0
                assertEquals("Cell ($x, $y) should match checkerboard", expectedCheckerboard, isBlack)
            }
        }
    }
}
