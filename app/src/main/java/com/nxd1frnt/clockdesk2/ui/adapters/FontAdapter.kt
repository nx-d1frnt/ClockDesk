package com.nxd1frnt.clockdesk2.ui.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R

sealed class FontItem {
    abstract val name: String

    data class ResourceFont(val resId: Int, override val name: String) : FontItem()
    data class CustomFont(val path: String, override val name: String) : FontItem()

    object AddNew : FontItem() {
        override val name: String = "Add New"
    }
}

class FontAdapter(
    private val fonts: List<FontItem>,
    private val onFontSelected: (Int) -> Unit,
    private val onAddFontClicked: () -> Unit,
    private val onFontLongClick: (Int) -> Unit
) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {

    var selectedPosition: Int = -1

    class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContainer: FrameLayout = itemView.findViewById(R.id.font_card_container)
        val fontPreview: TextView = itemView.findViewById(R.id.font_preview)
        val fontName: TextView = itemView.findViewById(R.id.font_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_font, parent, false)
        return FontViewHolder(view)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val item = fonts[position]
        val context = holder.itemView.context
        val isSelected = position == selectedPosition

        // Selection background matching ClockStyleAdapter
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
        holder.fontPreview.setTextColor(textColor)

        if (isSelected && item !is FontItem.AddNew) {
            holder.fontName.visibility = View.VISIBLE
            holder.fontName.text = item.name
            holder.fontName.isSelected = true
        } else {
            holder.fontName.visibility = View.GONE
        }

        when (item) {
            is FontItem.AddNew -> {
                holder.fontPreview.text = "+"
                holder.fontPreview.typeface = Typeface.DEFAULT
                holder.fontPreview.textSize = 28f

                holder.itemView.setOnClickListener { onAddFontClicked() }
                holder.itemView.setOnLongClickListener(null)
            }
            else -> {
                val typeface = try {
                    when (item) {
                        is FontItem.ResourceFont -> ResourcesCompat.getFont(context, item.resId)
                        is FontItem.CustomFont -> Typeface.createFromFile(item.path)
                        else -> Typeface.DEFAULT
                    }
                } catch (e: Exception) { Typeface.DEFAULT }

                holder.fontPreview.typeface = typeface
                holder.fontPreview.text = "Aa"
                holder.fontPreview.textSize = 22f

                if (item is FontItem.CustomFont) {
                    holder.itemView.setOnLongClickListener {
                        onFontLongClick(holder.bindingAdapterPosition)
                        true
                    }
                } else {
                    holder.itemView.setOnLongClickListener(null)
                }

                holder.itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = holder.bindingAdapterPosition

                    if (previousPosition != -1) notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)

                    (holder.itemView.parent as? RecyclerView)?.smoothScrollToPosition(selectedPosition)

                    onFontSelected(holder.bindingAdapterPosition)
                }
            }
        }
    }

    override fun getItemCount(): Int = fonts.size

    fun setSelectedFontIndex(index: Int) {
        if (index >= 0 && index < fonts.size && index != selectedPosition) {
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