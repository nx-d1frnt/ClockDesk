package com.nxd1frnt.clockdesk2.ui.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.view.DateTextView
import com.nxd1frnt.clockdesk2.utils.DateStyle
import java.util.Calendar

class DateStyleAdapter(
    private val styles: List<DateStyle>,
    private val onStyleSelected: (DateStyle) -> Unit
) : RecyclerView.Adapter<DateStyleAdapter.DateStyleViewHolder>() {

    var selectedPosition: Int = 0

    class DateStyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContainer: FrameLayout = itemView.findViewById(R.id.date_card_container)
        val twoLineContainer: LinearLayout = itemView.findViewById(R.id.two_line_container)
        val previewTop: TextView = itemView.findViewById(R.id.preview_top)
        val previewBottom: TextView = itemView.findViewById(R.id.preview_bottom)
        val singleLinePreview: TextView = itemView.findViewById(R.id.single_line_preview)
        val badgePreview: DateTextView = itemView.findViewById(R.id.badge_preview)
        val selectionDivider: View = itemView.findViewById(R.id.selection_divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateStyleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date_style, parent, false)
        return DateStyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateStyleViewHolder, position: Int) {
        val style = styles[position]
        val context = holder.itemView.context
        val isSelected = position == selectedPosition

        if (isSelected) {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_selected)
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_unselected)
        }

        val textColor = if (isSelected) {
            getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, R.color.clock_style_card_selected_text)
        } else {
            getThemeColor(context, com.google.android.material.R.attr.colorOnSurface, R.color.clock_style_card_unselected_text)
        }

        if (style.isBadge) {
            holder.twoLineContainer.visibility = View.GONE
            holder.singleLinePreview.visibility = View.GONE
            holder.badgePreview.visibility = View.VISIBLE
            holder.badgePreview.isBadgeMode = true
            holder.badgePreview.setTextColor(textColor)

            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, Calendar.OCTOBER)
                set(Calendar.DAY_OF_MONTH, 4)
            }
            holder.badgePreview.setDate(cal.time)
        } else if (style.isTwoLine) {
            holder.badgePreview.visibility = View.GONE
            holder.singleLinePreview.visibility = View.GONE
            holder.twoLineContainer.visibility = View.VISIBLE

            holder.previewTop.text = style.previewTop
            holder.previewBottom.text = style.previewBottom
            holder.previewTop.setTextColor(textColor)
            holder.previewBottom.setTextColor(textColor)

            val typeface = try {
                ResourcesCompat.getFont(context, R.font.googlesans_bold) ?: Typeface.DEFAULT_BOLD
            } catch (e: Exception) {
                Typeface.DEFAULT_BOLD
            }
            holder.previewTop.typeface = typeface
            holder.previewBottom.typeface = typeface
        } else {
            holder.badgePreview.visibility = View.GONE
            holder.twoLineContainer.visibility = View.GONE
            holder.singleLinePreview.visibility = View.VISIBLE

            holder.singleLinePreview.text = style.previewTop
            holder.singleLinePreview.setTextColor(textColor)
        }

        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition

            if (previousPosition != -1) notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)

            (holder.itemView.parent as? RecyclerView)?.smoothScrollToPosition(selectedPosition)
            onStyleSelected(style)
        }
    }

    override fun getItemCount(): Int = styles.size

    fun setSelectedStyle(style: DateStyle) {
        val index = styles.indexOf(style)
        if (index != -1 && index != selectedPosition) {
            val prev = selectedPosition
            selectedPosition = index
            if (prev != -1) notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
        }
    }

    private fun getThemeColor(context: android.content.Context, attrResId: Int, fallbackColorRes: Int): Int {
        val typedValue = android.util.TypedValue()
        if (context.theme.resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data
            }
        }
        return androidx.core.content.ContextCompat.getColor(context, fallbackColorRes)
    }
}
