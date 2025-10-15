package com.nxd1frnt.clockdesk2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView

class FontAdapter( private val fonts: List<Int>, private val onFontSelected: (Int) -> Unit ) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {
    class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fontPreview: TextView = itemView.findViewById(R.id.font_preview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_font, parent, false)
        return FontViewHolder(view)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val fontId = fonts[position]
        val typeface = ResourcesCompat.getFont(holder.itemView.context, fontId)
        holder.fontPreview.typeface = typeface
        holder.fontPreview.text = "12"
        holder.itemView.setOnClickListener {
            onFontSelected(fontId)
        }
    }

    override fun getItemCount(): Int = fonts.size

}