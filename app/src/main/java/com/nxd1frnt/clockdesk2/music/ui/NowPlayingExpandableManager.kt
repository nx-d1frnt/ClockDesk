package com.nxd1frnt.clockdesk2.music.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.MusicPluginManager
import com.nxd1frnt.clockdesk2.music.PluginState
import java.util.Locale
import kotlin.math.abs

class NowPlayingExpandableManager(
    private val activity: Activity,
    private val mainLayout: ViewGroup,
    private val lastfmLayout: ViewGroup,
    private val timeText: TextView,
    private val musicManagerProvider: () -> MusicPluginManager?,
    private val fontManagerProvider: () -> com.nxd1frnt.clockdesk2.utils.FontManager? = { null }
) {

    private val collapsedContainer: View = lastfmLayout.findViewById(R.id.collapsed_now_playing_container)
    private val expandedContainer: MaterialCardView = lastfmLayout.findViewById(R.id.expanded_now_playing_container)

    private val bgArtwork: ImageView = lastfmLayout.findViewById(R.id.expanded_bg_artwork)
    private val sourceIcon: ImageView = lastfmLayout.findViewById(R.id.expanded_source_icon)
    private val sourceName: TextView = lastfmLayout.findViewById(R.id.expanded_source_name)
    private val collapseBtn: ImageView = lastfmLayout.findViewById(R.id.expanded_collapse_btn)
    private val trackTitle: TextView = lastfmLayout.findViewById(R.id.expanded_track_title)
    private val trackArtist: TextView = lastfmLayout.findViewById(R.id.expanded_track_artist)
    private val seekbarContainer: LinearLayout = lastfmLayout.findViewById(R.id.expanded_seekbar_container)
    private val seekbar: SeekBar = lastfmLayout.findViewById(R.id.expanded_media_seekbar)
    private val timeElapsed: TextView = lastfmLayout.findViewById(R.id.expanded_time_elapsed)
    private val timeTotal: TextView = lastfmLayout.findViewById(R.id.expanded_time_total)
    private val btnPrevious: ImageButton = lastfmLayout.findViewById(R.id.expanded_btn_previous)
    private val btnPlayPauseCard: MaterialCardView = lastfmLayout.findViewById(R.id.expanded_play_pause_card)
    private val btnPlayPauseIcon: ImageView = lastfmLayout.findViewById(R.id.expanded_btn_play_pause)
    private val btnNext: ImageButton = lastfmLayout.findViewById(R.id.expanded_btn_next)
    private val btnLike: ImageButton = lastfmLayout.findViewById(R.id.expanded_btn_like)
    private val btnExtra: ImageButton = lastfmLayout.findViewById(R.id.expanded_btn_extra)

    var isExpanded: Boolean = false
        private set

    private var originalTimeTextPx: Float? = null
    private var isUserSeeking = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isExpanded) return

            val musicManager = musicManagerProvider()
            val info = musicManager?.getActivePlaybackInfo()
            if (info != null) {
                btnPlayPauseIcon.setImageResource(if (info.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

                if (info.canLike) {
                    btnLike.visibility = View.VISIBLE
                    btnLike.setImageResource(if (info.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                } else {
                    btnLike.visibility = View.GONE
                }

                if (info.customActions.isNotEmpty()) {
                    val extraAction = info.customActions.firstOrNull {
                        !it.id.contains("like", ignoreCase = true) && !it.id.contains("fav", ignoreCase = true)
                    }
                    if (extraAction != null) {
                        btnExtra.visibility = View.VISIBLE
                        btnExtra.contentDescription = extraAction.name
                        val iconRes = if (extraAction.id.contains("shuffle", ignoreCase = true)) {
                            R.drawable.ic_shuffle
                        } else {
                            R.drawable.ic_repeat
                        }
                        btnExtra.setImageResource(iconRes)
                        btnExtra.setOnClickListener {
                            musicManager?.performCustomActionActive(extraAction.id)
                        }
                    } else {
                        btnExtra.visibility = View.GONE
                    }
                } else {
                    btnExtra.visibility = View.GONE
                }

                if (!isUserSeeking && info.durationMs > 0) {
                    seekbarContainer.visibility = View.VISIBLE
                    val progress = ((info.positionMs.toFloat() / info.durationMs.toFloat()) * 1000).toInt()
                    seekbar.progress = progress.coerceIn(0, 1000)
                    timeElapsed.text = formatTime(info.positionMs)
                    timeTotal.text = formatTime(info.durationMs)
                } else if (info.durationMs <= 0) {
                    seekbarContainer.visibility = View.GONE
                }
            }

            handler.postDelayed(this, 500L)
        }
    }

    init {
        mainLayout.clipChildren = false
        mainLayout.clipToPadding = false

        collapseBtn.setOnClickListener { collapse() }

        btnLike.setOnClickListener {
            val musicManager = musicManagerProvider()
            musicManager?.toggleLikeActive()
            val info = musicManager?.getActivePlaybackInfo()
            val willBeLiked = !(info?.isLiked ?: false)
            btnLike.setImageResource(if (willBeLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        }

        val playPauseAction = {
            val musicManager = musicManagerProvider()
            musicManager?.togglePlayPauseActive()
            val info = musicManager?.getActivePlaybackInfo()
            val willBePlaying = !(info?.isPlaying ?: false)
            btnPlayPauseIcon.setImageResource(if (willBePlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        btnPlayPauseCard.setOnClickListener { playPauseAction() }
        btnPlayPauseIcon.setOnClickListener { playPauseAction() }

        btnPrevious.setOnClickListener {
            musicManagerProvider()?.skipToPreviousActive()
        }

        btnNext.setOnClickListener {
            musicManagerProvider()?.skipToNextActive()
        }

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val info = musicManagerProvider()?.getActivePlaybackInfo() ?: return
                    val duration = info.durationMs
                    if (duration > 0) {
                        val posMs = (progress.toFloat() / 1000f * duration).toLong()
                        timeElapsed.text = formatTime(posMs)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                val info = musicManagerProvider()?.getActivePlaybackInfo() ?: return
                val duration = info.durationMs
                if (duration > 0 && sb != null) {
                    val posMs = (sb.progress.toFloat() / 1000f * duration).toLong()
                    musicManagerProvider()?.seekToActive(posMs)
                }
            }
        })
    }

    var targetClockPx: Float? = null
        private set

    fun reapplyScale() {
        if (isExpanded && targetClockPx != null) {
            applyClockScale(targetClockPx!!)
        }
        applyDynamicColors()
    }

    fun applyDynamicColors() {
        val fontManager = fontManagerProvider() ?: return
        val scheme = fontManager.getDynamicScheme()

        val primary = scheme.primary
        val onPrimary = scheme.onPrimary
        val primaryContainer = scheme.primaryContainer
        val onPrimaryContainer = scheme.onPrimaryContainer
        val surfaceVariant = scheme.surfaceVariant

        btnPlayPauseCard.setCardBackgroundColor(primary)
        btnPlayPauseIcon.imageTintList = ColorStateList.valueOf(onPrimary)
        //sourceIcon.imageTintList = ColorStateList.valueOf(onPrimaryContainer)

        val controlTint = ColorStateList.valueOf(primary)

        expandedContainer.setCardBackgroundColor(surfaceVariant)
    }

    private fun applyClockScale(targetPx: Float) {
        targetClockPx = targetPx
        fontManagerProvider()?.clockFontSizeOverridePx = targetPx
        if (abs(timeText.textSize - targetPx) > 1f) {
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetPx)
        }
    }

    private fun calculateTargetClockSize(cardHeightPx: Float): Float {
        val parentHeight = mainLayout.height.toFloat()
        val originalPx = originalTimeTextPx ?: timeText.textSize
        if (parentHeight <= 0f) return originalPx * 0.65f

        val currentTextSize = timeText.textSize
        val currentHeight = timeText.height.toFloat()
        val unscaledTimeHeight = if (currentTextSize > 0f && originalPx > 0f) {
            currentHeight * (originalPx / currentTextSize)
        } else {
            currentHeight
        }

        val dateHeight = (mainLayout.findViewById<View>(R.id.date_text)?.height ?: 0).toFloat()
        val extraMargins = 64f * activity.resources.displayMetrics.density

        val requiredHeight = unscaledTimeHeight + dateHeight + cardHeightPx + extraMargins
        val overflow = requiredHeight - parentHeight

        if (overflow > 0 && unscaledTimeHeight > 0) {
            val targetTimeHeight = maxOf(unscaledTimeHeight * 0.4f, unscaledTimeHeight - overflow)
            val scaleRatio = (targetTimeHeight / unscaledTimeHeight).coerceIn(0.45f, 1.0f)
            return originalPx * scaleRatio
        }

        return originalPx
    }

    fun toggle(currentState: PluginState?) {
        if (isExpanded) {
            collapse()
        } else {
            expand(currentState)
        }
    }

    fun expand(currentState: PluginState?) {
        if (isExpanded) return
        isExpanded = true

        // Store original clock font size
        if (originalTimeTextPx == null) {
            originalTimeTextPx = timeText.textSize
        }

        updateContent(currentState)
        applyDynamicColors()

        // Measure expanded container height dynamically
        expandedContainer.measure(
            View.MeasureSpec.makeMeasureSpec(mainLayout.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardHeightPx = expandedContainer.measuredHeight.toFloat()
        val calcTargetPx = calculateTargetClockSize(cardHeightPx)
        applyClockScale(calcTargetPx)

        val set = TransitionSet().apply {
            addTransition(ChangeBounds().apply { resizeClip = false })
            addTransition(ChangeTransform())
            addTransition(Fade())
            duration = 300L
            interpolator = FastOutSlowInInterpolator()
        }

        TransitionManager.beginDelayedTransition(mainLayout, set)

        collapsedContainer.visibility = View.GONE
        expandedContainer.visibility = View.VISIBLE
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, calcTargetPx)

        handler.post(updateRunnable)
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        targetClockPx = null
        fontManagerProvider()?.clockFontSizeOverridePx = null
        handler.removeCallbacks(updateRunnable)

        val restoreClockPx = originalTimeTextPx ?: timeText.textSize

        val set = TransitionSet().apply {
            addTransition(ChangeBounds().apply { resizeClip = false })
            addTransition(ChangeTransform())
            addTransition(Fade())
            duration = 260L
            interpolator = FastOutSlowInInterpolator()
        }

        TransitionManager.beginDelayedTransition(mainLayout, set)

        expandedContainer.visibility = View.GONE
        collapsedContainer.visibility = View.VISIBLE
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, restoreClockPx)
    }

    fun updateContent(currentState: PluginState?) {
        val track = (currentState as? PluginState.Playing)?.track

        if (track != null) {
            trackTitle.text = track.title
            trackArtist.text = track.artist

            if (track.sourceIconBitmap != null && !track.sourceIconBitmap.isRecycled) {
                sourceIcon.imageTintList = null
                sourceIcon.setImageBitmap(track.sourceIconBitmap)
            } else {
                sourceIcon.setImageResource(R.drawable.music_note)
                sourceIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }

            if (!track.sourcePackageName.isNullOrEmpty()) {
                val pm = activity.packageManager
                val appLabel = runCatching {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(track.sourcePackageName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(track.sourcePackageName, 0)
                    }
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()
                sourceName.text = appLabel ?: activity.getString(R.string.now_playing_dialog_title)
            } else {
                sourceName.text = activity.getString(R.string.now_playing_dialog_title)
            }

            val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
            if (validBitmap) {
                bgArtwork.setImageBitmap(track.artworkBitmap)
            } else if (!track.artworkUrl.isNullOrEmpty()) {
                Glide.with(activity)
                    .load(track.artworkUrl)
                    .placeholder(R.drawable.ic_music_icon)
                    .error(R.drawable.ic_music_icon)
                    .into(bgArtwork)
            } else {
                bgArtwork.setImageResource(R.drawable.ic_music_icon)
            }
        } else {
            trackTitle.text = activity.getString(R.string.media_unknown_title)
            trackArtist.text = activity.getString(R.string.media_unknown_artist)
            bgArtwork.setImageResource(R.drawable.ic_music_icon)
            sourceName.text = activity.getString(R.string.now_playing_dialog_title)
        }

        if (isExpanded) {
            expandedContainer.measure(
                View.MeasureSpec.makeMeasureSpec(mainLayout.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val cardHeightPx = expandedContainer.measuredHeight.toFloat()
            val calcTargetPx = calculateTargetClockSize(cardHeightPx)
            applyClockScale(calcTargetPx)
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
