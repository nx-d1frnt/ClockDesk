package com.nxd1frnt.clockdesk2.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.ByteArrayOutputStream

/**
 * External background plugin implementation.
 * Communicates with external apps via broadcast intents.
 */
class ExternalBackgroundPlugin(
    private val context: Context,
    override val id: String,
    override val displayName: String,
    override val description: String
) : IBackgroundPlugin {
    
    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null
    
    private var callback: ((BackgroundPluginState) -> Unit)? = null
    private var currentState: BackgroundProviderState? = null
    
    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BackgroundPluginManager.ACTION_UPDATE_STATE) {
                val senderPackage = intent.getStringExtra(BackgroundPluginManager.KEY_PACKAGE_NAME)
                if (senderPackage != id) return
                
                processUpdate(intent)
            }
        }
    }
    
    override fun init() {
        val filter = IntentFilter(BackgroundPluginManager.ACTION_UPDATE_STATE)
        ContextCompat.registerReceiver(
            context,
            dataReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }
    
    private fun processUpdate(intent: Intent) {
        val isReady = intent.getBooleanExtra(BackgroundPluginManager.KEY_IS_READY, false)
        
        if (isReady) {
            val bitmapBytes = intent.getByteArrayExtra(BackgroundPluginManager.KEY_BACKGROUND_URI)
            var drawable: BitmapDrawable? = null
            var bitmap: Bitmap? = null
            
            if (bitmapBytes != null) {
                bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.size)
                if (bitmap != null) {
                    drawable = BitmapDrawable(context.resources, bitmap)
                }
            }
            
            val state = BackgroundProviderState(
                id = id,
                displayName = displayName,
                previewDrawable = drawable,
                data = bitmap,
                isLoading = false
            )
            currentState = state
            callback?.invoke(BackgroundPluginState.Ready(state))
        } else {
            currentState = currentState?.copy(isLoading = false)
            callback?.invoke(BackgroundPluginState.Idle)
        }
    }
    
    override fun setCallback(callback: (BackgroundPluginState) -> Unit) {
        this.callback = callback
    }
    
    override fun requestUpdate() {
        // Request update from external plugin
        val intent = Intent("$id.REQUEST_UPDATE")
        intent.setPackage(id)
        context.sendBroadcast(intent)
    }
    
    override fun getCurrentState(): BackgroundProviderState? = currentState
    
    override fun destroy() {
        try {
            context.unregisterReceiver(dataReceiver)
        } catch (e: Exception) {
            Logger.e("ExternalBackgroundPlugin") { "Error unregistering receiver: ${e.message}" }
        }
    }
}
