package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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

    @Test
    fun testParseChangelogWithDelimiters() {
        val markdown = """
            ### 🌙 ClockDesk Nightly Build
            - **Branch:** master
            - **Commit:** 1234567

            > [!WARNING]
            > Nightly builds are automated snapshots.

            ### What's Changed
            <!-- changelog-start -->
            #### Features
            - feat(Timer): add timer ([abc1234](https://github.com/nx-d1frnt/clockdesk/commit/abc1234))
            - feat(Theme): add Monet support ([def5678](https://github.com/nx-d1frnt/clockdesk/commit/def5678))

            #### Bug Fixes
            - fix(Desk): prevent crash on rotate ([9999999](https://github.com/nx-d1frnt/clockdesk/commit/9999999))
            <!-- changelog-end -->

            #### 📦 Downloads
            Download universal APK below.
            **SHA-256 Checksums:**
            ```text
            deadbeef1234 universal.apk
            ```
        """.trimIndent()

        val parsed = UpdateManager.parseChangelog(markdown)
        assertNotNull(parsed)
        assertFalse(parsed!!.contains("Downloads"))
        assertFalse(parsed.contains("deadbeef"))
        assertFalse(parsed.contains("abc1234](https"))
        assertFalse(parsed.contains("Nightly builds are automated"))
        assertTrue(parsed.contains("Features:"))
        assertTrue(parsed.contains("• feat(Timer): add timer"))
        assertTrue(parsed.contains("• feat(Theme): add Monet support"))
        assertTrue(parsed.contains("Bug Fixes:"))
        assertTrue(parsed.contains("• fix(Desk): prevent crash on rotate"))
    }

    @Test
    fun testParseChangelogFallbackExtraction() {
        val markdown = """
            ### 📦 ClockDesk v2.0.0
            Download the APK for your device architecture.

            ### What's Changed
            #### Features
            - feat(Weather): cache sunrise API ([1111111](https://github.com/nx-d1frnt/clockdesk/commit/1111111))

            #### Maintenance & Chores
            - chore: bump dependencies ([2222222](https://github.com/nx-d1frnt/clockdesk/commit/2222222))

            **Full Changelog**: https://github.com/nx-d1frnt/clockdesk/compare/v1.9.0...v2.0.0

            #### 🔐 SHA-256 Checksums
            ```text
            abcdef123456 release.apk
            ```
        """.trimIndent()

        val parsed = UpdateManager.parseChangelog(markdown)
        assertNotNull(parsed)
        assertFalse(parsed!!.contains("SHA-256"))
        assertFalse(parsed.contains("abcdef123456"))
        assertFalse(parsed.contains("Full Changelog"))
        assertFalse(parsed.contains("1111111](https"))
        assertTrue(parsed.contains("Features:"))
        assertTrue(parsed.contains("• feat(Weather): cache sunrise API"))
        assertTrue(parsed.contains("Maintenance & Chores:"))
        assertTrue(parsed.contains("• chore: bump dependencies"))
    }

    @Test
    fun testParseChangelogNullOrBlank() {
        assertNull(UpdateManager.parseChangelog(null))
        assertNull(UpdateManager.parseChangelog(""))
        assertNull(UpdateManager.parseChangelog("   \n  \t "))
    }

    @Test
    fun testParseChangelogRoutineMaintenanceFallback() {
        val withRoutineItem = """
            <!-- changelog-start -->
            - Routine maintenance and stability build.
            <!-- changelog-end -->
        """.trimIndent()
        assertEquals("• Routine maintenance and stability build.", UpdateManager.parseChangelog(withRoutineItem))

        val emptyInsideDelimiters = """
            <!-- changelog-start -->
            <!-- changelog-end -->
        """.trimIndent()
        assertEquals("Routine maintenance and stability build.", UpdateManager.parseChangelog(emptyInsideDelimiters))
    }

    @Test
    fun testSaveAndLoadCachedReleaseHistory() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val testItem = UpdateManager.ReleaseHistoryItem(
            tagName = "v2.0.0",
            name = "ClockDesk 2.0.0",
            date = "Oct 27, 2026",
            rawBody = "### What's Changed\n- feat: something",
            cleanNotes = "• feat: something",
            isPrerelease = false,
            htmlUrl = "https://github.com/nx-d1frnt/clockdesk/releases/tag/v2.0.0"
        )

        // Save into cache via reflection/private method invocation or direct helper
        val saveMethod = UpdateManager::class.java.getDeclaredMethod("saveReleaseHistoryCache", Context::class.java, List::class.java)
        saveMethod.isAccessible = true
        saveMethod.invoke(UpdateManager, activity, listOf(testItem))

        val loaded = UpdateManager.loadCachedReleaseHistory(activity)
        assertEquals(1, loaded.size)
        assertEquals("v2.0.0", loaded[0].tagName)
        assertEquals("ClockDesk 2.0.0", loaded[0].name)
        assertEquals("• feat: something", loaded[0].cleanNotes)
        assertFalse(loaded[0].isPrerelease)
    }
}
