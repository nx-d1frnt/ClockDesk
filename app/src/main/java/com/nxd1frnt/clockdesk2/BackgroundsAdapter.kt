package com.nxd1frnt.clockdesk2

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class BackgroundsAdapter(
    private val context: Context,
    var items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<BackgroundsAdapter.VH>() {

    var selectedId: String? = null
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.background_thumbnail)
        val overlay: View = view.findViewById(R.id.selection_overlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_background, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val id = items[position]
        try {
            when (id) {
                "__ADD__" -> {
                    holder.thumb.setImageResource(R.drawable.baseline_add_photo_alternate_24)
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                "__DEFAULT_GRADIENT__" -> {
                    // Load app drawable gradient as preview
                    Glide.with(context)
                        .load(R.drawable.gradient_background)
                        .centerCrop()
                        .into(holder.thumb)
                }
                else -> {
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(context)
                        .load(Uri.parse(id))
                        .centerCrop()
                        .into(holder.thumb)
                }
            }
        } catch (e: Exception) {
            // ignore and leave empty thumbnail
        }
        holder.overlay.visibility = if (id == selectedId) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            onClick(id) // Call the original click handler (for previewing)
            updateSelection(id) // Update the visual selection
        }
        holder.itemView.setOnClickListener { onClick(id) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateSelection(newId: String?) {
        selectedId = newId
        notifyDataSetChanged() // This will re-run onBindViewHolder
    }
}
