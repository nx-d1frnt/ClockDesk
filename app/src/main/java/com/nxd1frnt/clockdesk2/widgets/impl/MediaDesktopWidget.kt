package com.nxd1frnt.clockdesk2.widgets.impl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import com.nxd1frnt.clockdesk2.utils.PowerStateManager
import com.nxd1frnt.clockdesk2.utils.PowerSavePolicy
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.nxd1frnt.clockdesk2.utils.ArtworkColors
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
) : DesktopWidgetController(instance, definition, host), PowerSaveObserver {

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

    private var artworkCard: MaterialCardView? = null
    private var artworkCover: ImageView? = null
    private var artworkSourceIcon: ImageView? = null
    private var artworkSourceLabel: TextView? = null
    private var artworkTitle: TextView? = null
    private var artworkArtist: TextView? = null
    private var artworkProgress: SquigglyProgressBar? = null
    private var artworkPlayPause: ImageView? = null
    private var artworkColors = ArtworkColors.DEFAULT

    private fun progressBars() = listOfNotNull(expandedProgressBar, artworkProgress)
    private fun isPlayerShown() = mediaExpandedCard?.isShown == true || artworkCard?.isShown == true

    var lastTrackInfo: String? = null
        private set

    private var currentTrack: MusicTrack? = null
    private var isPlayingState: Boolean = false
    private var canControlPlayback = false

    // Transport control callbacks
    var onPlayPauseAction: (() -> Unit)? = null
    var onNextAction: (() -> Unit)? = null
    var onPrevAction: (() -> Unit)? = null
    var onTimeoutAction: (() -> Unit)? = null

    private var isPausedTimedOut: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pausedFadeOutRunnable: Runnable? = null
    private var progressTrackerRunnable: Runnable? = null

    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L
    private var positionSampleMs = 0L
    private var positionSampleAt = 0L
    private var hostResumed = true
    private var isPowerSaving = false
    private val powerStateManager = PowerStateManager.getInstance(host.hostContext)
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { resumeProgressTracker() }
        override fun onViewDetachedFromWindow(v: View) {
            stopProgressTracker()
            progressBars().forEach { it.isPlaying = false }
        }
    }

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isPowerSaving = isEnabled
        resumeProgressTracker()
    }

    override fun onPause() {
        hostResumed = false
        stopProgressTracker()
        progressBars().forEach { it.isPlaying = false }
    }

    override fun onResume() {
        hostResumed = true
        resumeProgressTracker()
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        resumeProgressTracker()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun bindView(view: View) {
        super.bindView(view)
        view.addOnAttachStateChangeListener(attachListener)

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

        artworkCard = view.findViewById(R.id.media_artwork_card)
        artworkCover = view.findViewById(R.id.artwork_cover_art)
        artworkSourceIcon = view.findViewById(R.id.artwork_source_icon)
        artworkSourceLabel = view.findViewById(R.id.artwork_source_label)
        artworkTitle = view.findViewById<TextView>(R.id.artwork_title_text)?.apply { isSelected = true }
        artworkArtist = view.findViewById<TextView>(R.id.artwork_artist_text)?.apply { isSelected = true }
        artworkProgress = view.findViewById(R.id.artwork_progress_bar)
        artworkPlayPause = view.findViewById(R.id.btn_artwork_play_pause)
        view.findViewById<View>(R.id.btn_artwork_prev)?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onPrevAction?.invoke()
        }
        artworkPlayPause?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onPlayPauseAction?.invoke()
        }
        view.findViewById<View>(R.id.btn_artwork_next)?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onNextAction?.invoke()
        }

        // Setup transport controls in Expanded mode
        btnPrev?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onPrevAction?.invoke()
        }
        btnPlayPause?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onPlayPauseAction?.invoke()
        }
        btnNext?.setOnClickListener {
            if (!host.isEditMode && canControlPlayback) onNextAction?.invoke()
        }

        // Setup gestures for Minimal and Compact modes (Single tap = Play/Pause, Double tap = Next)
        val gestureDetector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!host.isEditMode && canControlPlayback) {
                    onPlayPauseAction?.invoke()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!host.isEditMode && canControlPlayback) {
                    onNextAction?.invoke()
                    return true
                }
                return false
            }
        })

        val touchListener = View.OnTouchListener { _, event ->
            if (!host.isEditMode && canControlPlayback) {
                gestureDetector.onTouchEvent(event)
            } else {
                false
            }
        }

        mediaTickerLayout?.setOnTouchListener(touchListener)
        mediaCompactCard?.setOnTouchListener(touchListener)

        val currentStyle = host.fontManager?.getMediaStyle() ?: MediaStyle.MINIMAL_TICKER
        applyStyleVisibility(currentStyle)
        updateTransportControls(false)
        powerStateManager.registerObserver(this)
    }

    fun updateMediaStyle(style: MediaStyle) {
        applyStyleVisibility(style)
        compactCoverArt?.tag = null
        expandedCoverArt?.tag = null
        artworkCover?.tag = null
        artworkSourceIcon?.tag = null
        compactSourceIcon?.tag = null
        expandedSourceIcon?.tag = null
        currentTrack?.let { populateTrackData(it, isPlayingState) }
    }

    private fun applyStyleVisibility(style: MediaStyle) {
        mediaTickerLayout?.visibility = if (style == MediaStyle.MINIMAL_TICKER) View.VISIBLE else View.GONE
        mediaCompactCard?.visibility = if (style == MediaStyle.COMPACT_CARD) View.VISIBLE else View.GONE
        mediaExpandedCard?.visibility = if (style == MediaStyle.EXPANDED_PLAYER) View.VISIBLE else View.GONE
        artworkCard?.visibility = if (style == MediaStyle.ARTWORK_PLAYER) View.VISIBLE else View.GONE
        resumeProgressTracker()
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
                updateTextIfChanged(nowPlayingTextView, placeholder)
                updateTextIfChanged(compactTitleText, placeholder)
                updateTextIfChanged(compactArtistText, "Artist")
                updateTextIfChanged(expandedTitleText, placeholder)
                updateTextIfChanged(expandedArtistText, "Artist")
                updateTextIfChanged(artworkTitle, placeholder)
                updateTextIfChanged(artworkArtist, "Artist")
            }
            val trackToPreview = currentTrack ?: MusicTrack(
                title = host.hostContext.getString(R.string.now_playing_placeholder),
                artist = "Artist",
                album = ""
            )
            updateSourceIcon(trackToPreview)
        } else {
            val isMusicActive = currentTrack != null && !isPausedTimedOut && (isPlayingState || pausedFadeOutRunnable != null)

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

    private fun updateTransportControls(canControl: Boolean) {
        canControlPlayback = canControl
        val visibility = if (canControl) View.VISIBLE else View.GONE
        listOf(btnPrev, btnPlayPause, btnNext, artworkPlayPause,
            rootView?.findViewById<View>(R.id.btn_artwork_prev),
            rootView?.findViewById<View>(R.id.btn_artwork_next)).forEach {
            it?.visibility = visibility
        }
        rootView?.findViewById<View>(R.id.artwork_track_text)?.let { text ->
            val params = text.layoutParams as ViewGroup.MarginLayoutParams
            val margin = ((if (canControl) 94 else 18) * text.resources.displayMetrics.density).toInt()
            if (params.marginEnd != margin) {
                text.layoutParams = params.apply { marginEnd = margin }
            }
        }
    }

    fun handleMusicStateUpdate(state: PluginState) {
        val layout = rootView ?: return
        updateTransportControls(state.canControl)
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
                    updateTextIfChanged(nowPlayingTextView, placeholder)
                    updateTextIfChanged(compactTitleText, placeholder)
                    updateTextIfChanged(compactArtistText, "Artist")
                    updateTextIfChanged(expandedTitleText, placeholder)
                    updateTextIfChanged(expandedArtistText, "Artist")
                    updateTextIfChanged(artworkTitle, placeholder)
                    updateTextIfChanged(artworkArtist, "Artist")
                    lastTrackInfo = null
                    currentTrack = null
                }
            }
            return
        }

        when (state) {
            is PluginState.Playing -> {
                isPausedTimedOut = false
                cancelPausedTimeout()
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                val isTextDifferent = trackInfoText != lastTrackInfo
                lastTrackInfo = trackInfoText
                currentTrack = track
                isPlayingState = true

                populateTrackData(track, isPlaying = true)

                if (isTextDifferent || layout.visibility != View.VISIBLE || layout.alpha < 1f) {
                    performTrackTransition(trackInfoText)
                }
                startProgressTracker(track)
            }

            is PluginState.Paused -> {
                stopProgressTracker()
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                lastTrackInfo = trackInfoText
                currentTrack = track
                isPlayingState = false

                if (isPausedTimedOut) {
                    // Rule: Do not show paused players again after timeout
                    return
                }

                populateTrackData(track, isPlaying = false)

                if (layout.visibility != View.VISIBLE) {
                    performTrackTransition(trackInfoText)
                }

                // 45-second inactivity timeout before fading to Idle
                schedulePausedTimeout()
            }

            is PluginState.Idle, is PluginState.Disabled -> {
                isPausedTimedOut = false
                cancelPausedTimeout()
                stopProgressTracker()
                currentTrack = null
                isPlayingState = false
                progressBars().forEach { it.isPlaying = false }
                performIdleTransition()
            }
        }
    }

    private fun schedulePausedTimeout() {
        if (pausedFadeOutRunnable != null) {
            // Already scheduled, do not continuously postpone timeout on repeated paused updates
            return
        }
        val runnable = Runnable {
            if (!host.isEditMode && !isPlayingState) {
                isPausedTimedOut = true
                performIdleTransition()
                onTimeoutAction?.invoke()
            }
            pausedFadeOutRunnable = null
        }
        pausedFadeOutRunnable = runnable
        mainHandler.postDelayed(runnable, 45_000L)
    }

    private fun cancelPausedTimeout() {
        pausedFadeOutRunnable?.let { mainHandler.removeCallbacks(it) }
        pausedFadeOutRunnable = null
    }

    private fun updateTextIfChanged(textView: TextView?, newText: CharSequence) {
        if (textView == null) return
        if (textView.text?.toString() != newText.toString()) {
            textView.text = newText
        }
        if (!textView.isSelected) {
            textView.isSelected = true
        }
    }

    private fun areArtworkSourcesEqual(source1: Any?, source2: Any?): Boolean {
        if (source1 === source2) return true
        if (source1 == null || source2 == null) return false
        if (source1 is String && source2 is String) {
            return source1 == source2
        }
        if (source1 is Uri && source2 is Uri) {
            return source1 == source2
        }
        if (source1 is Int && source2 is Int) {
            return source1 == source2
        }
        if (source1 is Bitmap && source2 is Bitmap) {
            if (source1.isRecycled || source2.isRecycled) return false
            if (source1.width != source2.width || source1.height != source2.height) return false
            return runCatching { source1.sameAs(source2) }.getOrDefault(false)
        }
        return false
    }

    private fun populateTrackData(track: MusicTrack, isPlaying: Boolean) {
        val trackInfoText = "${track.artist} - ${track.title}"

        // 1. Minimal Ticker
        updateTextIfChanged(nowPlayingTextView, trackInfoText)
        updateSourceIcon(track)

        // 2. Compact Card
        updateTextIfChanged(compactTitleText, track.title.ifEmpty { trackInfoText })
        updateTextIfChanged(compactArtistText, track.artist.ifEmpty { "Unknown Artist" })
        loadArtwork(track, compactCoverArt)

        // 3. Expanded Player
        updateTextIfChanged(expandedTitleText, track.title.ifEmpty { trackInfoText })
        updateTextIfChanged(expandedArtistText, track.artist.ifEmpty { "Unknown Artist" })
        loadArtwork(track, expandedCoverArt)

        updateTextIfChanged(artworkTitle, track.title.ifEmpty { trackInfoText })
        updateTextIfChanged(artworkArtist, track.artist.ifEmpty { "Unknown Artist" })
        loadArtwork(track, artworkCover)

        val playPauseRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        listOfNotNull(btnPlayPause, artworkPlayPause).forEach { button ->
            if (button.tag != playPauseRes) {
                button.setImageResource(playPauseRes)
                button.tag = playPauseRes
            }
        }

        currentDurationMs = track.durationMs ?: 0L
        currentPositionMs = track.positionMs ?: 0L
        positionSampleMs = currentPositionMs
        positionSampleAt = SystemClock.elapsedRealtime()
        updateProgressBarUI()
    }

    private fun loadArtwork(track: MusicTrack, targetView: ImageView?) {
        val view = targetView ?: return
        val context = host.hostContext

        val currentSource: Any? = when {
            track.artworkBitmap != null && !track.artworkBitmap.isRecycled -> track.artworkBitmap
            !track.artworkUrl.isNullOrEmpty() -> track.artworkUrl
            track.sourceIconBitmap != null && !track.sourceIconBitmap.isRecycled -> track.sourceIconBitmap
            track.sourceIconResId != null -> track.sourceIconResId
            else -> R.drawable.music_note
        }

        if (areArtworkSourcesEqual(view.tag, currentSource)) {
            return
        }
        view.tag = currentSource
        if (view === artworkCover) {
            // Cancel any older URL request before replacing it with another cover or fallback.
            Glide.with(context).clear(view)
            applyArtworkColors(ArtworkColors.DEFAULT)
        }

        if (track.artworkBitmap != null && !track.artworkBitmap.isRecycled) {
            view.setImageBitmap(track.artworkBitmap)
            if (view === artworkCover) applyArtworkColors(ArtworkColors.fromBitmap(track.artworkBitmap))
            view.imageTintList = null
            return
        }

        if (!track.artworkUrl.isNullOrEmpty()) {
            if (view === artworkCover) {
                Glide.with(context).asBitmap()
                    .load(track.artworkUrl)
                    .placeholder(R.drawable.music_note)
                    .error(R.drawable.music_note)
                    .into(object : BitmapImageViewTarget(view) {
                        override fun setResource(resource: Bitmap?) {
                            super.setResource(resource)
                            if (artworkCover === view) {
                                applyArtworkColors(resource?.let(ArtworkColors::fromBitmap) ?: ArtworkColors.DEFAULT)
                            }
                        }
                    })
            } else {
                Glide.with(context)
                    .load(track.artworkUrl)
                    .placeholder(R.drawable.music_note)
                    .error(R.drawable.music_note)
                    .into(view)
            }
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

    private fun applyArtworkColors(colors: ArtworkColors) {
        artworkColors = colors
        artworkPlayPause?.let { button ->
            button.backgroundTintList = ColorStateList.valueOf(colors.accent)
            button.imageTintList = ColorStateList.valueOf(colors.onAccent)
        }
        artworkSourceIcon?.setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
    }

    private fun startProgressTracker(track: MusicTrack) {
        currentDurationMs = track.durationMs ?: 0L
        positionSampleMs = track.positionMs ?: 0L
        positionSampleAt = SystemClock.elapsedRealtime()
        resumeProgressTracker()
    }

    private fun resumeProgressTracker() {
        stopProgressTracker()
        val visible = hostResumed && instance.isVisible && rootView?.isShown == true &&
            isPlayerShown()
        val animate = visible && isPlayingState &&
            !PowerSavePolicy.read(host.getPreferences(), isPowerSaving).pauseAnimations
        progressBars().forEach { it.isPlaying = animate && it.isShown }
        if (!visible || !isPlayingState || currentDurationMs <= 0) return
        val interval = if (isPowerSaving) 5000L else 1000L
        progressTrackerRunnable = object : Runnable {
            override fun run() {
                if (!hostResumed || rootView?.isShown != true || !isPlayerShown()) {
                    progressBars().forEach { it.isPlaying = false }
                    return
                }
                currentPositionMs = (positionSampleMs + SystemClock.elapsedRealtime() - positionSampleAt)
                    .coerceIn(0L, currentDurationMs)
                updateProgressBarUI()
                if (currentPositionMs < currentDurationMs) mainHandler.postDelayed(this, interval)
            }
        }
        mainHandler.post(progressTrackerRunnable!!)
    }

    private fun stopProgressTracker() {
        progressTrackerRunnable?.let { mainHandler.removeCallbacks(it) }
        progressTrackerRunnable = null
    }

    private fun updateProgressBarUI() {
        progressBars().forEach { bar ->
            bar.isPlaying = isPlayingState && hostResumed && rootView?.isShown == true &&
                bar.isShown &&
                !PowerSavePolicy.read(host.getPreferences(), isPowerSaving).pauseAnimations
            bar.progress = if (currentDurationMs > 0) {
                ((currentPositionMs.toDouble() / currentDurationMs) * bar.max).toInt().coerceIn(0, bar.max)
            } else 0
            bar.visibility = if (currentDurationMs > 0) View.VISIBLE else View.GONE
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
        artworkSourceLabel?.let { label ->
            val sourceName = track.sourcePackageName?.let { packageName ->
                runCatching {
                    val pm = host.hostContext.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull()
            }
            label.text = sourceName.orEmpty()
            label.visibility = if (sourceName.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val showMediaIcon = host.getPreferences().getBoolean("show_media_icon", false)

        artworkSourceIcon?.let { icon ->
            icon.visibility = View.VISIBLE
            if (showMediaIcon) {
                applyIconToBadge(icon, track)
            } else {
                icon.setImageResource(R.drawable.music_note)
                icon.tag = null
            }
            icon.setColorFilter(artworkColors.accent, PorterDuff.Mode.SRC_IN)
        }

        // Handle badges on Compact & Expanded styles
        if (!showMediaIcon) {
            if (compactSourceIcon?.visibility != View.GONE) compactSourceIcon?.visibility = View.GONE
            if (expandedSourceIcon?.visibility != View.GONE) expandedSourceIcon?.visibility = View.GONE
        } else {
            compactSourceIcon?.let {
                if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                applyIconToBadge(it, track)
            }
            expandedSourceIcon?.let {
                if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                applyIconToBadge(it, track)
            }
        }

        // Handle Minimal Ticker icon
        val iconView = lastfmIcon ?: return
        val tickerKey: Any? = if (!showMediaIcon) {
            R.drawable.music_note
        } else {
            when {
                track.sourceIconResId != null -> track.sourceIconResId
                track.sourceIconBitmap != null -> track.sourceIconBitmap
                !track.sourcePackageName.isNullOrEmpty() -> track.sourcePackageName
                else -> R.drawable.music_note
            }
        }

        if (areArtworkSourcesEqual(iconView.tag, tickerKey)) {
            if (iconView.visibility != View.VISIBLE) iconView.visibility = View.VISIBLE
            return
        }
        iconView.tag = tickerKey

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
        val badgeKey: Any? = when {
            track.sourceIconResId != null -> track.sourceIconResId
            track.sourceIconBitmap != null -> track.sourceIconBitmap
            !track.sourcePackageName.isNullOrEmpty() -> track.sourcePackageName
            else -> R.drawable.music_note
        }

        val tintColor = host.fontManager?.getFinalColorForView(R.id.lastfm_layout)

        if (!areArtworkSourcesEqual(view.tag, badgeKey)) {
            view.tag = badgeKey

            if (track.sourceIconResId != null) {
                view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, track.sourceIconResId))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.imageAlpha = 255
                }
            } else if (track.sourceIconBitmap != null) {
                view.setImageBitmap(track.sourceIconBitmap)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.imageAlpha = 255
                }
            } else if (!track.sourcePackageName.isNullOrEmpty()) {
                try {
                    val icon = host.hostContext.packageManager.getApplicationIcon(track.sourcePackageName)
                    var monochromeDrawable: Drawable? = null

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is AdaptiveIconDrawable) {
                        monochromeDrawable = icon.monochrome
                    }

                    if (monochromeDrawable != null) {
                        view.setImageDrawable(monochromeDrawable)
                    } else {
                        view.setImageDrawable(icon)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        view.imageAlpha = 255
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
                }
            } else {
                view.setImageDrawable(ContextCompat.getDrawable(host.hostContext, R.drawable.music_note))
            }
        }

        if (tintColor != null) {
            view.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        } else {
            view.colorFilter = null
        }
    }

    override fun onDestroy() {
        onPause()
        powerStateManager.unregisterObserver(this)
        rootView?.removeOnAttachStateChangeListener(attachListener)
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
        artworkCover?.let { Glide.with(host.hostContext).clear(it) }
        artworkCard = null
        artworkCover = null
        artworkSourceIcon = null
        artworkSourceLabel = null
        artworkTitle = null
        artworkArtist = null
        artworkProgress = null
        artworkPlayPause = null
        onTimeoutAction = null
        isPausedTimedOut = false
        super.onDestroy()
    }
}
