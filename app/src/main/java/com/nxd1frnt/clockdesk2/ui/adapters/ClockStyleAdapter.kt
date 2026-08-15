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
import com.nxd1frnt.clockdesk2.ui.view.ClockTextView
import com.nxd1frnt.clockdesk2.utils.ClockStyle
import java.util.Calendar

class ClockStyleAdapter(
    private val styles: List<ClockStyle>,
    private val onStyleSelected: (ClockStyle) -> Unit
) : RecyclerView.Adapter<ClockStyleAdapter.ClockStyleViewHolder>() {

    var selectedPosition: Int = 0

    class ClockStyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContainer: FrameLayout = itemView.findViewById(R.id.clock_card_container)
        val twoLineContainer: LinearLayout = itemView.findViewById(R.id.two_line_container)
        val previewTop: TextView = itemView.findViewById(R.id.preview_top)
        val previewBottom: TextView = itemView.findViewById(R.id.preview_bottom)
        val singleLinePreview: TextView = itemView.findViewById(R.id.single_line_preview)
        val analogPreview: ClockTextView = itemView.findViewById(R.id.analog_preview)
        val selectionDivider: View = itemView.findViewById(R.id.selection_divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClockStyleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clock_style, parent, false)
        return ClockStyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClockStyleViewHolder, position: Int) {
        val style = styles[position]
        val context = holder.itemView.context
        val isSelected = position == selectedPosition

        // Selection background & divider
        if (isSelected) {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_selected)
            //holder.selectionDivider.visibility = View.VISIBLE
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_unselected)
           //holder.selectionDivider.visibility = View.GONE
        }

        val textColor = if (isSelected) {
            getThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, R.color.clock_style_card_selected_text)
        } else {
            getThemeColor(context, com.google.android.material.R.attr.colorOnSurface, R.color.clock_style_card_unselected_text)
        }

        if (style.isAnalog) {
            holder.twoLineContainer.visibility = View.GONE
            holder.singleLinePreview.visibility = View.GONE
            holder.analogPreview.visibility = View.VISIBLE
            holder.analogPreview.isAnalogMode = true
            holder.analogPreview.showBackdrop = style.hasBackdrop
            holder.analogPreview.setTextColor(textColor)

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 10)
                set(Calendar.SECOND, 0)
            }
            holder.analogPreview.setTime(cal.time)
        } else if (style.isTwoLine) {
            holder.analogPreview.visibility = View.GONE
            holder.singleLinePreview.visibility = View.GONE
            holder.twoLineContainer.visibility = View.VISIBLE

            holder.previewTop.text = style.previewTop
            holder.previewBottom.text = style.previewBottom
            holder.previewTop.setTextColor(textColor)
            holder.previewBottom.setTextColor(textColor)

            // Custom font previews for different two-line variants
            val typeface = try {
                when (style) {
                    ClockStyle.TWO_LINE -> ResourcesCompat.getFont(context, R.font.googlesans_bold) ?: Typeface.DEFAULT_BOLD
                    else -> Typeface.DEFAULT_BOLD
                }
            } catch (e: Exception) {
                Typeface.DEFAULT_BOLD
            }
            holder.previewTop.typeface = typeface
            holder.previewBottom.typeface = typeface
        } else {
            // Standard 1-line digital clock
            holder.analogPreview.visibility = View.GONE
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

    fun setSelectedStyle(style: ClockStyle) {
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
