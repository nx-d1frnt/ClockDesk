package com.nxd1frnt.clockdesk2.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testVersionComparisonWithHotfix() {
        // v2.0.0-rc2.1 should be recognized as newer than v2.0.0-rc2
        assertTrue(UpdateManager.isNewer("2.0.0-rc2.1", "2.0.0-rc2"))
        assertTrue(UpdateManager.isNewer("v2.0.0-rc2.1", "v2.0.0-rc2"))
        assertFalse(UpdateManager.isNewer("2.0.0-rc2", "2.0.0-rc2.1"))
    }

    @Test
    fun testReleaseCandidateProgression() {
        assertTrue(UpdateManager.isNewer("2.0.0-rc2", "2.0.0-rc1"))
        assertTrue(UpdateManager.isNewer("2.0.0-rc3", "2.0.0-rc2.1"))
        assertFalse(UpdateManager.isNewer("2.0.0-rc1", "2.0.0-rc2"))
    }

    @Test
    fun testBetaToRcProgression() {
        assertTrue(UpdateManager.isNewer("2.0.0-rc1", "2.0.0-beta4"))
        assertFalse(UpdateManager.isNewer("2.0.0-beta4", "2.0.0-rc1"))
    }

    @Test
    fun testStableOverPreRelease() {
        // Final stable release is newer than any rc/beta of the same version
        assertTrue(UpdateManager.isNewer("2.0.0", "2.0.0-rc2.1"))
        assertTrue(UpdateManager.isNewer("v2.0.0", "v2.0.0-rc2"))
        assertFalse(UpdateManager.isNewer("2.0.0-rc2.1", "2.0.0"))
    }

    @Test
    fun testEqualVersions() {
        assertFalse(UpdateManager.isNewer("2.0.0-rc2.1", "2.0.0-rc2.1"))
        assertFalse(UpdateManager.isNewer("v2.0.0", "2.0.0"))
    }

    @Test
    fun testNightlyBuildComparison() {
        // Official release vs nightly build of previous or same version
        assertTrue(UpdateManager.isNewer("2.0.0-rc2.1", "2.0.0-rc2-nightly-0802c91"))
        assertTrue(UpdateManager.isNewer("2.0.0-rc2", "2.0.0-rc2-nightly-0802c91"))
    }

    @Test
    fun testPatchVersionProgression() {
        assertTrue(UpdateManager.isNewer("2.0.1", "2.0.0"))
        assertTrue(UpdateManager.isNewer("2.1.0", "2.0.9"))
        assertTrue(UpdateManager.isNewer("3.0.0", "2.9.9"))
        assertFalse(UpdateManager.isNewer("2.0.0", "2.0.1"))
    }
}
