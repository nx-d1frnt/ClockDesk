package com.nxd1frnt.clockdesk2.utils

interface PowerSaveObserver {
    fun onPowerSaveModeChanged(isEnabled: Boolean)
}