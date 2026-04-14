package com.nxd1frnt.clockdesk2.background

interface IBackgroundPlugin {
    val id: String
    val displayName: String
    val description: String

    val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>?

    fun init()
    fun destroy()
    fun setCallback(callback: (BackgroundState) -> Unit)
}

sealed class BackgroundState {
    data class Available(val url: String, val uri: String? = null) : BackgroundState()
    object Unavailable : BackgroundState()
    object Disabled : BackgroundState()
}
