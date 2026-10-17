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
    val previewResId: Int = 0,
    val previewResName: String? = null,
    var previewUri: String? = null,
    val iconResName: String? = null,
    private var lastPayload: BackgroundPayload? = null
) : IBackgroundProvider {

    fun getPluginIcon(context: Context): Drawable? {
        if (iconResId != 0 && iconResId != R.drawable.ic_layers) {
            try {
                val pluginContext = context.createPackageContext(packageName, 0)
                val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                    pluginContext.resources,
                    iconResId,
                    pluginContext.theme
                )
                if (d != null) return d
            } catch (_: Exception) {}
        }
        if (!iconResName.isNullOrEmpty()) {
            try {
                val pluginContext = context.createPackageContext(packageName, 0)
                val targetId = pluginContext.resources.getIdentifier(iconResName, "drawable", packageName)
                if (targetId != 0) {
                    val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                        pluginContext.resources,
                        targetId,
                        pluginContext.theme
                    )
                    if (d != null) return d
                }
            } catch (_: Exception) {}
        }
        try {
            return context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {}
        return ContextCompat.getDrawable(context, R.drawable.ic_layers)
    }

    fun updatePayload(payload: BackgroundPayload) {
        lastPayload = payload
    }

    fun updatePayload(context: Context, payload: BackgroundPayload) {
        lastPayload = payload
        BackgroundProviderManager.getInstance(context).savePayloadForId(id, payload)
    }

    override fun getIconDrawable(context: Context): Drawable? {
        return getPluginIcon(context)
    }

    override fun getPreviewDrawable(context: Context): Drawable? {
        if (previewResId != 0 || !previewResName.isNullOrEmpty()) {
            try {
                val pluginContext = context.createPackageContext(packageName, 0)
                val targetId = if (previewResId != 0) previewResId else {
                    pluginContext.resources.getIdentifier(previewResName, "drawable", packageName)
                }
                if (targetId != 0) {
                    val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                        pluginContext.resources,
                        targetId,
                        pluginContext.theme
                    )
                    if (d != null) return d
                }
            } catch (_: Exception) {}
        }
        val payload = getCurrentPayload(context)
        if (payload is BackgroundPayload.SolidColor) {
            return android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(payload.color)
            }
        }
        return getPluginIcon(context)
    }

    fun getPreviewModel(context: Context): Any? {
        if (!previewUri.isNullOrEmpty()) {
            return android.net.Uri.parse(previewUri)
        }
        val savedPreviewUri = BackgroundProviderManager.getInstance(context).getSavedPreviewUriForId(id)
        if (!savedPreviewUri.isNullOrEmpty()) {
            return android.net.Uri.parse(savedPreviewUri)
        }
        if (previewResId != 0 || !previewResName.isNullOrEmpty()) {
            try {
                val pluginContext = context.createPackageContext(packageName, 0)
                val targetId = if (previewResId != 0) previewResId else {
                    pluginContext.resources.getIdentifier(previewResName, "drawable", packageName)
                }
                if (targetId != 0) {
                    val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                        pluginContext.resources,
                        targetId,
                        pluginContext.theme
                    )
                    if (d != null) return d
                }
            } catch (_: Exception) {}
        }
        when (val payload = getCurrentPayload(context)) {
            is BackgroundPayload.ImageUri -> return android.net.Uri.parse(payload.uriString)
            is BackgroundPayload.ImageBitmap -> return payload.bitmap
            else -> {}
        }
        return null
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
