package com.nxd1frnt.clockdesk2.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R

/**
 * Manages the premium onboarding landing screens and interactive tutorial on first launch.
 */
class TutorialManager(
    private val tutorialLayout: ConstraintLayout,
    private val tutorialFinger: ImageView,
    private val tutorialText: TextView,
    private val mainLayout: View,
    private val timeText: View,
    private val prefs: SharedPreferences,
    private val toggleEditModeAction: () -> Unit,
    private val showCustomizationAction: (View) -> Unit,
    private val hideBottomSheetAction: () -> Unit,
    private val requestLocationPermissionAction: () -> Unit,
    private val onTutorialFinished: () -> Unit
) {
    var isTutorialRunning = false
        private set

    private var currentSlide = 0
    private var isInteractiveGuideRunning = false

    private val cardView: View = tutorialLayout.findViewById(R.id.onboarding_card)
    private val iconView: ImageView = tutorialLayout.findViewById(R.id.onboarding_icon)
    private val titleView: TextView = tutorialLayout.findViewById(R.id.onboarding_title)
    private val descView: TextView = tutorialLayout.findViewById(R.id.onboarding_desc)
    private val actionBtn: MaterialButton = tutorialLayout.findViewById(R.id.onboarding_btn_action)
    private val btnLeft: MaterialButton = tutorialLayout.findViewById(R.id.onboarding_btn_left)
    private val btnRight: MaterialButton = tutorialLayout.findViewById(R.id.onboarding_btn_right)
    private val indicatorContainer: LinearLayout = tutorialLayout.findViewById(R.id.onboarding_indicator_container)

    private data class OnboardingSlide(
        val iconResId: Int,
        val titleResId: Int,
        val descResId: Int,
        val hasActionBtn: Boolean = false,
        val actionBtnTextResId: Int = 0,
        val actionBtnIconResId: Int = 0
    )

    private val slides = listOf(
        OnboardingSlide(
            iconResId = R.mipmap.ic_launcher,
            titleResId = R.string.onboarding_welcome_title,
            descResId = R.string.onboarding_welcome_desc
        ),
        OnboardingSlide(
            iconResId = R.drawable.ic_palette_swatch,
            titleResId = R.string.onboarding_customization_title,
            descResId = R.string.onboarding_customization_desc,
            hasActionBtn = true,
            actionBtnTextResId = R.string.onboarding_try_guide_btn,
            actionBtnIconResId = R.drawable.cursor_pointer // placeholder icon
        ),
        OnboardingSlide(
            iconResId = R.drawable.ic_music_icon,
            titleResId = R.string.onboarding_widgets_title,
            descResId = R.string.onboarding_widgets_desc
        ),
        OnboardingSlide(
            iconResId = R.drawable.ic_weather_cloudy_clock,
            titleResId = R.string.onboarding_weather_title,
            descResId = R.string.onboarding_weather_desc
        ),
        OnboardingSlide(
            iconResId = R.drawable.ic_sun_clock_outline,
            titleResId = R.string.onboarding_permission_title,
            descResId = R.string.onboarding_permission_desc,
            hasActionBtn = true,
            actionBtnTextResId = R.string.onboarding_grant_btn,
            actionBtnIconResId = R.drawable.ic_sun_clock_outline
        )
    )

    init {
        // Setup button click listeners
        btnLeft.setOnClickListener {
            if (currentSlide == 0) {
                finishOnboarding()
            } else {
                showSlide(currentSlide - 1, -1)
            }
        }

        btnRight.setOnClickListener {
            if (currentSlide == slides.size - 1) {
                finishOnboarding()
            } else {
                showSlide(currentSlide + 1, 1)
            }
        }

        actionBtn.setOnClickListener {
            if (currentSlide == 1) {
                startInteractiveGuide()
            } else if (currentSlide == slides.size - 1) {
                requestLocationPermissionAction()
            }
        }
    }

    private var iconFloatingAnimator: ObjectAnimator? = null
    private var nextBtnPulseAnimator: ObjectAnimator? = null

    private fun applyBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP)
                mainLayout.setRenderEffect(blurEffect)
                tutorialLayout.setBackgroundColor(Color.parseColor("#66000000"))
            } catch (_: Throwable) {
                tutorialLayout.setBackgroundColor(Color.parseColor("#D9000000"))
            }
        } else {
            tutorialLayout.setBackgroundColor(Color.parseColor("#D9000000"))
        }
    }

    private fun removeBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                mainLayout.setRenderEffect(null)
            } catch (_: Throwable) {}
        }
    }

    private fun startContinuousAnimations() {
        stopContinuousAnimations()

        // Gentle floating bobbing animation for the slide icon
        iconFloatingAnimator = ObjectAnimator.ofFloat(
            iconView,
            "translationY",
            -5f * tutorialLayout.resources.displayMetrics.density,
            5f * tutorialLayout.resources.displayMetrics.density
        ).apply {
            duration = 1600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

    }

    private fun stopContinuousAnimations() {
        iconFloatingAnimator?.cancel()
        iconFloatingAnimator = null
        nextBtnPulseAnimator?.cancel()
        nextBtnPulseAnimator = null
        iconView.translationY = 0f
        btnRight.scaleX = 1f
        btnRight.scaleY = 1f
    }

    fun start() {
        isTutorialRunning = true
        applyBackgroundBlur()
        startContinuousAnimations()

        tutorialLayout.visibility = View.VISIBLE
        tutorialLayout.alpha = 0f

        cardView.scaleX = 0.92f
        cardView.scaleY = 0.92f
        cardView.alpha = 0f

        // Reset overlays
        tutorialFinger.visibility = View.GONE
        tutorialText.visibility = View.GONE

        tutorialLayout.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        cardView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.05f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    showSlide(0, 1)
                }
            })
            .start()
    }

    fun handleBackPressed(): Boolean {
        if (tutorialLayout.visibility == View.VISIBLE) {
            if (isInteractiveGuideRunning) {
                cancelInteractiveGuide()
            } else if (currentSlide > 0) {
                showSlide(currentSlide - 1, -1)
            } else {
                finishOnboarding()
            }
            return true
        }
        return false
    }

    private fun showSlide(index: Int, direction: Int) {
        val nextSlide = slides.getOrNull(index) ?: return
        val context = tutorialLayout.context
        val density = context.resources.displayMetrics.density

        // Cancel previous element animations
        iconView.animate().cancel()
        titleView.animate().cancel()
        descView.animate().cancel()
        actionBtn.animate().cancel()

        // Clean slide transition animation: Fade out current, slide in next
        val contentContainer = tutorialLayout.findViewById<View>(R.id.onboarding_content_container)

        contentContainer.animate()
            .alpha(0f)
            .translationX(-direction * 30f * density)
            .setDuration(160)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Update views with new slide content
                    iconView.setImageResource(nextSlide.iconResId)
                    if (index == 0) {
                        iconView.clearColorFilter()
                    } else {
                        iconView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    }
                    titleView.setText(nextSlide.titleResId)
                    descView.setText(nextSlide.descResId)

                    if (nextSlide.hasActionBtn) {
                        actionBtn.visibility = View.VISIBLE
                        actionBtn.setText(nextSlide.actionBtnTextResId)
                        if (index == 1) {
                            actionBtn.setIconResource(R.drawable.cursor_pointer)
                        } else {
                            actionBtn.setIconResource(nextSlide.actionBtnIconResId)
                        }
                        actionBtn.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#121212")))
                        checkAndUpdatePermissionState()
                    } else {
                        actionBtn.visibility = View.GONE
                    }

                    // Update navigation buttons text
                    btnLeft.text = if (index == 0) context.getString(R.string.onboarding_skip_btn) else context.getString(R.string.onboarding_back_btn)
                    btnRight.text = if (index == slides.size - 1) context.getString(R.string.onboarding_finish_btn) else context.getString(R.string.onboarding_next_btn)

                    updateIndicators(index, slides.size)

                    // Position incoming content for slide-in
                    contentContainer.translationX = direction * 30f * density
                    contentContainer.alpha = 1f

                    // Prepare initial values for staggered entrance
                    iconView.scaleX = 0.5f
                    iconView.scaleY = 0.5f
                    iconView.alpha = 0f

                    titleView.translationY = 18f * density
                    titleView.alpha = 0f

                    descView.translationY = 18f * density
                    descView.alpha = 0f

                    if (nextSlide.hasActionBtn) {
                        actionBtn.scaleX = 0.8f
                        actionBtn.scaleY = 0.8f
                        actionBtn.alpha = 0f
                    }

                    // Slide container in
                    contentContainer.animate()
                        .translationX(0f)
                        .setDuration(220)
                        .setListener(null)
                        .start()

                    // Staggered pop-in animations for individual slide components
                    iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(320)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()

                    titleView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(240)
                        .setStartDelay(40)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    descView.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(240)
                        .setStartDelay(80)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    if (nextSlide.hasActionBtn) {
                        actionBtn.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(280)
                            .setStartDelay(120)
                            .setInterpolator(OvershootInterpolator(1.2f))
                            .start()
                    }
                }
            }).start()

        currentSlide = index
    }

    private fun updateIndicators(currentIndex: Int, totalSlides: Int) {
        indicatorContainer.removeAllViews()
        val context = tutorialLayout.context
        val density = context.resources.displayMetrics.density

        for (i in 0 until totalSlides) {
            val dot = ImageView(context).apply {
                val dotWidth = if (i == currentIndex) (14 * density).toInt() else (6 * density).toInt()
                val dotHeight = (6 * density).toInt()
                val margin = (5 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotWidth, dotHeight).apply {
                    setMargins(margin, 0, margin, 0)
                }
                setImageResource(
                    if (i == currentIndex) R.drawable.onboarding_dot_active
                    else R.drawable.onboarding_dot_inactive
                )
            }
            indicatorContainer.addView(dot)
        }
    }

    fun checkAndUpdatePermissionState() {
        if (currentSlide == slides.size - 1 && !isInteractiveGuideRunning) { // permission slide
            val context = tutorialLayout.context
            if (isLocationPermissionGranted()) {
                actionBtn.isEnabled = false
                actionBtn.setText(context.getString(R.string.onboarding_permission_granted))
                actionBtn.setIconResource(R.drawable.ic_check)
            } else {
                actionBtn.isEnabled = true
                actionBtn.setText(context.getString(R.string.onboarding_grant_btn))
                actionBtn.setIconResource(R.drawable.ic_sun_clock_outline)
            }
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        val context = tutorialLayout.context
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return hasCoarse || hasFine
    }

    private fun finishOnboarding() {
        prefs.edit()
            .putBoolean("isFirstLaunch", false)
            .putBoolean("location_permission_rationale_shown", true)
            .apply()

        cardView.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .alpha(0f)
            .setDuration(250)
            .start()

        tutorialLayout.animate()
            .alpha(0f)
            .setDuration(250)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tutorialLayout.visibility = View.GONE
                    isTutorialRunning = false
                    removeBackgroundBlur()
                    onTutorialFinished()
                }
            })
            .start()
    }

    // region Interactive Guide

    private fun startInteractiveGuide() {
        isInteractiveGuideRunning = true

        cardView.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cardView.visibility = View.GONE
                    removeBackgroundBlur()

                    tutorialFinger.visibility = View.VISIBLE
                    tutorialText.visibility = View.VISIBLE

                    tutorialFinger.translationX = tutorialLayout.resources.displayMetrics.widthPixels.toFloat()
                    tutorialFinger.translationY = -200f
                    tutorialFinger.alpha = 0f
                    tutorialText.alpha = 0f

                    step3ShowFingerAndHold()
                }
            })
            .start()
    }

    private fun cancelInteractiveGuide() {
        isInteractiveGuideRunning = false
        applyBackgroundBlur()

        tutorialFinger.animate().cancel()
        tutorialFinger.visibility = View.GONE
        tutorialText.animate().cancel()
        tutorialText.visibility = View.GONE

        tutorialLayout.setOnClickListener(null)

        cardView.visibility = View.VISIBLE
        cardView.alpha = 0f
        cardView.scaleX = 0.92f
        cardView.scaleY = 0.92f
        cardView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.05f))
            .setListener(null)
            .start()

        showSlide(currentSlide, 1)
    }

    private fun step3ShowFingerAndHold() {
        if (!isInteractiveGuideRunning) return
        tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_2)
        tutorialText.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isInteractiveGuideRunning) return
                tutorialFinger.animate()
                    .alpha(1f)
                    .translationX(mainLayout.width / 2f - tutorialFinger.width / 2f)
                    .translationY(mainLayout.height / 2f - tutorialFinger.height / 2f)
                    .setDuration(1200)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            step4TapAnimation()
                        }
                    }).start()
            }
        }).start()
    }

    private fun step4TapAnimation() {
        if (!isInteractiveGuideRunning) return
        tutorialFinger.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(200)
            .setStartDelay(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isInteractiveGuideRunning) return
                    tutorialFinger.animate()
                        .scaleX(0.8f).scaleY(0.8f)
                        .setDuration(800)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (!isInteractiveGuideRunning) return
                                toggleEditModeAction()
                                step5MoveToTimeText()
                            }
                        }).start()
                }
            }).start()
    }

    private fun step5MoveToTimeText() {
        if (!isInteractiveGuideRunning) return
        tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_3)
        val targetX = timeText.x + (timeText.width / 2f) - (tutorialFinger.width / 2f)
        val targetY = timeText.y + (timeText.height / 2f) - (tutorialFinger.height / 2f)

        tutorialFinger.animate()
            .scaleX(1f).scaleY(1f)
            .x(targetX).y(targetY)
            .setDuration(1000)
            .setStartDelay(800)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    step6TapTimeText(targetY)
                }
            }).start()
    }

    private fun step6TapTimeText(targetY: Float) {
        if (!isInteractiveGuideRunning) return
        tutorialFinger.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isInteractiveGuideRunning) return
                    showCustomizationAction(timeText)
                    step7Finish(targetY)
                }
            }).start()
    }

    private fun step7Finish(targetY: Float) {
        if (!isInteractiveGuideRunning) return
        tutorialFinger.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(150)
            .setStartDelay(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isInteractiveGuideRunning) return
                    tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_4)
                    tutorialFinger.animate()
                        .alpha(0f)
                        .y(targetY - 200)
                        .setDuration(500)
                        .start()

                    tutorialLayout.setOnClickListener {
                        hideBottomSheetAction()
                        tutorialFinger.animate().cancel()
                        tutorialFinger.visibility = View.GONE
                        tutorialText.animate().cancel()
                        tutorialText.visibility = View.GONE
                        tutorialLayout.setOnClickListener(null)
                        isInteractiveGuideRunning = false

                        applyBackgroundBlur()

                        // Restore onboarding card and move to the next slide (widgets)
                        cardView.visibility = View.VISIBLE
                        cardView.alpha = 0f
                        cardView.scaleX = 0.92f
                        cardView.scaleY = 0.92f
                        cardView.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .setInterpolator(OvershootInterpolator(1.05f))
                            .setListener(null)
                            .start()

                        showSlide(2, 1) // Advance to the widgets slide (index 2)
                    }
                }
            }).start()
    }

    // endregion
}