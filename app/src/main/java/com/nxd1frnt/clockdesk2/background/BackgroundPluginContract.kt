package com.nxd1frnt.clockdesk2.background

import android.graphics.drawable.Drawable

/**
 * Contract for background provider plugins.
 * Similar to MusicPluginContract and ChipPluginContract.
 */

data class BackgroundProviderState(
    val id: String,
    val displayName: String,
    val previewDrawable: Drawable?,
    val data: Any? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class BackgroundPluginState {
    object Idle : BackgroundPluginState()
    object Loading : BackgroundPluginState()
    data class Ready(val state: BackgroundProviderState) : BackgroundPluginState()
    data class Error(val message: String) : BackgroundPluginState()
}

interface IBackgroundPlugin {
    val id: String
    val displayName: String
    val description: String
    
    /** Optional settings fragment for plugin configuration */
    val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>?
    
    /** Initialize the plugin */
    fun init()
    
    /** Destroy/cleanup the plugin */
    fun destroy()
    
    /** Set callback for state updates */
    fun setCallback(callback: (BackgroundPluginState) -> Unit)
    
    /** Request a background update (e.g., fetch new wallpaper) */
    fun requestUpdate()
    
    /** Get current background state synchronously if available */
    fun getCurrentState(): BackgroundProviderState?
}
