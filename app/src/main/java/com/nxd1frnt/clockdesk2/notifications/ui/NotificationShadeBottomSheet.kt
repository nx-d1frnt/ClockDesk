package com.nxd1frnt.clockdesk2.notifications.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
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
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationHelper

object NotificationShadeBottomSheet {

    fun show(context: Context) {
        var activityContext: Context? = context
        while (activityContext is ContextWrapper && activityContext !is Activity) {
            activityContext = activityContext.baseContext
        }
        val targetContext = activityContext ?: context
        val dialogContext = DynamicColors.wrapContextIfAvailable(targetContext)

        val dialog = BottomSheetDialog(dialogContext)
        val view = LayoutInflater.from(dialogContext).inflate(R.layout.bottom_sheet_notification_shade, null, false)
        dialog.setContentView(view)

        val displayMetrics = targetContext.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels

        // Maintain a consistent, well-proportioned height across all notification states (0, 1, 6+ items)
        val targetHeight = if (isLandscape) {
            (screenHeight * 0.78f).toInt()
        } else {
            (screenHeight * 0.65f).toInt()
        }

        fun applySheetHeight(sheet: View) {
            sheet.layoutParams?.let { lp ->
                lp.height = targetHeight
                sheet.layoutParams = lp
            }
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.skipCollapsed = true
            behavior.peekHeight = targetHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { applySheetHeight(it) }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let { applySheetHeight(it) }
        }

        val repository = DeskNotificationRepository.getInstance(targetContext)
        val deskConnectManager = DeskConnectManager.getInstance(targetContext)

        val recyclerView = view.findViewById<RecyclerView>(R.id.shade_recycler_view)
        val emptyState = view.findViewById<View>(R.id.shade_empty_state)
        val clearAllBtn = view.findViewById<MaterialButton>(R.id.btn_clear_all_notifications)
        val countBadge = view.findViewById<TextView>(R.id.shade_count_badge)

        var isClearingAll = false
        var isFirstLoad = true

        val adapter = NotificationAdapter(
            context = dialogContext,
            deskConnectManager = deskConnectManager,
            onDismissClick = { item ->
                repository.dismissNotification(item.deviceId, item.notificationId)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(dialogContext)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = NotificationItemAnimator().apply {
            removeDuration = 220
            moveDuration = 240
            addDuration = 220
            supportsChangeAnimations = false
        }

        // Swipe-to-dismiss setup with dynamic alpha fade and smooth completion
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = adapter.getItem(position)
                    repository.dismissNotification(item.deviceId, item.notificationId)
                }
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (isClearingAll) return 0
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = adapter.getItem(position)
                    if (!item.isClearable) return 0
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val width = viewHolder.itemView.width.toFloat()
                    val alpha = if (width > 0f) (1f - (Math.abs(dX) / (width * 0.8f))).coerceIn(0f, 1f) else 1f
                    viewHolder.itemView.alpha = alpha
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val view = viewHolder.itemView
                // If swipe was cancelled, restore full opacity and position
                if (Math.abs(view.translationX) < view.width * 0.4f) {
                    view.alpha = 1f
                    view.translationX = 0f
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)

        fun updateUI(list: List<DeskNotification>) {
            if (isClearingAll) return

            adapter.submitList(list)
            if (list.isEmpty()) {
                if (isFirstLoad) {
                    isFirstLoad = false
                    emptyState.visibility = View.VISIBLE
                    emptyState.alpha = 1f
                    emptyState.scaleX = 1f
                    emptyState.scaleY = 1f
                    recyclerView.visibility = View.GONE
                    countBadge.visibility = View.GONE
                    clearAllBtn.isEnabled = false
                    clearAllBtn.alpha = 0.5f
                    return
                }

                // Smoothly fade out list and scale/fade in empty state
                if (recyclerView.visibility == View.VISIBLE) {
                    recyclerView.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction {
                            recyclerView.visibility = View.GONE
                            recyclerView.alpha = 1f
                        }
                        .start()
                } else {
                    recyclerView.visibility = View.GONE
                }

                countBadge.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .alpha(0f)
                    .setDuration(160)
                    .withEndAction {
                        countBadge.visibility = View.GONE
                        countBadge.scaleX = 1f
                        countBadge.scaleY = 1f
                        countBadge.alpha = 1f
                    }
                    .start()

                clearAllBtn.isEnabled = false
                clearAllBtn.animate().alpha(0.5f).setDuration(180).start()

                emptyState.alpha = 0f
                emptyState.scaleX = 0.92f
                emptyState.scaleY = 0.92f
                emptyState.visibility = View.VISIBLE
                emptyState.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                isFirstLoad = false
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.alpha = 1f

                val hasClearable = list.any { it.isClearable }
                clearAllBtn.isEnabled = hasClearable
                clearAllBtn.animate().alpha(if (hasClearable) 1.0f else 0.5f).setDuration(150).start()

                val countStr = list.size.toString()
                if (countBadge.visibility != View.VISIBLE) {
                    countBadge.text = countStr
                    countBadge.alpha = 0f
                    countBadge.scaleX = 0.5f
                    countBadge.scaleY = 0.5f
                    countBadge.visibility = View.VISIBLE
                    countBadge.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                } else if (countBadge.text != countStr) {
                    countBadge.animate()
                        .scaleX(1.25f)
                        .scaleY(1.25f)
                        .setDuration(90)
                        .withEndAction {
                            countBadge.text = countStr
                            countBadge.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .start()
                        }
                        .start()
                }
            }
        }

        // Smooth staggered cascade clear-all animation
        clearAllBtn.setOnClickListener {
            if (isClearingAll) return@setOnClickListener
            val childCount = recyclerView.childCount
            if (childCount == 0) {
                repository.clearAllNotifications()
                return@setOnClickListener
            }

            isClearingAll = true
            clearAllBtn.isEnabled = false
            clearAllBtn.animate().alpha(0.3f).setDuration(180).start()
            countBadge.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(160).start()

            val baseDuration = 220L
            val staggerDelay = 35L
            val maxDelay = (childCount - 1) * staggerDelay

            for (i in 0 until childCount) {
                val child = recyclerView.getChildAt(i)
                child.animate()
                    .translationX(child.width.toFloat() * 1.1f)
                    .alpha(0f)
                    .setStartDelay(i * staggerDelay)
                    .setDuration(baseDuration)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }

            recyclerView.postDelayed({
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    child.translationX = 0f
                    child.alpha = 1f
                }
                isClearingAll = false
                repository.clearAllNotifications()
            }, maxDelay + baseDuration + 20L)
        }

        val listener = DeskNotificationRepository.OnNotificationsChangedListener { list ->
            updateUI(list)
        }

        repository.addListener(listener)
        dialog.setOnDismissListener {
            repository.removeListener(listener)
            if (isClearingAll) {
                repository.clearAllNotifications()
            }
        }

        dialog.show()
    }

