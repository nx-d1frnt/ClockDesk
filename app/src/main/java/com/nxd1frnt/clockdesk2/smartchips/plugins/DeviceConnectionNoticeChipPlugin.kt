package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import java.util.ArrayDeque

class DeviceConnectionNoticeChipPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_device_connection_chip"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false
    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())

    private data class Notice(
        val deviceId: String,
        val deviceName: String,
        val iconRes: Int,
        val isConnected: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val noticeQueue = ArrayDeque<Notice>()
    private var currentNotice: Notice? = null

    private val dismissRunnable = Runnable {
        showNextNoticeOrDismiss()
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}

        override fun onDeviceConnected(device: DeskConnectDevice) {
            enqueueNotice(device, isConnected = true)
        }

        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            enqueueNotice(device, isConnected = false)
        }

        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}

        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (isPaired && device.isConnected) {
                enqueueNotice(device, isConnected = true)
            }
        }
    }

    private fun enqueueNotice(device: DeskConnectDevice, isConnected: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { enqueueNotice(device, isConnected) }
            return
        }

        val isPaired = device.isPaired || deskConnectManager.security.isDevicePaired(device.deviceId)
        if (!isPaired) return

        val notice = Notice(
            deviceId = device.deviceId,
            deviceName = device.getDisplayName(),
            iconRes = device.getDeviceTypeIconRes(),
            isConnected = isConnected
        )

        // Ignore if identical to the current notice within 2 seconds
        if (currentNotice?.deviceId == notice.deviceId &&
            currentNotice?.isConnected == notice.isConnected &&
            System.currentTimeMillis() - (currentNotice?.timestamp ?: 0) < 2000L) {
            return
        }

        // Drop oldest if queue exceeds max capacity
        while (noticeQueue.size >= 5) {
            noticeQueue.pollFirst()
        }

        if (currentNotice == null) {
            currentNotice = notice
            scheduleDismissal()
            stateChangeListener?.invoke()
        } else {
            noticeQueue.addLast(notice)
        }
    }

    private fun scheduleDismissal() {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, 4000L)
    }

    private fun showNextNoticeOrDismiss() {
        if (noticeQueue.isNotEmpty()) {
            currentNotice = noticeQueue.pollFirst()
            scheduleDismissal()
        } else {
            currentNotice = null
        }
        stateChangeListener?.invoke()
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        deskConnectManager.deviceListeners.add(deviceListener)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        deskConnectManager.deviceListeners.remove(deviceListener)
        handler.removeCallbacks(dismissRunnable)
        noticeQueue.clear()
        currentNotice = null
        isListening = false
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context).inflate(R.layout.smart_chip_layout, null, false)
        view.setOnClickListener {
            showNextNoticeOrDismiss()
        }
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val isEnabled = sharedPreferences.getBoolean(preferenceKey, true)
        if (!isEnabled) {
            currentNotice = null
            noticeQueue.clear()
            handler.removeCallbacks(dismissRunnable)
            return false
        }

        val notice = currentNotice ?: return false

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        if (iconView.tag != notice.iconRes) {
            iconView.setImageResource(notice.iconRes)
            iconView.tag = notice.iconRes
        }
        val formatRes = if (notice.isConnected) {
            R.string.device_connected_notice
        } else {
            R.string.device_disconnected_notice
        }
        val newText = context.getString(formatRes, notice.deviceName)
        if (textView.text.toString() != newText) {
            textView.text = newText
        }
        if (!textView.isSelected) {
            textView.isSelected = true
        }
        return true
    }
}
