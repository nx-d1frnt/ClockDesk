package com.nxd1frnt.clockdesk2.smartchips.plugins

import com.nxd1frnt.clockdesk2.notifications.ui.NotificationShadeBottomSheet
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationHelper
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip

class NotificationSmartChipPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_notifications_chip"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false
    private val repository = DeskNotificationRepository.getInstance(context)

    private val notificationsListener = DeskNotificationRepository.OnNotificationsChangedListener {
        stateChangeListener?.invoke()
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        repository.addListener(notificationsListener)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        repository.removeListener(notificationsListener)
        isListening = false
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context).inflate(R.layout.smart_chip_notifications, null, false)
        view.setOnClickListener {
            val prefs = view.context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            val action = prefs.getString("notification_chip_tap_action", "shade")
            if (action == "clear") {
                val currentNotifications = repository.getNotifications()
                for (item in currentNotifications) {
                    repository.dismissNotification(item.deviceId, item.notificationId)
                }
                android.widget.Toast.makeText(view.context, R.string.notifications_cleared, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                NotificationShadeBottomSheet.show(view.context)
            }
        }
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val isEnabled = sharedPreferences.getBoolean(preferenceKey, true)
        if (!isEnabled) return false

        val notifications = repository.getNotifications()
        if (notifications.isEmpty()) {
            return false
        }

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val chipText = view.findViewById<TextView>(R.id.chip_text)

        val count = notifications.size
        val firstItem = notifications[0]
        val textColor = chipText.currentTextColor

        val useAppIcon = sharedPreferences.getBoolean("notification_chip_use_app_icon", true)
        if (useAppIcon) {
            DeskNotificationHelper.applyIcon(
                context,
                iconView,
                firstItem.iconBytes,
                firstItem.notificationId,
                textColor
            )
        } else {
            iconView.setImageResource(R.drawable.ic_notifications)
            iconView.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        val singleFormat = sharedPreferences.getString("notification_chip_single_format", "title")
        val multiFormat = sharedPreferences.getString("notification_chip_multi_format", "compact")

        val label = if (count == 1) {
            when (singleFormat) {
                "app" -> firstItem.appName.trim().ifBlank { "1" }
                "count" -> "1"
                else -> {
                    val title = firstItem.title.trim()
                    val app = firstItem.appName.trim()
                    when {
                        title.isNotBlank() -> title
                        app.isNotBlank() -> app
                        else -> "1"
                    }
                }
            }
        } else {
            when (multiFormat) {
                "label" -> context.resources.getQuantityString(R.plurals.notification_count, count, count)
                else -> "+$count"
            }
        }

        chipText.text = label
        chipText.isSelected = true

        return true
    }
}
