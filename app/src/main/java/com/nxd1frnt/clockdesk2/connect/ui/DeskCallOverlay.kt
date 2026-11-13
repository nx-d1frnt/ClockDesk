package com.nxd1frnt.clockdesk2.connect.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BlurTransformation
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.concurrent.Executors

class DeskCallOverlay(
    private val context: Context,
    private val container: ViewGroup,
    private val deskConnectManager: DeskConnectManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val blurExecutor = Executors.newSingleThreadExecutor()
    private var isDestroyed = false
    private var currentCallView: View? = null

    private val telephonyListener = object : DeskConnectManager.TelephonyListener {
        override fun onCallStateChanged(
            deviceId: String,
            event: String,
            phoneNumber: String,
            contactName: String?,
            phoneThumbnailBase64: String?
        ) {
            mainHandler.post {
                if (isDestroyed) return@post
                val dev = deskConnectManager.discoveredDevices[deviceId]
                val deviceName = dev?.getDisplayName() ?: "Phone"
                val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

                when (event.lowercase()) {
                    "ringing" -> {
                        if (prefs.getBoolean("desk_call_overlay_enabled", true)) {
                            val displayName = contactName ?: phoneNumber
                            val subtitle = if (contactName != null) phoneNumber else ""
                            showIncomingCall(
                                deviceName = deviceName,
                                displayName = displayName,
                                subtitle = subtitle,
                                contactPhoto = null
                            )
                            if (!phoneThumbnailBase64.isNullOrEmpty() && !blurExecutor.isShutdown) {
                                blurExecutor.execute {
                                    val photoBitmap = decodeAndBlurPhoto(phoneThumbnailBase64)
                                    if (photoBitmap != null) {
                                        mainHandler.post {
                                            if (!isDestroyed && currentCallView != null) {
                                                updateCallInfo(deviceName, displayName, subtitle, photoBitmap)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "missedcall" -> {
                        hideCallOverlay()
                        if (prefs.getBoolean("desk_call_missed_notification", true)) {
                            val missedNotification = com.nxd1frnt.clockdesk2.connect.model.DeskNotification(
                                deviceId = deviceId,
                                notificationId = "call_missed_${System.currentTimeMillis()}",
                                appName = context.getString(R.string.pref_cat_phone_calls),
                                title = contactName ?: phoneNumber,
                                text = context.getString(R.string.missed_call),
                                timestamp = System.currentTimeMillis(),
                                iconBytes = null,
                                isClearable = true
                            )
                            com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository.getInstance(context).postNotification(missedNotification)
                        }
                    }
                    "talking", "idle", "canceled" -> {
                        hideCallOverlay()
                    }
                }
            }
        }
    }

    init {
        deskConnectManager.telephonyListeners.add(telephonyListener)
    }

    private fun showIncomingCall(
        deviceName: String,
        displayName: String,
        subtitle: String,
        contactPhoto: Bitmap? = null
    ) {
        if (currentCallView != null) {
            updateCallInfo(deviceName, displayName, subtitle, contactPhoto)
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.view_incoming_call, container, false)
        currentCallView = view

        val cardView = view.findViewById<MaterialCardView>(R.id.incoming_call_card)
        val bgPhotoView = view.findViewById<ImageView>(R.id.call_bg_photo)
        val bgScrimView = view.findViewById<View>(R.id.call_bg_scrim)
        val headerView = view.findViewById<TextView>(R.id.call_header)
        val callerNameView = view.findViewById<TextView>(R.id.caller_name)
        val callerNumberView = view.findViewById<TextView>(R.id.caller_number)

        headerView.text = "${context.getString(R.string.incoming_call)} • $deviceName"
        callerNameView.text = displayName
        if (subtitle.isNotEmpty()) {
            callerNumberView.text = subtitle
            callerNumberView.visibility = View.VISIBLE
        } else {
            callerNumberView.visibility = View.GONE
        }

        if (contactPhoto != null) {
            cardView.setCardBackgroundColor(Color.parseColor("#D9181818"))
            bgPhotoView.setImageBitmap(contactPhoto)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    bgPhotoView.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
                } catch (_: Exception) {}
            }
            bgPhotoView.visibility = View.VISIBLE
            bgScrimView.visibility = View.VISIBLE
        } else {
            cardView.setCardBackgroundColor(Color.parseColor("#E6102A16"))
            bgPhotoView.visibility = View.GONE
            bgScrimView.visibility = View.GONE
        }

        view.translationY = -140f
        view.alpha = 0f
        container.addView(view)

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun updateCallInfo(
        deviceName: String,
        displayName: String,
        subtitle: String,
        contactPhoto: Bitmap? = null
    ) {
        val view = currentCallView ?: return
        val cardView = view.findViewById<MaterialCardView>(R.id.incoming_call_card)
        val bgPhotoView = view.findViewById<ImageView>(R.id.call_bg_photo)
        val bgScrimView = view.findViewById<View>(R.id.call_bg_scrim)
        val headerView = view.findViewById<TextView>(R.id.call_header)
        val callerNameView = view.findViewById<TextView>(R.id.caller_name)
        val callerNumberView = view.findViewById<TextView>(R.id.caller_number)

        headerView.text = "${context.getString(R.string.incoming_call)} • $deviceName"
        callerNameView.text = displayName
        if (subtitle.isNotEmpty()) {
            callerNumberView.text = subtitle
            callerNumberView.visibility = View.VISIBLE
        } else {
            callerNumberView.visibility = View.GONE
        }

        if (contactPhoto != null) {
            cardView.setCardBackgroundColor(Color.parseColor("#D9181818"))
            bgPhotoView.setImageBitmap(contactPhoto)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    bgPhotoView.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
                } catch (_: Exception) {}
            }
            bgPhotoView.visibility = View.VISIBLE
            bgScrimView.visibility = View.VISIBLE
        }
    }

    private fun hideCallOverlay() {
        val viewToHide = currentCallView ?: return
        currentCallView = null

        viewToHide.animate()
            .translationY(-120f)
            .alpha(0f)
            .setDuration(350)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.removeView(viewToHide)
                }
            })
            .start()
    }

    private fun decodeAndBlurPhoto(phoneThumbnailBase64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(phoneThumbnailBase64, Base64.DEFAULT)
            val rawBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return null
            val mutable = rawBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            BlurTransformation.blurBitmapInPlace(context, mutable, radius = 22)
            mutable
        } catch (e: Exception) {
            Logger.e("DeskCallOverlay") { "Failed to decode/blur contact photo: ${e.message}" }
            null
        }
    }

    fun destroy() {
        isDestroyed = true
        blurExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        deskConnectManager.telephonyListeners.remove(telephonyListener)
        currentCallView?.let { container.removeView(it) }
        currentCallView = null
    }
}
