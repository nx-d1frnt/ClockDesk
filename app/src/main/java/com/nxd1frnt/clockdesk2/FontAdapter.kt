package com.nxd1frnt.clockdesk2

import android.graphics.Typeface
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File

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
    private val onAddFontClicked: () -> Unit
) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {

    var selectedPosition: Int = -1

    class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

        holder.fontPreview.scaleX = 1f
        holder.fontPreview.scaleY = 1f

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
                holder.fontPreview.textSize = 24f

                holder.fontName.visibility = View.GONE

                holder.itemView.setOnClickListener { onAddFontClicked() }
                applySelectionBorder(holder.fontPreview, null)
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
                holder.fontPreview.textSize = 20f

                val borderDrawable = if (isSelected) {
                    ContextCompat.getDrawable(context, R.drawable.selected_font_border)
                } else {
                    null
                }

                applySelectionBorder(holder.fontPreview, borderDrawable)

                holder.itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = holder.adapterPosition

                    if (previousPosition != -1) notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)

                    (holder.itemView.parent as? RecyclerView)?.smoothScrollToPosition(selectedPosition)

                    onFontSelected(holder.adapterPosition)
                }
            }
        }
    }

    private fun applySelectionBorder(view: View, drawable: android.graphics.drawable.Drawable?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.foreground = drawable
        } else if (view is FrameLayout) {
            view.foreground = drawable
        } else {
            if (drawable != null) view.background = drawable else view.setBackgroundResource(R.drawable.item_font_card)
        }
    }

    override fun getItemCount(): Int = fonts.size
}