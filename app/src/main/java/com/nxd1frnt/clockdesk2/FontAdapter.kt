package com.nxd1frnt.clockdesk2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView

class FontAdapter(
    private val fonts: List<Int>,
    private val onFontSelected: (Int) -> Unit
) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {

    var selectedPosition: Int = -1

    class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fontPreview: TextView = itemView.findViewById(R.id.font_preview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_font, parent, false)
        return FontViewHolder(view)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val fontId = fonts[position]
        val context = holder.itemView.context
        val typeface = ResourcesCompat.getFont(context, fontId)

        holder.fontPreview.typeface = typeface
        holder.fontPreview.text = "12"

        if (position == selectedPosition) {
            holder.itemView.foreground = ContextCompat.getDrawable(context, R.drawable.selected_font_border)
        } else {
            holder.itemView.foreground = null
        }

        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)

            onFontSelected(fontId)
        }
    }

    override fun getItemCount(): Int = fonts.size
}