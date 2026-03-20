package com.nxd1frnt.clockdesk2.smartchips

import android.content.Context
import android.content.SharedPreferences
import android.view.View

interface ISmartChip {
    /**
     * The key from preferences.xml that enables/disables this chip.
     */
    val preferenceKey: String


    /**
     * Creates and returns the chip's View. This is called once.
     */
    fun createView(context: Context): View

    /**
     * Synchronously updates the chip's view.
     *
     * @return True if the chip should be visible, false if it should be hidden.
     */
    fun update(view: View, sharedPreferences: SharedPreferences): Boolean

    fun setOnStateChangeListener(listener: () -> Unit) {}

    fun startListening() {}
    fun stopListening() {}
}