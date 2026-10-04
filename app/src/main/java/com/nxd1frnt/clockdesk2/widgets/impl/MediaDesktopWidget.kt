package com.nxd1frnt.clockdesk2.widgets.impl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.ui.view.SquigglyProgressBar
import com.nxd1frnt.clockdesk2.utils.MediaStyle
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost

class MediaDesktopWidget(
    instance: WidgetInstance,
    definition: DesktopWidgetDefinition,
    host: DesktopWidgetHost
) : DesktopWidgetController(instance, definition, host) {

    override var rootView: View? = null

    // Style 1: Minimal Ticker
    var mediaTickerLayout: View? = null
        private set
    var nowPlayingTextView: TextView? = null
        private set
    var lastfmIcon: ImageView? = null
        private set

    // Style 2: Compact Card
    var mediaCompactCard: MaterialCardView? = null
        private set
    var compactCoverArt: ImageView? = null
        private set
    var compactSourceIcon: ImageView? = null
        private set
    var compactTitleText: TextView? = null
        private set
    var compactArtistText: TextView? = null
        private set

    // Style 3: Expanded Player
    var mediaExpandedCard: MaterialCardView? = null
        private set
    var expandedCoverArt: ImageView? = null
        private set
    var expandedSourceIcon: ImageView? = null
        private set
    var expandedTitleText: TextView? = null
        private set
    var expandedArtistText: TextView? = null
        private set
    var expandedProgressBar: SquigglyProgressBar? = null
        private set
    var btnPrev: View? = null
        private set
    var btnPlayPause: ImageView? = null
        private set
    var btnNext: View? = null
        private set

    var lastTrackInfo: String? = null
        private set

    private var currentTrack: MusicTrack? = null
    private var isPlayingState: Boolean = false

    // Transport control callbacks
    var onPlayPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPrevAction: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pausedFadeOutRunnable: Runnable? = null
    private var progressTrackerRunnable: Runnable? = null

    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun bindView(view: View) {
        super.bindView(view)

        // 1. Minimal Ticker
        mediaTickerLayout = view.findViewById(R.id.media_ticker_layout)
        nowPlayingTextView = view.findViewById(R.id.now_playing_text)
        lastfmIcon = view.findViewById(R.id.lastfm_icon)
        nowPlayingTextView?.isSelected = true

        // 2. Compact Card
        mediaCompactCard = view.findViewById(R.id.media_compact_card)
        compactCoverArt = view.findViewById(R.id.compact_cover_art)
        compactSourceIcon = view.findViewById(R.id.compact_source_icon)
        compactTitleText = view.findViewById(R.id.compact_title_text)
        compactArtistText = view.findViewById(R.id.compact_artist_text)
        compactTitleText?.isSelected = true
        compactArtistText?.isSelected = true

        // 3. Expanded Player
        mediaExpandedCard = view.findViewById(R.id.media_expanded_card)
        expandedCoverArt = view.findViewById(R.id.expanded_cover_art)
        expandedSourceIcon = view.findViewById(R.id.expanded_source_icon)
        expandedTitleText = view.findViewById(R.id.expanded_title_text)
        expandedArtistText = view.findViewById(R.id.expanded_artist_text)
        expandedProgressBar = view.findViewById(R.id.expanded_progress_bar)
        btnPrev = view.findViewById(R.id.btn_media_prev)
        btnPlayPause = view.findViewById(R.id.btn_media_play_pause)
        btnNext = view.findViewById(R.id.btn_media_next)
        expandedTitleText?.isSelected = true
        expandedArtistText?.isSelected = true

        // Setup transport controls in Expanded mode
        btnPrev?.setOnClickListener {
            if (!host.isEditMode) onPrevAction?.invoke()
        }
        btnPlayPause?.setOnClickListener {
            if (!host.isEditMode) onPlayPauseAction?.invoke()
        }
        btnNext?.setOnClickListener {
            if (!host.isEditMode) onNextAction?.invoke()
        }

        // Setup gestures for Minimal and Compact modes (Single tap = Play/Pause, Double tap = Next)
        val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!host.isEditMode) {
                    onPlayPauseAction?.invoke()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!host.isEditMode) {
                    onNextAction?.invoke()
                    return true
                }
                return false
            }
        })

        val touchListener = View.OnTouchListener { _, event ->
            if (!host.isEditMode) {
                gestureDetector.onTouchEvent(event)
            } else {
                false
            }
        }

        mediaTickerLayout?.setOnTouchListener(touchListener)
        mediaCompactCard?.setOnTouchListener(touchListener)

        val currentStyle = host.fontManager?.getMediaStyle() ?: MediaStyle.MINIMAL_TICKER
        applyStyleVisibility(currentStyle)
    }

    fun updateMediaStyle(style: MediaStyle) {
        applyStyleVisibility(style)
        currentTrack?.let { populateTrackData(it, isPlayingState) }
    }

    private fun applyStyleVisibility(style: MediaStyle) {
        mediaTickerLayout?.visibility = if (style == MediaStyle.MINIMAL_TICKER) View.VISIBLE else View.GONE
        mediaCompactCard?.visibility = if (style == MediaStyle.COMPACT_CARD) View.VISIBLE else View.GONE
        mediaExpandedCard?.visibility = if (style == MediaStyle.EXPANDED_PLAYER) View.VISIBLE else View.GONE
    }

    override fun setEditMode(isEditMode: Boolean) {
        super.setEditMode(isEditMode)
        val layout = rootView ?: return
        if (isEditMode) {
            layout.animate().cancel()
            layout.clearAnimation()
            layout.visibility = View.VISIBLE
            layout.alpha = 1f
            layout.translationX = host.getRestTranslationX(layout)

            val currentStyle = host.fontManager?.getMediaStyle() ?: MediaStyle.MINIMAL_TICKER
            applyStyleVisibility(currentStyle)

            if (nowPlayingTextView?.text.isNullOrEmpty()) {
                val placeholder = host.hostContext.getString(R.string.now_playing_placeholder)
                nowPlayingTextView?.text = placeholder
                compactTitleText?.text = placeholder
                compactArtistText?.text = "Artist"
                expandedTitleText?.text = placeholder
                expandedArtistText?.text = "Artist"
            }
            val trackToPreview = currentTrack ?: MusicTrack(
                title = host.hostContext.getString(R.string.now_playing_placeholder),
                artist = "Artist",
                album = ""
            )
            updateSourceIcon(trackToPreview)
        } else {
            val isMusicActive = currentTrack != null && layout.alpha > 0

            if (!isMusicActive) {
                layout.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (!host.isEditMode) {
                                layout.visibility = View.GONE
                            }
                        }
                    })
                    .start()
            } else {
                layout.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setListener(null)
                    .start()
            }
        }
    }

    fun handleMusicStateUpdate(state: PluginState) {
        val layout = rootView ?: return
        if (!instance.isVisible) {
            layout.visibility = View.GONE
            layout.alpha = 0f
            return
        }

        val currentStyle = host.fontManager?.getMediaStyle() ?: MediaStyle.MINIMAL_TICKER
        applyStyleVisibility(currentStyle)

        if (host.isEditMode) {
            when (state) {
                is PluginState.Playing -> {
                    currentTrack = state.track
                    isPlayingState = true
                    populateTrackData(state.track, isPlaying = true)
                }
                is PluginState.Paused -> {
                    currentTrack = state.track
                    isPlayingState = false
                    populateTrackData(state.track, isPlaying = false)
                }
                else -> {
                    val placeholder = host.hostContext.getString(R.string.now_playing_placeholder)
                    nowPlayingTextView?.text = placeholder
                    compactTitleText?.text = placeholder
                    compactArtistText?.text = "Artist"
                    expandedTitleText?.text = placeholder
                    expandedArtistText?.text = "Artist"
                    lastTrackInfo = null
                    currentTrack = null
                }
            }
            return
        }

        when (state) {
            is PluginState.Playing -> {
                cancelPausedTimeout()
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                val isTextDifferent = trackInfoText != lastTrackInfo
                lastTrackInfo = trackInfoText
                currentTrack = track
                isPlayingState = true

                populateTrackData(track, isPlaying = true)
                startProgressTracker(track)

                if (isTextDifferent || layout.visibility != View.VISIBLE || layout.alpha < 1f) {
                    performTrackTransition(trackInfoText)
                }
            }

            is PluginState.Paused -> {
                stopProgressTracker()
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                lastTrackInfo = trackInfoText
                currentTrack = track
                isPlayingState = false

                populateTrackData(track, isPlaying = false)

                if (layout.visibility != View.VISIBLE) {
                    performTrackTransition(trackInfoText)
                }

                // 45-second inactivity timeout before fading to Idle
                schedulePausedTimeout()
            }

            is PluginState.Idle, is PluginState.Disabled -> {
                cancelPausedTimeout()
                stopProgressTracker()
                currentTrack = null
                performIdleTransition()
            }
        }
    }

    private fun schedulePausedTimeout() {
        cancelPausedTimeout()
        val runnable = Runnable {
            if (!host.isEditMode && !isPlayingState) {
                performIdleTransition()
            }
        }
        pausedFadeOutRunnable = runnable
        mainHandler.postDelayed(runnable, 45_000L)
    }

    private fun cancelPausedTimeout() {
        pausedFadeOutRunnable?.let { mainHandler.removeCallbacks(it) }
        pausedFadeOutRunnable = null
    }

    private fun populateTrackData(track: MusicTrack, isPlaying: Boolean) {
        val trackInfoText = "${track.artist} - ${track.title}"

        // 1. Minimal Ticker
        nowPlayingTextView?.text = trackInfoText
        nowPlayingTextView?.isSelected = true
        updateSourceIcon(track)

        // 2. Compact Card
        compactTitleText?.text = track.title.ifEmpty { trackInfoText }
        compactArtistText?.text = track.artist.ifEmpty { "Unknown Artist" }
        loadArtwork(track, compactCoverArt)

        // 3. Expanded Player
        expandedTitleText?.text = track.title.ifEmpty { trackInfoText }
        expandedArtistText?.text = track.artist.ifEmpty { "Unknown Artist" }
        loadArtwork(track, expandedCoverArt)

        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        currentDurationMs = track.durationMs ?: 0L
        currentPositionMs = track.positionMs ?: 0L
        updateProgressBarUI()
    }

    private fun loadArtwork(track: MusicTrack, targetView: ImageView?) {
        val view = targetView ?: return
        val context = host.hostContext

        if (track.artworkBitmap != null && !track.artworkBitmap.isRecycled) {
            view.setImageBitmap(track.artworkBitmap)
            view.imageTintList = null
            return
        }

        if (!track.artworkUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(track.artworkUrl)
                .placeholder(R.drawable.music_note)
                .error(R.drawable.music_note)
                .into(view)
            view.imageTintList = null
            return
        }

        if (track.sourceIconBitmap != null && !track.sourceIconBitmap.isRecycled) {
            view.setImageBitmap(track.sourceIconBitmap)
            view.imageTintList = null
            return
        }

        if (track.sourceIconResId != null) {
            view.setImageResource(track.sourceIconResId)
            return
        }

        view.setImageResource(R.drawable.music_note)
    }

    private fun startProgressTracker(track: MusicTrack) {
        stopProgressTracker()
        val duration = track.durationMs ?: return
        if (duration <= 0) return

        currentDurationMs = duration
        currentPositionMs = track.positionMs ?: 0L

        progressTrackerRunnable = object : Runnable {
            override fun run() {
                if (isPlayingState && currentDurationMs > 0) {
                    currentPositionMs = (currentPositionMs + 1000L).coerceAtMost(currentDurationMs)
                    updateProgressBarUI()
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.post(progressTrackerRunnable!!)
    }

    private fun stopProgressTracker() {
        progressTrackerRunnable?.let { mainHandler.removeCallbacks(it) }
        progressTrackerRunnable = null
    }

    private fun updateProgressBarUI() {
        val bar = expandedProgressBar ?: return
        bar.isPlaying = isPlayingState
        if (currentDurationMs > 0) {
            val progress = ((currentPositionMs.toFloat() / currentDurationMs.toFloat()) * 1000).toInt()
            bar.progress = progress.coerceIn(0, 1000)
            bar.visibility = View.VISIBLE
        } else {
            bar.progress = 0
            bar.visibility = View.GONE
        }
    }

    private fun performTrackTransition(trackInfoText: String) {
        val layout = rootView ?: return
        layout.animate().cancel()
        layout.animate().setListener(null)

        val baseX = host.getRestTranslationX(layout)

        if (layout.visibility != View.VISIBLE || layout.alpha < 1f) {
            val needsFadeIn = layout.visibility != View.VISIBLE
            layout.visibility = View.VISIBLE
            if (needsFadeIn) {
                layout.alpha = 0f
                layout.translationX = baseX + 10f
                layout.animate()
                    .alpha(1f)
                    .translationX(baseX)
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            layout.translationX = baseX
                        }
                        override fun onAnimationEnd(animation: Animator) {
                            layout.animate().setListener(null)
                            layout.translationX = baseX
                        }
                    })
                    .start()
            } else {
                layout.alpha = 1f
                layout.translationX = baseX
            }
        } else {
            var isTransitionCanceled = false
            layout.animate()
                .alpha(0f)
                .translationX(baseX - 10f)
                .setDuration(250)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        isTransitionCanceled = true
                        layout.translationX = baseX
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        layout.animate().setListener(null)
                        if (!isTransitionCanceled && !host.isEditMode) {
                            layout.translationX = baseX + 10f
                            layout.animate()
                                .alpha(1f)
                                .translationX(baseX)
                                .setDuration(250)
                                .setListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationCancel(animation: Animator) {
                                        layout.translationX = baseX
                                    }
                                    override fun onAnimationEnd(animation: Animator) {
                                        layout.animate().setListener(null)
                                        layout.translationX = baseX
                                    }
                                })
                                .start()
                        } else {
                            layout.translationX = baseX
                        }
                    }
                })
                .start()
        }
    }

    fun performIdleTransition() {
        val layout = rootView ?: return
        layout.animate().cancel()
        layout.animate().setListener(null)

        val baseX = host.getRestTranslationX(layout)
        if (layout.visibility == View.VISIBLE) {
            var isIdleCanceled = false
            layout.animate()
                .alpha(0f)
                .translationX(baseX + 10f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        isIdleCanceled = true
                        layout.translationX = baseX
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        layout.animate().setListener(null)
                        layout.translationX = baseX
                        if (!isIdleCanceled && !host.isEditMode) {
                            layout.visibility = View.GONE
                        }
                    }
                })
                .start()
        } else {
            layout.translationX = baseX
        }
    }

    fun updateSourceIcon(track: MusicTrack) {
        val showMediaIcon = host.getPreferences().getBoolean("show_media_icon", false)

        // Handle badges on Compact & Expanded styles
        if (!showMediaIcon) {
            compactSourceIcon?.visibility = View.GONE
            expandedSourceIcon?.visibility = View.GONE
        } else {
            compactSourceIcon?.let {
                it.visibility = View.VISIBLE
                applyIconToBadge(it, track)
            }
            expandedSourceIcon?.let {
                it.visibility = View.VISIBLE
                applyIconToBadge(it, track)
            }
        }

        // Handle Minimal Ticker icon
        val iconView = lastfmIcon ?: return
        if (!showMediaIcon) {
            iconView.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
            val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
            if (tintColor != null) {
                iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            iconView.visibility = View.VISIBLE
            return
        }

        var iconApplied = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconView.clipToOutline = false
        }

        if (track.sourceIconResId != null) {
            iconView.setImageDrawable(ContextCompat.getDrawable(host.hostContext, track.sourceIconResId))
            val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
            if (tintColor != null) {
                iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                iconView.imageAlpha = 255
            }
            iconApplied = true
        } else if (track.sourceIconBitmap != null) {
            iconView.setImageBitmap(track.sourceIconBitmap)
            val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
            if (tintColor != null) {
                iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                iconView.imageAlpha = 255
            }
            iconApplied = true
        } else if (!track.sourcePackageName.isNullOrEmpty()) {
            try {
                val icon = host.hostContext.packageManager.getApplicationIcon(track.sourcePackageName)
                var monochromeDrawable: Drawable? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is AdaptiveIconDrawable) {
                    monochromeDrawable = icon.monochrome
                }

                if (monochromeDrawable != null) {
                    iconView.setImageDrawable(monochromeDrawable)
                    val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
                    if (tintColor != null) {
                        iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        iconView.imageAlpha = 255
                    }
                } else {
                    iconView.setImageDrawable(icon)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        iconView.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(v: View, outline: Outline) {
                                outline.setOval(0, 0, v.width, v.height)
                            }
                        }
                        iconView.clipToOutline = true
                    }
                    val matrix = ColorMatrix().apply { setSaturation(0f) }
                    iconView.colorFilter = ColorMatrixColorFilter(matrix)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        iconView.imageAlpha = 200
                    }
                }
                iconApplied = true
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore missing package
            }
        }

        if (!iconApplied) {
            iconView.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
            val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
            if (tintColor != null) {
                iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                iconView.imageAlpha = 255
            }
        }
        iconView.visibility = View.VISIBLE
    }

    private fun applyIconToBadge(view: ImageView, track: MusicTrack) {
        if (track.sourceIconResId != null) {
            view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, track.sourceIconResId))
            view.colorFilter = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.imageAlpha = 255
            }
        } else if (track.sourceIconBitmap != null) {
            view.setImageBitmap(track.sourceIconBitmap)
            view.colorFilter = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.imageAlpha = 255
            }
        } else if (!track.sourcePackageName.isNullOrEmpty()) {
            try {
                val icon = host.hostContext.packageManager.getApplicationIcon(track.sourcePackageName)
                view.setImageDrawable(icon)
                view.colorFilter = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.imageAlpha = 255
                }
            } catch (e: PackageManager.NameNotFoundException) {
                view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
                val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
                if (tintColor != null) {
                    view.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                }
            }
        } else {
            view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
            val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)
            if (tintColor != null) {
                view.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    override fun onDestroy() {
        cancelPausedTimeout()
        stopProgressTracker()
        rootView?.animate()?.cancel()
        nowPlayingTextView = null
        lastfmIcon = null
        mediaTickerLayout = null
        mediaCompactCard = null
        compactCoverArt = null
        compactSourceIcon = null
        compactTitleText = null
        compactArtistText = null
        mediaExpandedCard = null
        expandedCoverArt = null
        expandedSourceIcon = null
        expandedTitleText = null
        expandedArtistText = null
        expandedProgressBar = null
        btnPrev = null
        btnPlayPause = null
        btnNext = null
        super.onDestroy()
    }
}
