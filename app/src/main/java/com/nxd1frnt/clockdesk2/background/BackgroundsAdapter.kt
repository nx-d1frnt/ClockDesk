package com.nxd1frnt.clockdesk2.background

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class BackgroundsAdapter(
    private val context: Context,
    var items: List<String>,
    private val onClick: (String) -> Unit,
    private val onLongClick: (String) -> Unit,
    private val onSettingsClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<BackgroundsAdapter.VH>() {

    var selectedId: String? = null
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.background_thumbnail)
        val overlay: View = view.findViewById(R.id.selection_overlay)
        val settingsBtn: ImageView = view.findViewById(R.id.btn_settings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_background, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val id = items[position]
        val palette = UiMonetThemeManager.getPalette(holder.itemView.context)
        (holder.thumb.background?.mutate() as? GradientDrawable)?.setColor(palette.surfaceContainerHighest)
        holder.overlay.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 187))

        holder.settingsBtn.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.surfaceContainerHighest, 230))
        holder.settingsBtn.imageTintList = ColorStateList.valueOf(palette.onSurface)

        try {
            when (id) {
                "__ADD__" -> {
                    holder.settingsBtn.visibility = View.GONE
                    holder.settingsBtn.setOnClickListener(null)
                    holder.thumb.setImageResource(R.drawable.baseline_add_photo_alternate_24)
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    holder.thumb.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
                    holder.itemView.setOnLongClickListener(null) 
                }
                "__DEFAULT_GRADIENT__",
                com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID -> {
                    holder.settingsBtn.visibility = View.GONE
                    holder.settingsBtn.setOnClickListener(null)
                    holder.thumb.imageTintList = null
                    // Load app drawable gradient as preview
                    Glide.with(context)
                        .load(R.drawable.gradient_background)
                        .centerCrop()
                        .into(holder.thumb)
                    holder.itemView.setOnLongClickListener(null)
                }
                com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID -> {
                    holder.settingsBtn.visibility = View.VISIBLE
                    holder.settingsBtn.setOnClickListener {
                        onSettingsClick?.invoke(id)
                    }
                    val color = com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider().getColor(context)
                    holder.thumb.setImageDrawable(null)
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(color)
                    }
                    holder.thumb.background = bg
                    holder.thumb.setImageResource(R.drawable.ic_palette_swatch)
                    holder.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val isLight = ColorUtils.calculateLuminance(color) > 0.5
                    val iconTint = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    holder.thumb.imageTintList = ColorStateList.valueOf(iconTint)
                    holder.itemView.setOnLongClickListener {
                        onLongClick(id)
                        true
                    }
                }
                else -> {
                    if (id.startsWith("external:")) {
                        val plugin = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(context)
                            .getExternalProviders().firstOrNull { it.id == id }
                        val hasSettings = !plugin?.settingsActivity.isNullOrEmpty()
                        holder.settingsBtn.visibility = if (hasSettings) View.VISIBLE else View.GONE
                        if (hasSettings) {
                            holder.settingsBtn.setOnClickListener {
                                onSettingsClick?.invoke(id)
                            }
                        } else {
                            holder.settingsBtn.setOnClickListener(null)
                        }
                        val payload = plugin?.getCurrentPayload(context)
                        if (payload is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.SolidColor) {
                            holder.thumb.setImageDrawable(null)
                            val bg = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(payload.color)
                            }
                            holder.thumb.background = bg
                            val isLight = ColorUtils.calculateLuminance(payload.color) > 0.5
                            val iconTint = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                            holder.thumb.imageTintList = ColorStateList.valueOf(iconTint)
                        } else {
                            holder.thumb.imageTintList = ColorStateList.valueOf(palette.primary)
                        }
                        holder.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        holder.thumb.setImageResource(R.drawable.ic_layers)
                        holder.itemView.setOnLongClickListener {
                            onLongClick(id)
                            true
                        }
                    } else {
                        holder.settingsBtn.visibility = View.GONE
                        holder.settingsBtn.setOnClickListener(null)
                        holder.thumb.imageTintList = null
                        holder.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                        Glide.with(context)
                            .load(Uri.parse(id))
                            .centerCrop()
                            .into(holder.thumb)
                        holder.itemView.setOnLongClickListener {
                            onLongClick(id)
                            true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore and leave empty thumbnail
        }
        holder.overlay.visibility = if (id == selectedId) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            onClick(id)
            if (id != "__ADD__") {
                updateSelection(id)
            }
        }
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
