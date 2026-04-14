package com.nxd1frnt.clockdesk2.background.plugins

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.background.BackgroundPluginContract
import com.nxd1frnt.clockdesk2.background.BackgroundState
import com.nxd1frnt.clockdesk2.background.IBackgroundPlugin

class ExternalBackgroundPlugin(
    private val context: Context,
    override val id: String,
    override val displayName: String,
    override val description: String
) : IBackgroundPlugin {

    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    private var callback: ((BackgroundState) -> Unit)? = null

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BackgroundPluginContract.ACTION_UPDATE_BACKGROUND) {
                val senderPackage = intent.getStringExtra(BackgroundPluginContract.KEY_PLUGIN_PACKAGE)
                if (senderPackage != id) return

                processUpdate(intent)
            }
        }
    }

    override fun init() {
        val filter = IntentFilter(BackgroundPluginContract.ACTION_UPDATE_BACKGROUND)
        ContextCompat.registerReceiver(
            context,
            dataReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun processUpdate(intent: Intent) {
        val isAvailable = intent.getBooleanExtra(BackgroundPluginContract.KEY_IS_AVAILABLE, false)

        if (isAvailable) {
            val url = intent.getStringExtra(BackgroundPluginContract.KEY_BACKGROUND_URL) ?: ""
            val uri = intent.getStringExtra(BackgroundPluginContract.KEY_BACKGROUND_URI)

            if (url.isNotEmpty()) {
                callback?.invoke(BackgroundState.Available(url, uri))
            } else {
                callback?.invoke(BackgroundState.Unavailable)
            }
        } else {
            callback?.invoke(BackgroundState.Unavailable)
        }
    }

    override fun setCallback(callback: (BackgroundState) -> Unit) {
        this.callback = callback
    }

    override fun destroy() {
        try {
            context.unregisterReceiver(dataReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
