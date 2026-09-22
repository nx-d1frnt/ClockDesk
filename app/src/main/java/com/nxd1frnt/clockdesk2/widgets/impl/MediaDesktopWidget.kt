package com.nxd1frnt.clockdesk2.widgets.impl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
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
    var nowPlayingTextView: TextView? = null
        private set
    var lastfmIcon: ImageView? = null
        private set

    var lastTrackInfo: String? = null
        private set

    override fun bindView(view: View) {
        super.bindView(view)
        nowPlayingTextView = view.findViewById(R.id.now_playing_text)
        lastfmIcon = view.findViewById(R.id.lastfm_icon)
        nowPlayingTextView?.isSelected = true
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
            if (nowPlayingTextView?.text.isNullOrEmpty()) {
                nowPlayingTextView?.text = host.hostContext.getString(R.string.now_playing_placeholder)
            }
        } else {
            val isMusicPlaying = !nowPlayingTextView?.text.isNullOrEmpty() &&
                    lastTrackInfo != null &&
                    layout.alpha > 0

            if (!isMusicPlaying) {
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

        if (host.isEditMode) {
            if (state is PluginState.Playing) {
                val track = state.track
                val trackInfo = "${track.artist} - ${track.title}"
                nowPlayingTextView?.text = trackInfo
                lastTrackInfo = trackInfo
                updateSourceIcon(track)
            } else {
                nowPlayingTextView?.text = host.hostContext.getString(R.string.now_playing_placeholder)
                lastTrackInfo = null
            }
            return
        }

        when (state) {
            is PluginState.Playing -> {
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                val isTextDifferent = trackInfoText != lastTrackInfo
                lastTrackInfo = trackInfoText

                if (isTextDifferent) {
                    layout.animate().cancel()
                    layout.animate().setListener(null)
                    updateSourceIcon(track)

                    val baseX = host.getRestTranslationX(layout)

                    if (layout.visibility != View.VISIBLE || layout.alpha < 1f) {
                        val needsFadeIn = layout.visibility != View.VISIBLE
                        layout.visibility = View.VISIBLE
                        nowPlayingTextView?.text = trackInfoText
                        nowPlayingTextView?.isSelected = true

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
                                        nowPlayingTextView?.text = trackInfoText
                                        nowPlayingTextView?.isSelected = true
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
            }

            is PluginState.Idle, is PluginState.Disabled -> {
                performIdleTransition()
            }
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
        val iconView = lastfmIcon ?: return
        val showMediaIcon = host.getPreferences().getBoolean("show_media_icon", false)

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

    override fun onDestroy() {
        rootView?.animate()?.cancel()
        nowPlayingTextView = null
        lastfmIcon = null
        super.onDestroy()
    }
}
