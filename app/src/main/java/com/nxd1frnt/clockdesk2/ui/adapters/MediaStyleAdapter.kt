package com.nxd1frnt.clockdesk2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.MediaStyle
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class MediaStyleAdapter(
    private val styles: List<MediaStyle>,
    private val onStyleSelected: (MediaStyle) -> Unit
) : RecyclerView.Adapter<MediaStyleAdapter.MediaStyleViewHolder>() {

    var selectedPosition: Int = 0

    class MediaStyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContainer: FrameLayout = itemView.findViewById(R.id.media_card_container)
        val minimalContainer: LinearLayout = itemView.findViewById(R.id.minimal_ticker_preview)
        val minimalText: TextView = itemView.findViewById(R.id.minimal_preview_text)
        val minimalIcon: ImageView = itemView.findViewById(R.id.minimal_preview_icon)

        val compactContainer: LinearLayout = itemView.findViewById(R.id.compact_card_preview)
        val compactTitle: TextView = itemView.findViewById(R.id.compact_preview_title)
        val compactArtist: TextView = itemView.findViewById(R.id.compact_preview_artist)

        val artworkContainer: FrameLayout = itemView.findViewById(R.id.artwork_player_preview)
        val expandedContainer: LinearLayout = itemView.findViewById(R.id.expanded_player_preview)
        val expandedTitle: TextView = itemView.findViewById(R.id.expanded_preview_title)
        val expandedArtist: TextView = itemView.findViewById(R.id.expanded_preview_artist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaStyleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_style, parent, false)
        return MediaStyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaStyleViewHolder, position: Int) {
        val style = styles[position]
        val context = holder.itemView.context
        holder.itemView.contentDescription = context.getString(style.titleRes)
        val isSelected = position == selectedPosition
        val palette = UiMonetThemeManager.getPalette(context)

        if (isSelected) {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_selected)
            (holder.cardContainer.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(palette.primary)
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.bg_clock_style_unselected)
            (holder.cardContainer.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(palette.surfaceContainer)
        }

        val textColor = if (isSelected) {
            palette.onPrimary
        } else {
            palette.onSurface
        }

        holder.minimalContainer.visibility = View.GONE
        holder.compactContainer.visibility = View.GONE
        holder.expandedContainer.visibility = View.GONE
        holder.artworkContainer.visibility = View.GONE

        when (style) {
            MediaStyle.MINIMAL_TICKER -> {
                holder.minimalContainer.visibility = View.VISIBLE
                holder.minimalText.setTextColor(textColor)
                holder.minimalIcon.setColorFilter(textColor)
            }
            MediaStyle.COMPACT_CARD -> {
                holder.compactContainer.visibility = View.VISIBLE
                holder.compactTitle.setTextColor(textColor)
                holder.compactArtist.setTextColor(textColor)
            }
            MediaStyle.ARTWORK_PLAYER -> {
                holder.artworkContainer.visibility = View.VISIBLE
            }
            MediaStyle.EXPANDED_PLAYER -> {
                holder.expandedContainer.visibility = View.VISIBLE
                holder.expandedTitle.setTextColor(textColor)
                holder.expandedArtist.setTextColor(textColor)
            }
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

    fun setSelectedStyle(style: MediaStyle) {
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
