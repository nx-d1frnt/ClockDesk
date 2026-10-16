package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R

class ExternalBackgroundPlugin(
    val packageName: String,
    val receiverClassName: String,
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val iconResId: Int = R.drawable.ic_layers,
    val settingsActivity: String? = null,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(),
    private var lastPayload: BackgroundPayload? = null
) : IBackgroundProvider {

    fun updatePayload(payload: BackgroundPayload) {
        lastPayload = payload
    }

    fun updatePayload(context: Context, payload: BackgroundPayload) {
        lastPayload = payload
        BackgroundProviderManager.getInstance(context).savePayloadForId(id, payload)
    }

    override fun getPreviewDrawable(context: Context): Drawable? {
        val payload = getCurrentPayload(context)
        if (payload is BackgroundPayload.SolidColor) {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(payload.color)
            }
        }
        return ContextCompat.getDrawable(context, iconResId)
    }

    override fun getCurrentPayload(context: Context): BackgroundPayload {
        lastPayload?.let { return it }
        val saved = BackgroundProviderManager.getInstance(context).getSavedPayloadForId(id)
        lastPayload = saved
        return saved
    }

    fun getSettingsIntent(): android.content.Intent? {
        if (settingsActivity.isNullOrEmpty()) return null
        val targetClass = when {
            settingsActivity.startsWith(".") -> "$packageName$settingsActivity"
            !settingsActivity.contains(".") -> "$packageName.$settingsActivity"
            else -> settingsActivity
        }
        return android.content.Intent().apply {
            setClassName(packageName, targetClass)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
