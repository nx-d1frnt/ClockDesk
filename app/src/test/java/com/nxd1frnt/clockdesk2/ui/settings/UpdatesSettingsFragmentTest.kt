package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.M3SettingsAdapter
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import com.nxd1frnt.clockdesk2.utils.UpdateManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdatesSettingsFragmentTest {
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var activity: FragmentActivity
    private lateinit var fragment: UpdatesSettingsFragment

    private fun state(name: String, value: Any?) {
        UpdateManager::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(UpdateManager, value)
    }

    private fun resetState() {
        state("isChecking", false)
        state("isUpdateAvailable", false)
        state("lastError", null)
        state("latestVersion", null)
        state("releaseNotes", null)
        state("apkDownloadUrl", null)
        state("apkFileName", null)
        state("downloadState", UpdateManager.DownloadState.IDLE)
        state("downloadProgress", 0)
        state("downloadedBytes", 0L)
        state("totalBytes", 0L)
    }

    @Before
    fun setUp() {
        resetState()
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        controller.setup()
        fragment = UpdatesSettingsFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "updates").commitNow()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        UpdateManager.onUpdateStateChanged = null
        resetState()
    }

    private fun card() = fragment.buildSettings().filterIsInstance<SettingsItem.UpdateStatus>().single()

    @Test
    fun checkingDisablesActionEvenWithPreviouslyAvailableUpdate() {
        state("isUpdateAvailable", true)
        state("isChecking", true)
        assertEquals(activity.getString(R.string.update_status_checking), card().title)
        assertFalse(card().actionEnabled)
    }

    @Test
    fun failedCheckDoesNotHideBehindPreviouslyAvailableUpdate() {
        state("isUpdateAvailable", true)
        state("lastError", "Network error")
        assertEquals(activity.getString(R.string.update_status_error), card().title)
        assertEquals(activity.getString(R.string.update_retry_check), card().actionTitle)
        assertTrue(card().actionEnabled)
    }

    @Test
    fun downloadProgressAndCancelReturnToDownloadAction() {
        state("isUpdateAvailable", true)
        state("apkDownloadUrl", "https://example.com/update.apk")
        state("downloadState", UpdateManager.DownloadState.DOWNLOADING)
        state("totalBytes", 1024L * 1024L)
        state("downloadedBytes", 512L * 1024L)
        state("downloadProgress", 50)
        assertEquals(50, card().progress)
        assertFalse(card().actionEnabled)
        card().onCancel()
        assertFalse(card().downloading)
        assertEquals(activity.getString(R.string.download_and_install_title), card().actionTitle)
    }

    @Test
    fun unknownDownloadSizeRendersIndeterminateProgress() {
        state("isUpdateAvailable", true)
        state("downloadState", UpdateManager.DownloadState.DOWNLOADING)
        val item = card()
        val adapter = M3SettingsAdapter(activity,
            activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE), listOf(item))
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val progress = holder.itemView.findViewById<LinearProgressIndicator>(R.id.update_card_progress)
        assertEquals(View.VISIBLE, progress.visibility)
        assertTrue(progress.isIndeterminate)
        assertTrue(holder.itemView.findViewById<TextView>(R.id.update_card_details).text.isNotBlank())
    }

    @Test
    fun readyAndFailedDownloadsHaveDistinctActions() {
        state("isUpdateAvailable", true)
        state("downloadState", UpdateManager.DownloadState.READY_TO_INSTALL)
        assertEquals(activity.getString(R.string.install_update_action), card().actionTitle)
        state("downloadState", UpdateManager.DownloadState.FAILED)
        assertEquals(activity.getString(R.string.update_retry_download), card().actionTitle)
    }

    @Test
    fun releaseNotesExpandWithoutLosingTheirContents() {
        state("isUpdateAvailable", true)
        state("latestVersion", "3.0")
        val notes = "First change\nSecond change\nThird change\nFourth change"
        state("releaseNotes", notes)
        val preview = fragment.buildSettings().filterIsInstance<SettingsItem.Clickable>()
            .first { it.title == "3.0" }
        assertFalse(preview.summary!!.contains("Fourth change"))
        preview.onClick()
        val expanded = fragment.buildSettings().filterIsInstance<SettingsItem.Clickable>()
            .first { it.title == "3.0" }
        assertEquals(notes, expanded.summary)
    }

    @Test
    fun successfulCheckTimeSurvivesFailedAttemptAndIsScopedToChannel() {
        val settings = activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        settings.edit().putBoolean(UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, false).commit()
        activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).edit()
            .putLong("last_success_time", 1234L).putBoolean("last_success_channel", false)
            .putLong(UpdateManager.KEY_LAST_CHECK, 5678L).commit()
        state("lastError", "Network error")
        assertEquals(1234L, UpdateManager.getLastSuccessfulCheckTime(activity))
        settings.edit().putBoolean(UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, true).commit()
        assertEquals(0L, UpdateManager.getLastSuccessfulCheckTime(activity))
    }

    @Test
    fun settingsListenerDoesNotReplaceClockChipListener() {
        var chipNotifications = 0
        UpdateManager.onUpdateStateChanged = { chipNotifications++ }
        fragment.onPause()
        fragment.onResume()
        UpdateManager.cancelDownload(activity)
        assertEquals(1, chipNotifications)
        fragment.onPause()
        UpdateManager.cancelDownload(activity)
        assertEquals(2, chipNotifications)
    }

    @Test
    fun updateCardActionHasMonetColoring() {
        val item = card()
        val adapter = M3SettingsAdapter(
            activity,
            activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE),
            listOf(item)
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)

        val actionBtn = holder.itemView.findViewById<MaterialButton>(R.id.update_card_action)
        val palette = UiMonetThemeManager.getPalette(activity)

        val enabledStates = intArrayOf(android.R.attr.state_enabled)
        val disabledStates = intArrayOf(-android.R.attr.state_enabled)

        assertEquals(palette.primary, actionBtn.backgroundTintList?.getColorForState(enabledStates, 0))
        assertEquals(palette.onPrimary, actionBtn.textColors.getColorForState(enabledStates, 0))
    }

    @Test
    fun updateCardActionFollowsMonetPaletteWhenSeeded() {
        val seedColor = 0xffe91e63.toInt()
        activity.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(UiMonetThemeManager.KEY_DYNAMIC_SEED_COLOR, seedColor)
            .putBoolean(UiMonetThemeManager.KEY_UI_MONET_FROM_BACKGROUND, true)
            .commit()

        val item = card()
        val adapter = M3SettingsAdapter(
            activity,
            activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE),
            listOf(item)
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)

        val actionBtn = holder.itemView.findViewById<MaterialButton>(R.id.update_card_action)
        val palette = UiMonetThemeManager.getPalette(activity)

        val enabledStates = intArrayOf(android.R.attr.state_enabled)
        assertEquals(palette.primary, actionBtn.backgroundTintList?.getColorForState(enabledStates, 0))
        assertEquals(palette.onPrimary, actionBtn.textColors.getColorForState(enabledStates, 0))
    }
}
