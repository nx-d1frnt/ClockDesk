package com.nxd1frnt.clockdesk2.ui.dashboard

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.PictureDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.view.SquigglySlider
import com.nxd1frnt.clockdesk2.utils.Logger

class DashboardAdapter(
    var tiles: List<DashboardTile> = emptyList(),
    private val onTileClick: (DashboardTile) -> Unit,
    private val onToggleChange: (DashboardTile, Boolean) -> Unit,
    private val onSliderChange: (DashboardTile, Float) -> Unit,
    private val onMediaControl: (DashboardTile, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isEditMode: Boolean = false
    var onEditClick: ((DashboardTile) -> Unit)? = null
    var onDeleteClick: ((DashboardTile) -> Unit)? = null


    companion object {
        private const val TYPE_INFO = 0
        private const val TYPE_TOGGLE = 1
        private const val TYPE_SLIDER = 2
        private const val TYPE_BUTTON = 3
        private const val TYPE_MEDIA_PLAYER = 4
        private const val TYPE_WEATHER_FORECAST = 5
        private const val TYPE_NATIVE_APK = 6
    }

    override fun getItemViewType(position: Int): Int {
        return when (tiles[position].type.uppercase()) {
            "INFO" -> TYPE_INFO
            "TOGGLE" -> TYPE_TOGGLE
            "SLIDER" -> TYPE_SLIDER
            "BUTTON" -> TYPE_BUTTON
            "MEDIA_PLAYER" -> TYPE_MEDIA_PLAYER
            "WEATHER_FORECAST" -> TYPE_WEATHER_FORECAST
            "NATIVE_APK" -> TYPE_NATIVE_APK
            else -> TYPE_INFO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_INFO -> InfoViewHolder(inflater.inflate(R.layout.item_dashboard_info, parent, false))
            TYPE_TOGGLE -> ToggleViewHolder(inflater.inflate(R.layout.item_dashboard_toggle, parent, false))
            TYPE_SLIDER -> SliderViewHolder(inflater.inflate(R.layout.item_dashboard_slider, parent, false))
            TYPE_BUTTON -> ButtonViewHolder(inflater.inflate(R.layout.item_dashboard_button, parent, false))
            TYPE_MEDIA_PLAYER -> MediaPlayerViewHolder(inflater.inflate(R.layout.item_dashboard_media_player, parent, false))
            TYPE_WEATHER_FORECAST -> WeatherViewHolder(inflater.inflate(R.layout.item_dashboard_weather_forecast, parent, false))
            TYPE_NATIVE_APK -> NativeViewHolder(inflater.inflate(R.layout.item_dashboard_native_apk, parent, false))
            else -> InfoViewHolder(inflater.inflate(R.layout.item_dashboard_info, parent, false))
        }
    }

    override fun getItemCount() = tiles.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tile = tiles[position]
        when (holder) {
            is InfoViewHolder -> holder.bind(tile)
            is ToggleViewHolder -> holder.bind(tile)
            is SliderViewHolder -> holder.bind(tile)
            is ButtonViewHolder -> holder.bind(tile)
            is MediaPlayerViewHolder -> holder.bind(tile)
            is WeatherViewHolder -> holder.bind(tile)
            is NativeViewHolder -> holder.bind(tile)
        }

        // Bind Edit Overlay Controls
        val editOverlay = holder.itemView.findViewById<View>(R.id.edit_overlay)
        val btnTileEdit = holder.itemView.findViewById<View>(R.id.btn_tile_edit)
        val btnTileDelete = holder.itemView.findViewById<View>(R.id.btn_tile_delete)

        if (editOverlay != null) {
            editOverlay.visibility = if (isEditMode) View.VISIBLE else View.GONE
            btnTileEdit?.setOnClickListener { onEditClick?.invoke(tile) }
            btnTileDelete?.setOnClickListener { onDeleteClick?.invoke(tile) }
        }
    }

    // --- Helper to bind icons based on priorities ---
    private fun bindIcon(imageView: ImageView, tile: DashboardTile, isToned: Boolean = false) {
        imageView.visibility = View.VISIBLE
        imageView.clearColorFilter()

        // Priority 1: iconUrl (Remote)
        if (!tile.iconUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(tile.iconUrl)
                .into(imageView)
            return
        }

        // Priority 2: localIcon (SVG from assets or filesDir)
        if (!tile.localIcon.isNullOrEmpty()) {
            try {
                val context = imageView.context
                val file = java.io.File(context.filesDir, "plugins/${tile.pluginId}/assets/${tile.localIcon}")
                val svg = if (file.exists()) {
                    file.inputStream().use { SVG.getFromInputStream(it) }
                } else {
                    val assetPath = "plugins/${tile.pluginId}/assets/${tile.localIcon}"
                    SVG.getFromAsset(context.assets, assetPath)
                }
                val drawable = PictureDrawable(svg.renderToPicture())
                imageView.setImageDrawable(drawable)
            } catch (e: Exception) {
                Logger.e("DashboardAdapter") { "Failed to load SVG ${tile.localIcon}: ${e.message}" }
                imageView.visibility = View.GONE
            }
            return
        }

        // Priority 3: icon (System)
        if (!tile.icon.isNullOrEmpty()) {
            val context = imageView.context
            val resId = context.resources.getIdentifier(tile.icon, "drawable", context.packageName)
            if (resId != 0) {
                imageView.setImageResource(resId)
                if (isToned) {
                    val color = ContextCompat.getColor(context, R.color.md_theme_onSurface)
                    imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                }
            } else {
                imageView.visibility = View.GONE
            }
            return
        }

        // Fallback: hide icon if none defined
        imageView.visibility = View.GONE
    }

    // --- ViewHolders ---

    inner class InfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconView = view.findViewById<ImageView>(R.id.icon)
        private val titleView = view.findViewById<TextView>(R.id.title)
        private val infoView = view.findViewById<TextView>(R.id.info)

        fun bind(tile: DashboardTile) {
            titleView.text = tile.title ?: "Info"
            infoView.text = tile.info ?: ""
            bindIcon(iconView, tile, isToned = true)
        }
    }

    inner class ToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view.findViewById<MaterialCardView>(R.id.card)
        private val iconView = view.findViewById<ImageView>(R.id.icon)
        private val titleView = view.findViewById<TextView>(R.id.title)

        fun bind(tile: DashboardTile) {
            titleView.text = tile.title ?: "Toggle"
            bindIcon(iconView, tile, isToned = true)

            card.isChecked = tile.state
            
            // Visual feedback for check state
            if (tile.state) {
                card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.md_theme_primaryContainer))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.md_theme_surfaceContainerHigh))
            }

            card.setOnClickListener {
                val newState = !tile.state
                tile.state = newState
                onToggleChange(tile, newState)
                
                // Animate change
                if (newState) {
                    card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.md_theme_primaryContainer))
                } else {
                    card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.md_theme_surfaceContainerHigh))
                }
                card.isChecked = newState
            }
        }
    }

    inner class SliderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconView = view.findViewById<ImageView>(R.id.icon)
        private val titleView = view.findViewById<TextView>(R.id.title)
        private val valueView = view.findViewById<TextView>(R.id.slider_value)
        private val slider = view.findViewById<Slider>(R.id.slider)

        fun bind(tile: DashboardTile) {
            titleView.text = tile.title ?: "Slider"
            bindIcon(iconView, tile, isToned = true)

            slider.valueFrom = tile.min
            slider.valueTo = tile.max
            slider.value = tile.value.coerceIn(tile.min, tile.max)
            
            valueView.text = "${tile.value.toInt()}%"

            slider.setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }

            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    slider.parent?.requestDisallowInterceptTouchEvent(true)
                }
                override fun onStopTrackingTouch(slider: Slider) {
                    slider.parent?.requestDisallowInterceptTouchEvent(false)
                    tile.value = slider.value
                    valueView.text = "${slider.value.toInt()}%"
                    onSliderChange(tile, slider.value)
                }
            })
        }
    }

    inner class ButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view.findViewById<MaterialCardView>(R.id.card)
        private val iconView = view.findViewById<ImageView>(R.id.icon)
        private val titleView = view.findViewById<TextView>(R.id.title)

        fun bind(tile: DashboardTile) {
            titleView.text = tile.title ?: "Action"
            bindIcon(iconView, tile, isToned = true)

            card.setOnClickListener {
                onTileClick(tile)
            }
        }
    }

    inner class MediaPlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bgAlbumArt = view.findViewById<ImageView>(R.id.media_bg_album_art)
        private val titleView = view.findViewById<TextView>(R.id.media_title)
        private val artistView = view.findViewById<TextView>(R.id.media_artist)
        private val progressSlider = view.findViewById<SquigglySlider>(R.id.media_progress_slider)
        private val btnPrev = view.findViewById<View>(R.id.media_btn_prev)
        private val btnPlayPause = view.findViewById<ImageView>(R.id.media_btn_play_pause)
        private val btnPlayPauseCard = view.findViewById<View>(R.id.media_btn_play_pause_card)
        private val btnNext = view.findViewById<View>(R.id.media_btn_next)
        private val btnLike = view.findViewById<View>(R.id.media_btn_like)
        private val btnRepeat = view.findViewById<View>(R.id.media_btn_repeat)
        private val sourceIcon = view.findViewById<ImageView>(R.id.media_source_icon)

        fun bind(tile: DashboardTile) {
            val title = tile.extraData?.get("trackTitle") as? String ?: tile.title ?: "No Media Playing"
            val artist = tile.extraData?.get("trackArtist") as? String ?: ""
            val isPlaying = tile.extraData?.get("isPlaying") as? Boolean ?: tile.state
            val progress = (tile.extraData?.get("progress") as? Number)?.toFloat() ?: 0f
            val maxProgress = (tile.extraData?.get("maxProgress") as? Number)?.toFloat() ?: 100f
            
            titleView.text = title
            artistView.text = artist
            
            progressSlider.valueFrom = 0f
            progressSlider.valueTo = maxOf(maxProgress, 1f)
            progressSlider.value = progress.coerceIn(0f, progressSlider.valueTo)
            progressSlider.isPlaying = isPlaying

            progressSlider.onSliderTouchListener = object : SquigglySlider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: SquigglySlider) {
                    slider.parent?.requestDisallowInterceptTouchEvent(true)
                }
                override fun onStopTrackingTouch(slider: SquigglySlider) {
                    slider.parent?.requestDisallowInterceptTouchEvent(false)
                    onMediaControl(tile, "seek:${slider.value.toLong()}")
                }
            }

            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }

            // Bind album art (priority: bitmap, url, fallback icon)
            val artworkBitmap = tile.extraData?.get("artworkBitmap") as? Bitmap
            val artworkUrl = tile.extraData?.get("artworkUrl") as? String

            when {
                artworkBitmap != null -> {
                    bgAlbumArt.setImageBitmap(artworkBitmap)
                }
                !artworkUrl.isNullOrEmpty() -> {
                    Glide.with(bgAlbumArt.context)
                        .load(artworkUrl)
                        .placeholder(R.drawable.ic_music_icon)
                        .into(bgAlbumArt)
                }
                else -> {
                    bgAlbumArt.setImageResource(R.drawable.ic_music_icon)
                }
            }

            // Bind source app icon
            if (sourceIcon != null) {
                var iconApplied = false
                sourceIcon.clipToOutline = false
                sourceIcon.clearColorFilter()
                
                val typedValue = android.util.TypedValue()
                sourceIcon.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                val tintColor = typedValue.data
                
                val sourcePackageName = tile.extraData?.get("sourcePackageName") as? String
                val sourceIconBitmap = tile.extraData?.get("sourceIconBitmap") as? Bitmap

                if (sourceIconBitmap != null) {
                    sourceIcon.setImageBitmap(sourceIconBitmap)
                    sourceIcon.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                    sourceIcon.imageAlpha = 255
                    iconApplied = true
                } else if (!sourcePackageName.isNullOrEmpty()) {
                    try {
                        val pm = sourceIcon.context.packageManager
                        val icon = pm.getApplicationIcon(sourcePackageName)
                        var monochromeDrawable: android.graphics.drawable.Drawable? = null
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is android.graphics.drawable.AdaptiveIconDrawable) {
                            monochromeDrawable = icon.monochrome
                        }
                        
                        if (monochromeDrawable != null) {
                            sourceIcon.setImageDrawable(monochromeDrawable)
                            sourceIcon.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                            sourceIcon.imageAlpha = 255
                        } else {
                            sourceIcon.setImageDrawable(icon)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                sourceIcon.outlineProvider = object : android.view.ViewOutlineProvider() {
                                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                        outline.setOval(0, 0, view.width, view.height)
                                    }
                                }
                                sourceIcon.clipToOutline = true
                            }
                            val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                            sourceIcon.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                            sourceIcon.imageAlpha = 200
                        }
                        iconApplied = true
                    } catch (e: Exception) {
                        Logger.w("DashboardAdapter") { "Couldn't find icon for package: $sourcePackageName" }
                    }
                }
                
                if (!iconApplied) {
                    sourceIcon.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                    sourceIcon.setImageDrawable(ContextCompat.getDrawable(sourceIcon.context, R.drawable.music_note))
                    sourceIcon.imageAlpha = 255
                }
            }

            btnPrev.setOnClickListener { onMediaControl(tile, "previous") }
            btnPlayPauseCard.setOnClickListener { onMediaControl(tile, "playPause") }
            btnNext.setOnClickListener { onMediaControl(tile, "next") }
            btnLike?.setOnClickListener { onMediaControl(tile, "like") }
            btnRepeat?.setOnClickListener { onMediaControl(tile, "repeat") }
        }
    }

    inner class WeatherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val currentTemp = view.findViewById<TextView>(R.id.weather_current_temp)
        private val currentIcon = view.findViewById<ImageView>(R.id.weather_current_icon)
        private val currentDesc = view.findViewById<TextView>(R.id.weather_current_desc)

        private val forecastDayNames = listOf(
            view.findViewById<TextView>(R.id.forecast_day_1_name),
            view.findViewById<TextView>(R.id.forecast_day_2_name),
            view.findViewById<TextView>(R.id.forecast_day_3_name),
            view.findViewById<TextView>(R.id.forecast_day_4_name)
        )
        private val forecastDayIcons = listOf(
            view.findViewById<ImageView>(R.id.forecast_day_1_icon),
            view.findViewById<ImageView>(R.id.forecast_day_2_icon),
            view.findViewById<ImageView>(R.id.forecast_day_3_icon),
            view.findViewById<ImageView>(R.id.forecast_day_4_icon)
        )
        private val forecastDayTemps = listOf(
            view.findViewById<TextView>(R.id.forecast_day_1_temp),
            view.findViewById<TextView>(R.id.forecast_day_2_temp),
            view.findViewById<TextView>(R.id.forecast_day_3_temp),
            view.findViewById<TextView>(R.id.forecast_day_4_temp)
        )

        fun bind(tile: DashboardTile) {
            val temp = tile.extraData?.get("temp") as? String ?: tile.info ?: "0°C"
            val desc = tile.extraData?.get("description") as? String ?: "No Weather Data"
            
            currentTemp.text = temp
            currentDesc.text = desc

            // Bind current condition icon
            bindIcon(currentIcon, tile)

            // Bind 4-day forecast
            val forecastList = tile.extraData?.get("forecast") as? List<Map<String, Any?>>
            if (forecastList != null && forecastList.size >= 4) {
                for (i in 0 until 4) {
                    val dayData = forecastList[i]
                    forecastDayNames[i].text = dayData["day"] as? String ?: ""
                    forecastDayTemps[i].text = dayData["temp"] as? String ?: ""

                    // Create sub-tile mock for bindIcon
                    val subTileIcon = dayData["icon"] as? String ?: "ic_weather_unknown"
                    val mockTile = DashboardTile(
                        id = "${tile.id}_f_$i",
                        type = "INFO",
                        pluginId = tile.pluginId,
                        icon = subTileIcon
                    )
                    bindIcon(forecastDayIcons[i], mockTile, isToned = false)
                }
            }
        }
    }

    inner class NativeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container = view.findViewById<FrameLayout>(R.id.native_view_container)

        fun bind(tile: DashboardTile) {
            container.removeAllViews()
            val nativeV = tile.nativeView
            if (nativeV != null) {
                // If the view has a parent, remove it first
                (nativeV.parent as? ViewGroup)?.removeView(nativeV)
                container.addView(nativeV)
            } else {
                val tv = TextView(container.context).apply {
                    text = "Native Widget Loading Failed"
                    setPadding(16, 16, 16, 16)
                }
                container.addView(tv)
            }
        }
    }
}
