package com.nxd1frnt.clockdesk2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetRegistry
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetType

class WidgetGallerySideSheetManager(
    private val sideSheetView: LinearLayout,
    private val mainLayout: View,
    private val backgroundCustomizationTab: View,
    private val widgetManager: DesktopWidgetManager,
    private val onWidgetAdded: (DesktopWidgetType) -> Unit,
    private val onSheetStateChanged: (isHidden: Boolean) -> Unit
) {
    private val behavior: SideSheetBehavior<LinearLayout> = SideSheetBehavior.from(sideSheetView)
    private val recyclerView by lazy { sideSheetView.findViewById<RecyclerView>(R.id.gallery_recycler_view) }
    private val closeButton by lazy { sideSheetView.findViewById<MaterialButton>(R.id.close_gallery_button) }

    private var calculatedTargetTx = 0f
    private var dynamicTargetScale = 0.65f

    init {
        setupBehavior()
        initControls()
    }

    private fun setupBehavior() {
        behavior.state = SideSheetBehavior.STATE_HIDDEN

        behavior.addCallback(object : SideSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == SideSheetBehavior.STATE_HIDDEN) {
                    onSheetStateChanged(true)
                    mainLayout.scaleX = 0.90f
                    mainLayout.scaleY = 0.90f
                    mainLayout.translationX = 0f
                    mainLayout.translationY = 0f
                    backgroundCustomizationTab.alpha = 1f
                    backgroundCustomizationTab.visibility = View.VISIBLE
                } else {
                    onSheetStateChanged(false)
                }
            }

            override fun onSlide(sheet: View, slideOffset: Float) {
                val safeOffset = slideOffset.coerceIn(0f, 1f)
                val baseScale = 0.90f

                sheet.alpha = safeOffset
                val sheetScale = 0.95f + (0.05f * safeOffset)
                sheet.scaleX = sheetScale
                sheet.scaleY = sheetScale

                val currentScale = baseScale - ((baseScale - dynamicTargetScale) * safeOffset)
                mainLayout.scaleX = currentScale
                mainLayout.scaleY = currentScale
                mainLayout.translationX = calculatedTargetTx * safeOffset

                backgroundCustomizationTab.alpha = 1f - safeOffset
                if (safeOffset < 1f && backgroundCustomizationTab.visibility == View.GONE) {
                    backgroundCustomizationTab.visibility = View.VISIBLE
                } else if (safeOffset == 1f && backgroundCustomizationTab.visibility == View.VISIBLE) {
                    backgroundCustomizationTab.visibility = View.GONE
                }
            }
        })
    }

    private fun calculateHorizontalShift() {
        val metrics = sideSheetView.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val isTablet = sideSheetView.resources.configuration.smallestScreenWidthDp >= 600
        dynamicTargetScale = if (isTablet) 0.85f else 0.65f

        val sheetW = if (sideSheetView.width > 0) sideSheetView.width.toFloat() else (380f * metrics.density)
        val visibleAreaCenter = (screenW - sheetW) / 2f
        val screenCenter = screenW / 2f
        calculatedTargetTx = (visibleAreaCenter - screenCenter).coerceIn(-screenW, screenW)
    }

    private fun initControls() {
        recyclerView.layoutManager = LinearLayoutManager(sideSheetView.context)
        recyclerView.isNestedScrollingEnabled = false

        closeButton.setOnClickListener {
            hide()
        }
    }

    fun show() {
        calculateHorizontalShift()
        recyclerView.adapter = WidgetPickerAdapter(DesktopWidgetRegistry.getAll()) { definition ->
            val added = widgetManager.addWidget(definition.type)
            if (added != null) {
                onWidgetAdded(definition.type)
                recyclerView.adapter?.notifyDataSetChanged()
                hide()
            }
        }
        behavior.state = SideSheetBehavior.STATE_EXPANDED
    }

    fun hide() {
        behavior.state = SideSheetBehavior.STATE_HIDDEN
    }

    val isShowing: Boolean
        get() = behavior.state != SideSheetBehavior.STATE_HIDDEN

    private inner class WidgetPickerAdapter(
        private val items: List<DesktopWidgetDefinition>,
        private val onSelect: (DesktopWidgetDefinition) -> Unit
    ) : RecyclerView.Adapter<WidgetPickerAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iconView: ImageView = itemView.findViewById(R.id.widget_icon)
            val titleView: TextView = itemView.findViewById(R.id.widget_title)
            val descView: TextView = itemView.findViewById(R.id.widget_description)
            val addButton: MaterialButton = itemView.findViewById(R.id.add_widget_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.titleView.setText(item.titleRes)
            holder.descView.setText(item.descriptionRes)
            holder.iconView.setImageResource(item.iconRes)

            val isAlreadyActive = !item.allowMultipleInstances && widgetManager.isWidgetTypeActive(item.type)
            if (isAlreadyActive) {
                holder.addButton.isEnabled = false
                holder.addButton.setText(R.string.widget_already_added)
                holder.addButton.icon = ContextCompat.getDrawable(sideSheetView.context, R.drawable.ic_check)
            } else {
                holder.addButton.isEnabled = true
                holder.addButton.setText(R.string.add_widget)
                holder.addButton.icon = ContextCompat.getDrawable(sideSheetView.context, R.drawable.ic_add)
                holder.addButton.setOnClickListener {
                    onSelect(item)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
