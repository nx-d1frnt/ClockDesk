package com.nxd1frnt.clockdesk2.background.provider

data class ProviderCapabilities(
    val supportsBlur: Boolean = true,
    val supportsCrop: Boolean = true,
    val supportsDimming: Boolean = true,
    val supportsWeather: Boolean = true,
    val supportsCustomColor: Boolean = false,
    val supportsConfig: Boolean = false
)
