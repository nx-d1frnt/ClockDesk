package com.nxd1frnt.clockdesk2.music

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nxd1frnt.clockdesk2.utils.Logger

class ClockDeskMediaService : NotificationListenerService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: ClockDeskMediaService? = null

        fun getMediaIconBitmap(packageName: String, context: Context): Bitmap? {
            val service = instance ?: return null

            return runCatching {
                val notifications = service.activeNotifications
                val sbn = notifications?.firstOrNull { it.packageName == packageName }
                val notification = sbn?.notification ?: return@runCatching null

                val pkgContext = context.createPackageContext(packageName, 0)

                val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notification.smallIcon?.loadDrawable(pkgContext)
                } else {
                    @Suppress("DEPRECATION")
                    val resId = notification.icon
                    if (resId != 0) {
                        pkgContext.resources.getDrawable(resId, context.theme)
                    } else null
                }

                drawable?.let { drawableToBitmap(it) }
            }.onFailure { e ->
                if (e is PackageManager.NameNotFoundException) {
                    Logger.d("ClockDeskMediaService") { "Package visibility restriction for: $packageName" }
                } else if (e is SecurityException && e.message?.contains("unknown notification listener", ignoreCase = true) == true) {
                    Logger.w("ClockDeskMediaService") { "Listener proxy is dead (Android 11 quirk). Performing hard reset." }
                    instance = null
                    performHardReset(context)
                } else {
                    Logger.e("ClockDeskMediaService") { "Failed to extract icon for $packageName: ${e.message}" }
                }
            }.getOrNull() ?: getApplicationIconFallback(packageName, context) // Визуальный обход: если вернулся null или ошибка, берем иконку приложения
        }

        private fun performHardReset(context: Context) {
            runCatching {
                val componentName = ComponentName(context, ClockDeskMediaService::class.java)
                val pm = context.packageManager

                pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(componentName)
                }
            }
        }

        private fun getApplicationIconFallback(packageName: String, context: Context): Bitmap? {
            return runCatching {
                val iconDrawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(iconDrawable)
            }.getOrNull()
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable) {
                return drawable.bitmap
            }
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}