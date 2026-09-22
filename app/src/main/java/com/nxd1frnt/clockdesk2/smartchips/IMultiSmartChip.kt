package com.nxd1frnt.clockdesk2.smartchips

import android.content.Context
import android.content.SharedPreferences
import android.view.View

data class MultiChipItem(
    val chipId: String,
    val text: String,
    val iconRes: Int? = null,
    val isVisible: Boolean = true
)

interface IMultiSmartChip : ISmartChip {
    /**
     * Returns the active chip items for this plugin.
     * Each item corresponds to an independently displayed smart chip sharing this plugin's preferenceKey channel.
     */
    fun getChips(sharedPreferences: SharedPreferences): List<MultiChipItem>

    override fun createView(context: Context): View {
        return View(context)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        return false
    }
}