    private class NotificationAdapter(
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
                    return o.deviceId == n.deviceId && o.notificationId == n.notificationId
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val o = oldItems[oldItemPosition]
                    val n = newItems[newItemPosition]
                    return o.title == n.title &&
                            o.text == n.text &&
                            o.timestamp == n.timestamp &&
                            o.isClearable == n.isClearable
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

                DeskNotificationHelper.applyIcon(context, appIcon, item.iconBytes, item.notificationId)

                val dev = deskConnectManager.discoveredDevices[item.deviceId]
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
            }
        }
    }

    private class NotificationItemAnimator : DefaultItemAnimator() {
        private val animatingHolders = mutableSetOf<RecyclerView.ViewHolder>()

        override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
            val view = holder.itemView
            // If already swiped away by ItemTouchHelper, finish immediately
            if (Math.abs(view.translationX) >= view.width * 0.4f) {
                dispatchRemoveStarting(holder)
                dispatchRemoveFinished(holder)
                return true
            }

            animatingHolders.add(holder)
            view.animate()
                .translationX(view.width.toFloat() * 1.05f)
                .alpha(0f)
                .setDuration(removeDuration)
                .setInterpolator(FastOutSlowInInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        dispatchRemoveStarting(holder)
                    }
                    override fun onAnimationEnd(animation: Animator) {
                        view.animate().setListener(null)
                        view.translationX = 0f
                        view.alpha = 1f
                        if (animatingHolders.remove(holder)) {
                            dispatchRemoveFinished(holder)
                        }
                    }
                })
                .start()
            return true
        }

        override fun endAnimation(holder: RecyclerView.ViewHolder) {
            if (animatingHolders.remove(holder)) {
                holder.itemView.animate().setListener(null).cancel()
                holder.itemView.translationX = 0f
                holder.itemView.alpha = 1f
                dispatchRemoveFinished(holder)
            }
            super.endAnimation(holder)
        }

        override fun endAnimations() {
            val toEnd = ArrayList(animatingHolders)
            animatingHolders.clear()
            for (holder in toEnd) {
                holder.itemView.animate().setListener(null).cancel()
                holder.itemView.translationX = 0f
                holder.itemView.alpha = 1f
                dispatchRemoveFinished(holder)
            }
            super.endAnimations()
        }

        override fun isRunning(): Boolean {
            return animatingHolders.isNotEmpty() || super.isRunning()
        }
    }
}
