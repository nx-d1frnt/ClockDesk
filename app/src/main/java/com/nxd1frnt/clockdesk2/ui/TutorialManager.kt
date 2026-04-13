package com.nxd1frnt.clockdesk2.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.SharedPreferences
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R

/**
 * Управляет логикой и цепочкой анимаций обучающего руководства при первом запуске.
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
    private val onTutorialFinished: () -> Unit
) {
    var isTutorialRunning = false
        private set

    fun start() {
        isTutorialRunning = true
        tutorialLayout.visibility = View.VISIBLE
        tutorialFinger.translationX = tutorialLayout.resources.displayMetrics.widthPixels.toFloat()
        tutorialFinger.translationY = -200f
        tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_1)

        // Шаг 1: Появление
        tutorialLayout.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                step2ShowTextAndHide()
            }
        }).start()
    }

    fun handleBackPressed(): Boolean {
        if (tutorialLayout.visibility == View.VISIBLE) {
            tutorialLayout.animate().alpha(0f).setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        tutorialLayout.visibility = View.GONE
                        isTutorialRunning = false
                    }
                }).start()
            return true
        }
        return false
    }

    private fun step2ShowTextAndHide() {
        tutorialText.animate().alpha(1f).setDuration(800).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tutorialText.animate().alpha(0f).setDuration(800).setStartDelay(1000)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            step3ShowFingerAndHold()
                        }
                    }).start()
            }
        }).start()
    }

    private fun step3ShowFingerAndHold() {
        tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_2)
        tutorialText.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
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
        tutorialFinger.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(200)
            .setStartDelay(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tutorialFinger.animate()
                        .scaleX(0.8f).scaleY(0.8f)
                        .setDuration(800)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                toggleEditModeAction()
                                step5MoveToTimeText()
                            }
                        }).start()
                }
            }).start()
    }

    private fun step5MoveToTimeText() {
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
        tutorialFinger.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    showCustomizationAction(timeText)
                    step7Finish(targetY)
                }
            }).start()
    }

    private fun step7Finish(targetY: Float) {
        tutorialFinger.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(150)
            .setStartDelay(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tutorialText.text = tutorialLayout.context.getString(R.string.tutorial_text_4)
                    tutorialFinger.animate()
                        .alpha(0f)
                        .y(targetY - 200)
                        .setDuration(500)
                        .start()

                    tutorialLayout.setOnClickListener {
                        hideBottomSheetAction()
                        tutorialLayout.animate().alpha(0f).setDuration(300)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    tutorialLayout.visibility = View.GONE
                                    prefs.edit().putBoolean("isFirstLaunch", false).apply()
                                    isTutorialRunning = false
                                    onTutorialFinished()
                                }
                            }).start()
                    }
                }
            }).start()
    }
}