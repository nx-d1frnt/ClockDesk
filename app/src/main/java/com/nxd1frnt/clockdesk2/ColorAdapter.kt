package com.nxd1frnt.clockdesk2

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView

class ColorAdapter(
    private val items: List<ColorItem>,
    private var selectedColor: Int,
    private var useDynamic: Boolean,
    private var selectedRole: String?,
    private val onColorSelected: (ColorItem) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorPreview: View = itemView.findViewById(R.id.color_preview)
        val selectionRing: View = itemView.findViewById(R.id.selection_ring)
        val icon: ImageView = itemView.findViewById(R.id.color_icon)
    }

    fun updateSelection(newColor: Int, newUseDynamic: Boolean, newRole: String?) {
        this.selectedColor = newColor
        this.useDynamic = newUseDynamic
        this.selectedRole = newRole
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val background = ContextCompat.getDrawable(context, R.drawable.item_font_card) as? GradientDrawable
        val drawable = background?.constantState?.newDrawable()?.mutate() as? GradientDrawable

        var isSelected = false

        when (item) {
            is ColorItem.Dynamic -> {
                val color = item.color
                drawable?.setColor(color)

                holder.icon.visibility = View.VISIBLE
                holder.icon.setImageResource(R.drawable.ic_auto_awesome)

                val luminance = ColorUtils.calculateLuminance(color)
                holder.icon.setColorFilter(if (luminance > 0.5) Color.BLACK else Color.WHITE)

                isSelected = useDynamic && selectedRole == item.roleKey
            }
            is ColorItem.Solid -> {
                drawable?.setColor(item.color)
                holder.icon.visibility = View.GONE

                isSelected = (!useDynamic && selectedColor == item.color)
            }
            is ColorItem.AddNew -> {
                // Логика пикера
            }
        }

        holder.colorPreview.background = drawable
        // Set selection ring visibility
        if (isSelected) {
            holder.selectionRing.visibility = View.VISIBLE
            val ringColor = if (item is ColorItem.Dynamic) item.color else ContextCompat.getColor(context, R.color.md_theme_primary)
            (holder.selectionRing.background as? GradientDrawable)?.setStroke(6, ringColor)
        } else {
            holder.selectionRing.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onColorSelected(item)
        }
    }

    override fun getItemCount(): Int = items.size
}