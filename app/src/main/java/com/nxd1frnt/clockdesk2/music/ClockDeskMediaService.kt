package com.nxd1frnt.clockdesk2.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
            try {
                val notifications = service.activeNotifications
                val sbn = notifications?.firstOrNull { it.packageName == packageName }
                val notification = sbn?.notification ?: return null

                val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notification.smallIcon?.loadDrawable(context)
                } else {
                    @Suppress("DEPRECATION")
                    val resId = notification.icon
                    if (resId != 0) {
                        val pkgContext = context.createPackageContext(packageName, 0)
                        pkgContext.resources.getDrawable(resId)
                    } else null
                }

                return drawable?.let { drawableToBitmap(it) }
            } catch (e: Exception) {
                Logger.e("ClockDeskMediaService") { "Failed to extract icon for $packageName: ${e.message}" }
                return null
            }
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