package com.nxd1frnt.clockdesk2.smartchips

data class ExternalChipPlugin(
    val packageName: String,
    val receiverClassName: String,
    val preferenceKey: String,
    val displayName: String,
    val priority: Int
)