package com.nxd1frnt.clockdesk2.notifications.ui

import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.desks.DeskType
import com.nxd1frnt.clockdesk2.ui.MainActivity
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationHelper
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

internal class NotificationAdapter(
    private val context: Context,
    private val deskConnectManager: DeskConnectManager,
    private val onDismissClick: (DeskNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val items = mutableListOf<DeskNotification>()

    fun submitList(newItems: List<DeskNotification>) {
        val oldItems = ArrayList(items)
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = oldItems[oldItemPosition]
                val n = newItems[newItemPosition]
                return o.key == n.key
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = oldItems[oldItemPosition]
                val n = newItems[newItemPosition]
                return o == n
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItem(position: Int): DeskNotification = items[position]

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_shade, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, context, deskConnectManager, onDismissClick)
        UiMonetThemeManager.applyLiveToViewHierarchy(holder.itemView)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.shade_item_app_icon)
        private val headerText: TextView = itemView.findViewById(R.id.shade_item_header)
        private val titleText: TextView = itemView.findViewById(R.id.shade_item_title)
        private val bodyText: TextView = itemView.findViewById(R.id.shade_item_body)
        private val dismissBtn: ImageButton = itemView.findViewById(R.id.btn_dismiss_shade_item)

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                appIcon.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) {
                        val radius = v.context.resources.displayMetrics.density * 8f
                        outline.setRoundRect(0, 0, v.width, v.height, radius)
                    }
                }
                appIcon.clipToOutline = true
            }
        }

        fun bind(
            item: DeskNotification,
            context: Context,
            deskConnectManager: DeskConnectManager,
            onDismissClick: (DeskNotification) -> Unit
        ) {
            itemView.translationX = 0f
            itemView.alpha = 1f
            dismissBtn.isEnabled = true

            DeskNotificationHelper.applyIcon(context, appIcon, item.iconBytes, item.notificationId, iconRes = item.iconRes)

            val dev = deskConnectManager.discoveredDevices[item.relatedDeviceId ?: item.deviceId]
            val deviceName = dev?.getDisplayName()

            val timeStr = if (item.timestamp > 0) {
                DateUtils.getRelativeTimeSpanString(
                    item.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else {
                context.getString(R.string.notification_time_just_now)
            }

            val headerParts = mutableListOf<String>()
            if (item.appName.isNotEmpty()) headerParts.add(item.appName.uppercase())
            if (!deviceName.isNullOrEmpty()) headerParts.add(deviceName.uppercase())
            headerParts.add(timeStr.uppercase())

            headerText.text = headerParts.joinToString(" • ")
            titleText.text = item.title
            titleText.visibility = if (item.title.isNotEmpty()) View.VISIBLE else View.GONE

            bodyText.text = item.text
            bodyText.visibility = if (item.text.isNotEmpty()) View.VISIBLE else View.GONE

            dismissBtn.visibility = if (item.isClearable) View.VISIBLE else View.GONE
            dismissBtn.setOnClickListener {
                if (!item.isClearable) return@setOnClickListener
                dismissBtn.isEnabled = false
                onDismissClick(item)
            }
            itemView.setOnClickListener {
                when (item.alertType) {
                    AppAlertType.TIMER_FINISHED -> {
                        onDismissClick(item)
                        MainActivity.openDeskType(DeskType.TIMER)
                    }
                    AppAlertType.DEVICE_BATTERY_LOW -> MainActivity.openDeskType(DeskType.DEVICES)
                    null -> Unit
                }
            }
        }
    }
}

