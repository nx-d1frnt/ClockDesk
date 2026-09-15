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
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.utils.Logger
import kotlin.math.max
import kotlin.math.min

class DeskCallOverlay(
    private val context: Context,
    private val container: ViewGroup,
    private val deskConnectManager: DeskConnectManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
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
                val dev = deskConnectManager.discoveredDevices[deviceId]
                val deviceName = dev?.getDisplayName() ?: "Phone"

                var photoBitmap: Bitmap? = null
                if (!phoneThumbnailBase64.isNullOrEmpty()) {
                    try {
                        val decodedBytes = Base64.decode(phoneThumbnailBase64, Base64.DEFAULT)
                        val rawBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (rawBitmap != null) {
                            photoBitmap = blurBitmap(rawBitmap, radius = 22)
                        }
                    } catch (e: Exception) {
                        Logger.e("DeskCallOverlay") { "Failed to decode/blur contact photo: ${e.message}" }
                    }
                }

                when (event.lowercase()) {
                    "ringing" -> {
                        showIncomingCall(
                            deviceName = deviceName,
                            displayName = contactName ?: phoneNumber,
                            subtitle = if (contactName != null) phoneNumber else "",
                            contactPhoto = photoBitmap
                        )
                    }
                    "missedcall", "talking", "idle", "canceled" -> {
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

    private fun blurBitmap(src: Bitmap, radius: Int = 20): Bitmap {
        val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
        val clampedRadius = radius.coerceIn(1, 25)

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                var rs: RenderScript? = null
                var input: Allocation? = null
                var output: Allocation? = null
                var script: ScriptIntrinsicBlur? = null
                try {
                    rs = RenderScript.create(context)
                    input = Allocation.createFromBitmap(rs, mutable, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
                    output = Allocation.createTyped(rs, input.type)
                    script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                    script.setRadius(clampedRadius.toFloat())
                    script.setInput(input)
                    script.forEach(output)
                    output.copyTo(mutable)
                    return mutable
                } finally {
                    try { script?.destroy() } catch (_: Exception) {}
                    try { input?.destroy() } catch (_: Exception) {}
                    try { output?.destroy() } catch (_: Exception) {}
                    try { rs?.destroy() } catch (_: Exception) {}
                }
            }
        } catch (e: Throwable) {
            Logger.w("DeskCallOverlay") { "RenderScript blur exception: ${e.message}" }
        }

        return applyKotlinBoxBlur(mutable, clampedRadius)
    }

    private fun applyKotlinBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val passes = 3
        val r = max(1, radius / passes)
        val tempPixels = IntArray(w * h)

        for (pass in 0 until passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowStart = y * w
                for (x in 0 until w) {
                    val left = max(0, x - r)
                    val right = min(w - 1, x + r)
                    val kernelSize = right - left + 1

                    var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
                    for (i in left..right) {
                        val p = pixels[rowStart + i]
                        sumA += (p ushr 24) and 0xFF
                        sumR += (p ushr 16) and 0xFF
                        sumG += (p ushr 8) and 0xFF
                        sumB += p and 0xFF
                    }
                    tempPixels[rowStart + x] = ((sumA / kernelSize) shl 24) or
                            ((sumR / kernelSize) shl 16) or
                            ((sumG / kernelSize) shl 8) or
                            (sumB / kernelSize)
                }
            }

            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val top = max(0, y - r)
                    val bottom = min(h - 1, y + r)
                    val kernelSize = bottom - top + 1

                    var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
                    for (i in top..bottom) {
                        val p = tempPixels[i * w + x]
                        sumA += (p ushr 24) and 0xFF
                        sumR += (p ushr 16) and 0xFF
                        sumG += (p ushr 8) and 0xFF
                        sumB += p and 0xFF
                    }
                    pixels[y * w + x] = ((sumA / kernelSize) shl 24) or
                            ((sumR / kernelSize) shl 16) or
                            ((sumG / kernelSize) shl 8) or
                            (sumB / kernelSize)
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun destroy() {
        deskConnectManager.telephonyListeners.remove(telephonyListener)
        currentCallView?.let { container.removeView(it) }
        currentCallView = null
    }
}
