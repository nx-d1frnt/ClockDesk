package com.nxd1frnt.clockdesk2.ui.settings

import android.os.Bundle
import android.view.View
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import com.nxd1frnt.clockdesk2.utils.UpdateManager

class ChangelogHistoryFragment : BaseM3SettingsFragment() {

    private val expandedSet = mutableSetOf<String>()
    private var isLoading = false
    private var loadError: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHistory(force = false)
    }

    private fun loadHistory(force: Boolean = false) {
        isLoading = true
        loadError = null
        scheduleRebuild(0)

        UpdateManager.fetchReleaseHistory(requireContext(), force = force) { _, error ->
            if (isAdded && !isDetached) {
                isLoading = false
                loadError = error
                rebuildSettings()
            }
        }
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        val history = UpdateManager.releaseHistory

        if (isLoading && history.isEmpty()) {
            category(getString(R.string.changelog_history_title)) {
                action(
                    title = getString(R.string.loading_changelog_history),
                    summary = null
                ) {}
            }
            return@settings
        }

        if (loadError != null && history.isEmpty()) {
            category(getString(R.string.changelog_history_title)) {
                action(
                    title = getString(R.string.changelog_history_load_failed),
                    summary = loadError ?: "",
                    statusText = getString(R.string.update_retry_check)
                ) {
                    loadHistory(force = true)
                }
            }
            return@settings
        }

        if (history.isEmpty()) {
            category(getString(R.string.changelog_history_title)) {
                action(
                    title = getString(R.string.changelog_history_empty),
                    summary = getString(R.string.update_check_error_help)
                ) {}
            }
            return@settings
        }

        category(getString(R.string.changelog_history_title)) {
            for (release in history) {
                val isExpanded = expandedSet.contains(release.tagName)
                val summaryText = if (isExpanded) {
                    release.cleanNotes
                } else {
                    release.cleanNotes.lineSequence().take(4).joinToString("\n").take(240)
                }

                val fullSummary = if (release.date.isNotBlank()) {
                    "${release.date}\n\n$summaryText"
                } else {
                    summaryText
                }

                action(
                    title = release.name.ifBlank { release.tagName },
                    summary = fullSummary,
                    statusText = getString(if (isExpanded) R.string.update_show_less else R.string.update_show_more)
                ) {
                    if (isExpanded) {
                        expandedSet.remove(release.tagName)
                    } else {
                        expandedSet.add(release.tagName)
                    }
                    rebuildSettings()
                }
            }
        }
    }
}
