package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.notifications.ui.NotificationAdapter
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class NotificationsDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val repository = DeskNotificationRepository.getInstance(context)
    private val adapter = NotificationAdapter(context, DeskConnectManager.getInstance(context)) {
        repository.dismissNotification(it.deviceId, it.notificationId)
    }
    private val recyclerView: RecyclerView
    private val emptyState: View
    private val countBadge: TextView
    private val clearAllButton: MaterialButton
    private val listener = DeskNotificationRepository.OnNotificationsChangedListener(::updateNotifications)

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_notifications_layout, this, true)
        recyclerView = findViewById(R.id.shade_recycler_view)
        emptyState = findViewById(R.id.shade_empty_state)
        countBadge = findViewById(R.id.shade_count_badge)
        clearAllButton = findViewById(R.id.btn_clear_all_notifications)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
        clearAllButton.setOnClickListener { repository.clearAllNotifications() }
        updateNotifications(repository.getNotifications())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMonetTheme()
        repository.addListener(listener)
    }

    override fun onDetachedFromWindow() {
        repository.removeListener(listener)
        recyclerView.itemAnimator?.endAnimations()
        super.onDetachedFromWindow()
    }

    private fun updateNotifications(notifications: List<DeskNotification>) {
        adapter.submitList(notifications)
        emptyState.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        countBadge.text = notifications.size.toString()
        countBadge.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        clearAllButton.isEnabled = notifications.any { it.isClearable }
        clearAllButton.alpha = if (clearAllButton.isEnabled) 1f else 0.5f
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        adapter.notifyDataSetChanged()
        DeskForegroundStyle.apply(this)
    }
}
