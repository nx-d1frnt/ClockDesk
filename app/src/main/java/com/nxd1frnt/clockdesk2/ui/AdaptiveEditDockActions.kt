package com.nxd1frnt.clockdesk2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R

/** Chooses the least compact arrangement that fits the actual translated content. */
class AdaptiveEditDockActions @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private data class Action(val button: MaterialButton, val label: CharSequence, val iconPadding: Int)
    private var actions = emptyList<Action>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        actions = listOf(R.id.settings_button, R.id.demo_button, R.id.btn_add_widget,
            R.id.btn_reset_layout, R.id.btn_manage_desks, R.id.btn_done_edit).map { id ->
            val button = findViewById<MaterialButton>(id)
            button.contentDescription = button.text
            Action(button, button.text, button.iconPadding)
        }
        findViewById<View>(R.id.btn_edit_dock_more).setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                actions.filter { it.button.visibility == GONE }.forEach { action ->
                    menu.add(0, action.button.id, 0, action.label)
                }
                setOnMenuItemClickListener { item ->
                    actions.first { it.button.id == item.itemId }.button.performClick()
                }
                show()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (actions.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            Int.MAX_VALUE else MeasureSpec.getSize(widthMeasureSpec)
        for (mode in 0..4) {
            applyMode(mode)
            // Measure unconstrained first: a constrained row can conceal overflowing children.
            super.onMeasure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
            if (measuredWidth <= available) break
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun applyMode(mode: Int) {
        actions.forEach { action ->
            val button = action.button
            val hidden = (mode >= 2 && button.id in listOf(R.id.demo_button, R.id.btn_reset_layout)) ||
                (mode >= 4 && button.id in listOf(R.id.settings_button, R.id.btn_manage_desks))
            val iconOnly = mode >= 1 && button.id in listOf(R.id.settings_button,
                R.id.demo_button, R.id.btn_manage_desks, R.id.btn_done_edit) || mode >= 3
            button.visibility = if (hidden) GONE else VISIBLE
            button.text = if (iconOnly) "" else action.label
            button.iconPadding = if (iconOnly) 0 else action.iconPadding
            button.layoutParams.width = if (iconOnly) (48 * resources.displayMetrics.density).toInt()
                else LayoutParams.WRAP_CONTENT
            TooltipCompat.setTooltipText(button, if (iconOnly) action.label else null)
        }
        findViewById<View>(R.id.btn_edit_dock_more).visibility = if (mode >= 2) VISIBLE else GONE
        findViewById<View>(R.id.edit_dock_reset_space).visibility = if (mode >= 2) GONE else VISIBLE
    }
}
