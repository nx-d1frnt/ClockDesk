package com.nxd1frnt.clockdesk2.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.Logger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Total vibecode bruh since it's 1 AM already and I don't wanna deal with this properly (someone help pls)
 * Sorry for the mess in advance
 * works somewhat okay now tho
 */


/**
 * WidgetMover.kt
 * Manages movable widgets within a parent view, allowing users to drag and reorder them.
 */
class WidgetMover(
    private val context: Context,
    private val views: List<View>,
    private val parentView: View
) {
    private val TAG = "WidgetMover"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)

    // State Management
    private var currentMode: LayoutMode = LayoutMode.StackMode
    private var isEditMode = false
    private var isGridSnapEnabled = true
    private var isCollisionCheckEnabled = true


    // Touch Handling
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dX = 0f
    private var dY = 0f
    private var lastToastTime = 0L

    // Grid Configuration
    private val gridSize: Float = 18f * context.resources.displayMetrics.density

    // View State Restoration
    private val originalViewStates = mutableMapOf<View, ViewState>()

    private val defaultGapDp = 16
    // Constants
    val ALIGN_H_LEFT = 0
    val ALIGN_H_CENTER = 1
    val ALIGN_H_RIGHT = 2
    val ALIGN_V_TOP = 0
    val ALIGN_V_CENTER = 1
    val ALIGN_V_BOTTOM = 2
    val GRAVITY_START = 0
    val GRAVITY_CENTER = 1
    val GRAVITY_END = 2


    private var onInteractionListener: ((Boolean) -> Unit)? = null

    // ============================================================================
    // INITIALIZATION & STATE MANAGEMENT
    // ============================================================================

    init {
        Logger.d("WidgetMover"){"WidgetMover initialized with ${views.size} views"}
        loadInitialState()

        parentView.post {
            if (parentView.width > 0 && parentView.height > 0) {
                Logger.d("WidgetMover"){"Auto-initializing widget positions..."}
                checkAndInitializeDefaults()
                restoreOrderAndPositions()
            } else {
                Logger.d("WidgetMover"){"Layout not ready, skipping auto-init"}
            }
        }
    }

    private fun loadInitialState() {
        currentMode = if (prefs.getBoolean("free_movement_beta_enabled", false)) {
            LayoutMode.FreeMode
        } else {
            LayoutMode.StackMode
        }
        isGridSnapEnabled = prefs.getBoolean("grid_snap_enabled", true)
        isCollisionCheckEnabled = prefs.getBoolean("collision_check_enabled", true)
        Logger.d("WidgetMover"){"Initial mode: $currentMode"}
    }

    fun setOnInteractionListener(listener: (Boolean) -> Unit) {
        this.onInteractionListener = listener
    }


    private fun beginLayoutTransition() {
        // TransitionManager available since API 19 (KitKat).
        // On older devices, changes will happen instantly without animation, which is safe.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && parentView is ViewGroup) {
            val transition = android.transition.AutoTransition()
            transition.duration = 300 // 300ms for smoothness
            // Soft bounce
            transition.interpolator = OvershootInterpolator(0.8f)
            android.transition.TransitionManager.beginDelayedTransition(parentView, transition)
        }
    }

    // ============================================================================
    // MODE MANAGEMENT
    // ============================================================================

    sealed class LayoutMode {
        object StackMode : LayoutMode()
        object FreeMode : LayoutMode()

        fun canDrag(): Boolean = this is FreeMode

        override fun toString(): String = when(this) {
            is StackMode -> "StackMode"
            is FreeMode -> "FreeMode"
        }
    }

    fun setFreeMovementEnabled(view: View, enabled: Boolean) {
        val idName = getResourceName(view.id)
        prefs.edit().putBoolean("${idName}_individual_free_mode", enabled).apply()
        restoreOrderAndPositions()
    }

    fun isFreeMovementEnabled(view: View): Boolean {
        val idName = getResourceName(view.id)
        return prefs.getBoolean("${idName}_individual_free_mode", false)
    }

    private fun checkSavedPositions(): Boolean {
        return views.any { view ->
            val idName = getResourceName(view.id)
            prefs.contains("${idName}_x") && prefs.contains("${idName}_y")
        }
    }

    // ============================================================================
    // INITIALIZATION & DEFAULTS
    // ============================================================================

    private fun checkAndInitializeDefaults() {
        if (!prefs.contains("is_layout_initialized")) {
            Logger.d("WidgetMover"){"First run detected! Applying default layout settings."}
            val editor = prefs.edit()
            editor.putBoolean("is_vertical_stack_mode", true)
            editor.putBoolean("grid_snap_enabled", true)
            editor.putBoolean("collision_check_enabled", true)

            views.forEachIndexed { index, view ->
                val idName = getResourceName(view.id)
                editor.putInt("${idName}_order_index", index)
                editor.putInt("${idName}_align_h", ALIGN_H_LEFT)
                editor.putInt("${idName}_align_v", ALIGN_V_BOTTOM)
                editor.putInt("${idName}_internal_gravity", GRAVITY_CENTER)
                editor.putFloat("${idName}_x", 0f)
                editor.putFloat("${idName}_y", 0f)
                editor.putBoolean("${idName}_individual_free_mode", false)
            }

            editor.putBoolean("is_layout_initialized", true)
            editor.apply()
            Logger.d("WidgetMover"){"Default settings initialized"}
        }
    }


    // ============================================================================
    // RESTORE LOGIC
    // ============================================================================

    fun restoreOrderAndPositions() {
        if (parentView !is ConstraintLayout) return

        // loadInitialState()
        // checkAndInitializeDefaults()

        parentView.post {
            views.forEach { view ->
                val idName = getResourceName(view.id)
                val internalGravity = prefs.getInt("${idName}_internal_gravity", GRAVITY_CENTER)
                applyInternalGravity(view, internalGravity)
            }

            applyHybridLayout()
        }
    }

    private fun applyHybridLayout() {
        val set = ConstraintSet()
        set.clone(parentView as ConstraintLayout)

        val allSorted = views.sortedBy {
            prefs.getInt("${getResourceName(it.id)}_order_index", 0)
        }

        val stackedViews = allSorted.filter { !isFreeMovementEnabled(it) }
        val freeViews = allSorted.filter { isFreeMovementEnabled(it) }

        val density = context.resources.displayMetrics.density
        val normalGapPx = (defaultGapDp * density).toInt()
        val smallGapPx = (5 * density).toInt()

        views.forEach { clearAllConstraints(set, it) }

        if (stackedViews.isNotEmpty()) {
            var lastTopIndex = -1
            for (i in stackedViews.indices) {
                if (getAlignmentOnlyV(stackedViews[i]) == ALIGN_V_TOP) lastTopIndex = i else break
            }

            var firstBottomIndex = stackedViews.size
            for (i in stackedViews.indices.reversed()) {
                if (getAlignmentOnlyV(stackedViews[i]) == ALIGN_V_BOTTOM) firstBottomIndex = i else break
            }

            if (firstBottomIndex <= lastTopIndex) firstBottomIndex = lastTopIndex + 1

            for (i in 0..lastTopIndex) {
                val view = stackedViews[i]
                val prevView = if (i > 0) stackedViews[i - 1] else null

                if (prevView == null) {
                    set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP, 0)
                } else {
                    val gap = calculateSmartGap(prevView, view, normalGapPx, smallGapPx)
                    set.connect(view.id, ConstraintSet.TOP, prevView.id, ConstraintSet.BOTTOM, gap)
                }
                applyHorizontalLogic(set, view)
            }

            for (i in stackedViews.size - 1 downTo firstBottomIndex) {
                val view = stackedViews[i]
                val nextView = if (i < stackedViews.size - 1) stackedViews[i + 1] else null

                if (nextView == null) {
                    set.connect(view.id, ConstraintSet.BOTTOM, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.BOTTOM, 0)
                } else {
                    val gap = calculateSmartGap(view, nextView, normalGapPx, smallGapPx)
                    set.connect(view.id, ConstraintSet.BOTTOM, nextView.id, ConstraintSet.TOP, gap)
                }
                applyHorizontalLogic(set, view)
            }

            val middleStart = lastTopIndex + 1
            val middleEnd = firstBottomIndex - 1

            if (middleStart <= middleEnd) {
                val head = stackedViews[middleStart]
                val tail = stackedViews[middleEnd]

                if (lastTopIndex >= 0) {
                    val topAnchor = stackedViews[lastTopIndex]
                    val gap = calculateSmartGap(topAnchor, head, normalGapPx, smallGapPx)
                    set.connect(head.id, ConstraintSet.TOP, topAnchor.id, ConstraintSet.BOTTOM, gap)
                } else {
                    set.connect(head.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP, normalGapPx)
                }

                if (firstBottomIndex < stackedViews.size) {
                    val bottomAnchor = stackedViews[firstBottomIndex]
                    val gap = calculateSmartGap(tail, bottomAnchor, normalGapPx, smallGapPx)
                    set.connect(tail.id, ConstraintSet.BOTTOM, bottomAnchor.id, ConstraintSet.TOP, gap)
                } else {
                    set.connect(tail.id, ConstraintSet.BOTTOM, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.BOTTOM, normalGapPx)
                }

                set.setVerticalChainStyle(head.id, ConstraintSet.CHAIN_PACKED)
                set.setVerticalBias(head.id, 0.5f)

                for (i in middleStart..middleEnd) {
                    val view = stackedViews[i]
                    applyHorizontalLogic(set, view)

                    if (i > middleStart) {
                        val prev = stackedViews[i - 1]

                        val gap = calculateSmartGap(prev, view, normalGapPx, smallGapPx)

                        set.connect(view.id, ConstraintSet.TOP, prev.id, ConstraintSet.BOTTOM, gap)
                        set.connect(prev.id, ConstraintSet.BOTTOM, view.id, ConstraintSet.TOP, 0)
                    }
                }
            }

            stackedViews.forEach {
                it.visibility = View.VISIBLE
                it.animate().translationX(0f).translationY(0f).setDuration(300).start()
            }
        }

        freeViews.forEach { view ->
            val idName = getResourceName(view.id)
            set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP)
            set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
            val savedX = prefs.getFloat("${idName}_x", 0f)
            val savedY = prefs.getFloat("${idName}_y", 0f)
            parentView.post { sanitizeAndApplyPosition(view, savedX, savedY) }
        }

        beginLayoutTransition()
        set.applyTo(parentView)
    }

    private fun calculateSmartGap(topView: View, bottomView: View, normalGap: Int, smallGap: Int): Int {
        val topAlign = getAlignmentOnlyV(topView)
        val bottomAlign = getAlignmentOnlyV(bottomView)

        if (topAlign == ALIGN_V_BOTTOM) return smallGap

        if (bottomAlign == ALIGN_V_TOP) return smallGap

        return normalGap
    }

    private fun applyHorizontalLogic(set: ConstraintSet, view: View) {
        val alignH = prefs.getInt("${getResourceName(view.id)}_align_h", ALIGN_H_CENTER)
        applyHorizontalConstraintToSet(set, view, alignH)
    }

    // ============================================================================
    // POSITION UTILITIES
    // ============================================================================

    private fun sanitizeAndApplyPosition(view: View, desiredX: Float, desiredY: Float) {
        // Чекаємо поки layout буде готовий
        if (parentView.width == 0 || parentView.height == 0 || view.width == 0 || view.height == 0) {
            parentView.post {
                sanitizeAndApplyPosition(view, desiredX, desiredY)
            }
            return
        }

        val isFree = isFreeMovementEnabled(view)

        val finalX: Float
        val finalY: Float

        if (isFree) {
            val maxTransX = (parentView.width - view.width).toFloat().coerceAtLeast(0f)
            val maxTransY = (parentView.height - view.height).toFloat().coerceAtLeast(0f)

            finalX = desiredX.coerceIn(0f, maxTransX)
            finalY = desiredY.coerceIn(0f, maxTransY)
        } else {
            // Для стекових віджетів: використовуємо відносні координати
            val minTransX = -view.left.toFloat()
            val maxTransX = (parentView.width - view.width).toFloat() - view.left
            val minTransY = -view.top.toFloat()
            val maxTransY = (parentView.height - view.height).toFloat() - view.top

            finalX = desiredX.coerceIn(minTransX, maxTransX)
            finalY = desiredY.coerceIn(minTransY, maxTransY)
        }

        view.translationX = finalX
        view.translationY = finalY

        Logger.d("WidgetMover"){"Sanitized position for ${getResourceName(view.id)}: ($desiredX, $desiredY) -> ($finalX, $finalY) [free=$isFree]"}
    }

    // ============================================================================
    // TOUCH HANDLING
    // ============================================================================

    private val dragListener = View.OnTouchListener { view, event ->
        if (!isEditMode) return@OnTouchListener false

        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(view, rawX, rawY)
            MotionEvent.ACTION_MOVE -> handleTouchMove(view, rawX, rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(view)
            else -> false
        }
    }

    private fun handleTouchDown(view: View, rawX: Float, rawY: Float): Boolean {
        isDragging = false
        initialTouchX = rawX
        initialTouchY = rawY
        dX = view.translationX - rawX
        dY = view.translationY - rawY

        // Visual feedback
        view.animate().cancel()
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(100)
            .start()

        return true
    }

    private fun handleTouchMove(view: View, rawX: Float, rawY: Float): Boolean {
        // Check if Free Mode is enabled
        val isIndividualFree = isFreeMovementEnabled(view)
        val canMove = isIndividualFree

        if (!canMove) {
            if (!isDragging) {
                val moved = abs(rawX - initialTouchX) > touchSlop || abs(rawY - initialTouchY) > touchSlop
                if (moved) {
                    showFreeModeHint()
                }
            }
            return true
        }

        // Start dragging if threshold exceeded
        if (!isDragging) {
            val moved = abs(rawX - initialTouchX) > touchSlop || abs(rawY - initialTouchY) > touchSlop
            if (moved) {
                startDragging(view, rawX, rawY)
            }
        }

        // Update position while dragging
        if (isDragging) {
            updateDragPosition(view, rawX, rawY)
        }

        return true
    }

    private fun startDragging(view: View, rawX: Float, rawY: Float) {
        isDragging = true
        onInteractionListener?.invoke(true)

        Logger.d("WidgetMover"){"Started dragging ${getResourceName(view.id)}"}

        if (isFreeMovementEnabled(view)) {
            healOrphans(view)
            prepareViewForDrag(view)
        }

        // Recalculate delta
        dX = view.translationX - rawX
        dY = view.translationY - rawY
    }

    private fun updateDragPosition(view: View, rawX: Float, rawY: Float) {
        var targetX = rawX + dX
        var targetY = rawY + dY

        // Apply grid snap
        if (isGridSnapEnabled) {
            targetX = (targetX / gridSize).roundToInt() * gridSize
            targetY = (targetY / gridSize).roundToInt() * gridSize
        }

        // Clamp to bounds
        val maxTransX = (parentView.width - view.width).toFloat()
        val maxTransY = (parentView.height - view.height).toFloat()
        targetX = targetX.coerceIn(0f, maxTransX)
        targetY = targetY.coerceIn(0f, maxTransY)

        view.translationX = targetX
        view.translationY = targetY

        // Visual collision feedback
        updateCollisionFeedback(view)
    }

    private fun handleTouchUp(view: View): Boolean {
        // Reset visual state
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()

        view.setBackgroundResource(R.drawable.editable_border)
        view.background?.clearColorFilter()

        if (isDragging) {
            handleDragEnd(view)
            onInteractionListener?.invoke(false)
        } else {
            // This was a click
            view.performClick()
        }

        isDragging = false
        return true
    }

    private fun handleDragEnd(view: View) {
        if (isCollisionCheckEnabled && checkCollision(view)) {
            Logger.d("WidgetMover"){"Collision detected, reverting position"}
            Toast.makeText(context, "Cannot place here - overlaps another widget", Toast.LENGTH_SHORT).show()
            restoreOrderAndPositions()
        } else {
            Logger.d("WidgetMover"){"Drag ended, saving position"}
            savePosition(view)
        }
    }

    private fun showFreeModeHint() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > 3000) {
            Toast.makeText(context, "Enable Free Mode to drag widgets", Toast.LENGTH_SHORT).show()
            lastToastTime = currentTime
        }
    }


    private fun updateCollisionFeedback(view: View) {
        if (!isCollisionCheckEnabled) {
            view.background?.setColorFilter(
                Color.argb(100, 0, 255, 0),
                PorterDuff.Mode.SRC_ATOP
            )
            return
        }

        // Якщо перевірка увімкнена - показуємо червоний/зелений
        if (checkCollision(view)) {
            view.background?.setColorFilter(
                Color.argb(100, 255, 0, 0),
                PorterDuff.Mode.SRC_ATOP
            )
        } else {
            view.background?.setColorFilter(
                Color.argb(100, 0, 255, 0),
                PorterDuff.Mode.SRC_ATOP
            )
        }
    }


    // ============================================================================
    // DRAG PREPARATION
    // ============================================================================

    private fun healOrphans(draggingView: View) {
        if (parentView !is ConstraintLayout) return

        Logger.d("WidgetMover"){"Healing orphans (excluding ${getResourceName(draggingView.id)})"}

        val set = ConstraintSet()
        set.clone(parentView)

        views.forEach { view ->
            if (view === draggingView) return@forEach

            if (!isFreeMovementEnabled(view)) {
                Logger.d("WidgetMover"){"Skipping ${getResourceName(view.id)} - it's in stack mode"}
                return@forEach
            }

            val idName = getResourceName(view.id)

            // Load saved positions
            val savedX = prefs.getFloat("${idName}_x", view.translationX)
            val savedY = prefs.getFloat("${idName}_y", view.translationY)

            // Clear and reanchor
            clearAllConstraints(set, view)
            set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP)
            set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
            set.setVerticalBias(view.id, 0f)
            set.setHorizontalBias(view.id, 0f)

            // Apply saved positions
            parentView.post {
                sanitizeAndApplyPosition(view, savedX, savedY)
            }
        }

        beginLayoutTransition()
        set.applyTo(parentView)
    }

    private fun prepareViewForDrag(view: View) {
        val currentVisualX = view.x
        val currentVisualY = view.y
        val savedGravity = getInternalGravity(view)

        if (parentView !is ConstraintLayout) return

        val set = ConstraintSet()
        set.clone(parentView as ConstraintLayout)

        clearAllConstraints(set, view)

        set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP)
        set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
        set.setVerticalBias(view.id, 0f)
        set.setHorizontalBias(view.id, 0f)

        set.applyTo(parentView)

        applyInternalGravity(view, savedGravity)

        // Maintain visual position using translation
        view.translationX = currentVisualX
        view.translationY = currentVisualY
    }

    // ============================================================================
    // COLLISION DETECTION
    // ============================================================================

    private fun checkCollision(activeView: View): Boolean {
        val activeRect = getViewRect(activeView)

        // Dynamic inset based on view size
        val inset = minOf(activeView.width, activeView.height) / 10
        activeRect.inset(inset, inset)

        for (other in views) {
            if (other === activeView) continue
            if (other.visibility != View.VISIBLE) continue

            val otherRect = getViewRect(other)
            if (Rect.intersects(activeRect, otherRect)) {
                Logger.d("WidgetMover"){"Collision: ${getResourceName(activeView.id)} <-> ${getResourceName(other.id)}"}
                return true
            }
        }

        return false
    }

    private fun getViewRect(view: View): Rect {
        val left = (view.left + view.translationX).toInt()
        val top = (view.top + view.translationY).toInt()
        return Rect(left, top, left + view.width, top + view.height)
    }

    // ============================================================================
    // BUTTON ACTIONS
    // ============================================================================

    fun moveWidgetOrder(activeView: View, moveUp: Boolean) {
        Logger.d("WidgetMover"){"Move order: ${getResourceName(activeView.id)} ${if (moveUp) "UP" else "DOWN"}"}

        // Якщо віджет у вільному режимі, він не може бути переміщений в стеку
        if (isFreeMovementEnabled(activeView)) {
            Toast.makeText(context, "Cannot reorder free-moving widget. Disable free mode first.", Toast.LENGTH_SHORT).show()
            return
        }

        val sortedViews = views.sortedBy {
            prefs.getInt("${getResourceName(it.id)}_order_index", 0)
        }.toMutableList()

        val currentIndex = sortedViews.indexOf(activeView)
        if (currentIndex == -1) return

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= sortedViews.size) return

        // Swap
        val temp = sortedViews[currentIndex]
        sortedViews[currentIndex] = sortedViews[targetIndex]
        sortedViews[targetIndex] = temp

        sortedViews.forEachIndexed { index, view ->
            prefs.edit().putInt("${getResourceName(view.id)}_order_index", index).apply()
        }

        restoreOrderAndPositions()
    }

    fun alignViewVertical(view: View, mode: Int) {
        Logger.d("WidgetMover"){"Align vertical: ${getResourceName(view.id)} -> $mode"}
        saveAlignmentOnlyV(view, mode)
        restoreOrderAndPositions()
    }

    fun alignViewHorizontal(view: View, mode: Int) {
        Logger.d("WidgetMover"){"Align horizontal: ${getResourceName(view.id)} -> $mode"}

        saveAlignmentOnlyH(view, mode)

        val isFree = isFreeMovementEnabled(view)

        if (isFree) {
            beginLayoutTransition()

            if (parentView is ConstraintLayout) {
                val set = ConstraintSet()
                set.clone(parentView)

                applyHorizontalConstraintToSet(set, view, mode)

                set.applyTo(parentView)
            }

            val savedY = prefs.getFloat("${getResourceName(view.id)}_y", view.translationY)
            view.translationX = 0f
            savePositionRaw(view, 0f, savedY)

        } else {
            beginLayoutTransition()
            restoreOrderAndPositions()
        }
    }

    fun setTextGravity(view: View, mode: Int) {
        Logger.d("WidgetMover"){"Set text gravity: ${getResourceName(view.id)} -> $mode"}
        applyInternalGravity(view, mode)
        saveInternalGravity(view, mode)
        view.invalidate()
        view.requestLayout()
    }

    // ============================================================================
    // CONSTRAINT HELPERS
    // ============================================================================

    private fun clearAllConstraints(set: ConstraintSet, view: View) {
        set.clear(view.id, ConstraintSet.TOP)
        set.clear(view.id, ConstraintSet.BOTTOM)
        set.clear(view.id, ConstraintSet.START)
        set.clear(view.id, ConstraintSet.END)
        set.clear(view.id, ConstraintSet.LEFT)
        set.clear(view.id, ConstraintSet.RIGHT)
    }


    private fun applyHorizontalConstraintToSet(set: ConstraintSet, view: View, mode: Int) {
        set.clear(view.id, ConstraintSet.START)
        set.clear(view.id, ConstraintSet.END)
        set.clear(view.id, ConstraintSet.LEFT)
        set.clear(view.id, ConstraintSet.RIGHT)

        when (mode) {
            ALIGN_H_LEFT -> {
                set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
                set.setHorizontalBias(view.id, 0f)
            }
            ALIGN_H_CENTER -> {
                set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
                set.connect(view.id, ConstraintSet.END, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.END)
                set.setHorizontalBias(view.id, 0.5f)
            }
            ALIGN_H_RIGHT -> {
                set.connect(view.id, ConstraintSet.END, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.END)
                set.setHorizontalBias(view.id, 1f)
            }
        }
    }

    private fun applyInternalGravity(view: View, mode: Int) {
        val hGravity = when (mode) {
            GRAVITY_START -> Gravity.START
            GRAVITY_CENTER -> Gravity.CENTER_HORIZONTAL
            GRAVITY_END -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        val targetGravity = hGravity or Gravity.CENTER_VERTICAL

        when (view) {
            is TextView -> {
                view.gravity = targetGravity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    view.textAlignment = when (mode) {
                        GRAVITY_START -> View.TEXT_ALIGNMENT_VIEW_START
                        GRAVITY_CENTER -> View.TEXT_ALIGNMENT_CENTER
                        GRAVITY_END -> View.TEXT_ALIGNMENT_VIEW_END
                        else -> View.TEXT_ALIGNMENT_CENTER
                    }
                }
            }
            is LinearLayout -> {
                view.gravity = targetGravity
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (child is TextView) {
                        child.gravity = targetGravity
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            child.textAlignment = when (mode) {
                                GRAVITY_START -> View.TEXT_ALIGNMENT_VIEW_START
                                GRAVITY_CENTER -> View.TEXT_ALIGNMENT_CENTER
                                GRAVITY_END -> View.TEXT_ALIGNMENT_VIEW_END
                                else -> View.TEXT_ALIGNMENT_CENTER
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    view.textAlignment = when (mode) {
                        GRAVITY_START -> View.TEXT_ALIGNMENT_VIEW_START
                        GRAVITY_CENTER -> View.TEXT_ALIGNMENT_CENTER
                        GRAVITY_END -> View.TEXT_ALIGNMENT_VIEW_END
                        else -> View.TEXT_ALIGNMENT_CENTER
                    }
                }
            }
        }
    }

    // ============================================================================
    // EDIT MODE
    // ============================================================================

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        Logger.d("WidgetMover"){"Edit mode: $enabled"}

        if (enabled) {
            restoreOrderAndPositions()
            views.forEach { view ->
                view.animate().cancel()

                // Save original state
                if (!originalViewStates.containsKey(view)) {
                    originalViewStates[view] = ViewState(
                        visibility = view.visibility,
                        alpha = view.alpha,
                        scaleX = view.scaleX,
                        scaleY = view.scaleY
                    )
                }

                // Make all views visible and interactive
                view.visibility = View.VISIBLE
                //view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                view.setOnTouchListener(dragListener)
                view.setBackgroundResource(R.drawable.editable_border)
            }
        } else {
            views.forEach { view ->
                view.setOnTouchListener(null)

                // Clear background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.background = null
                } else {
                    @Suppress("DEPRECATION")
                    view.setBackgroundDrawable(null)
                }

                // Restore original state
                originalViewStates[view]?.let { state ->
                    view.visibility = state.visibility
                    view.alpha = state.alpha
                    view.scaleX = state.scaleX
                    view.scaleY = state.scaleY
                }
            }
            originalViewStates.clear()
        }
    }

    fun setGridSnapEnabled(enabled: Boolean) {
        isGridSnapEnabled = enabled
        prefs.edit().putBoolean("grid_snap_enabled", enabled).apply()
        Logger.d("WidgetMover"){"Grid snap: $enabled"}
    }

    fun setCollisionCheckEnabled(enabled: Boolean) {
        isCollisionCheckEnabled = enabled
        prefs.edit().putBoolean("collision_check_enabled", enabled).apply()
        Logger.d("WidgetMover"){"Collision check: $enabled"}
    }

    fun isGridSnapEnabled(): Boolean = isGridSnapEnabled

    fun isCollisionCheckEnabled(): Boolean = isCollisionCheckEnabled

    // ============================================================================
    // PERSISTENCE
    // ============================================================================

    private fun savePosition(view: View) {
        savePositionRaw(view, view.translationX, view.translationY)
    }

    private fun savePositionRaw(view: View, x: Float, y: Float) {
        val idName = getResourceName(view.id)
        prefs.edit()
            .putFloat("${idName}_x", x)
            .putFloat("${idName}_y", y)
            .apply()
        Logger.d("WidgetMover"){"Saved position for $idName: ($x, $y)"}
    }

    private fun saveAlignmentOnlyV(view: View, v: Int) {
        val idName = getResourceName(view.id)
        prefs.edit().putInt("${idName}_align_v", v).apply()
    }

    private fun saveAlignmentOnlyH(view: View, h: Int) {
        val idName = getResourceName(view.id)
        prefs.edit().putInt("${idName}_align_h", h).apply()
    }

    private fun saveInternalGravity(view: View, gravity: Int) {
        val idName = getResourceName(view.id)
        prefs.edit().putInt("${idName}_internal_gravity", gravity).apply()
    }

    fun getInternalGravity(view: View): Int {
        val idName = getResourceName(view.id)
        return prefs.getInt("${idName}_internal_gravity", GRAVITY_CENTER)
    }

    fun getAlignmentOnlyH(view: View): Int {
        val idName = getResourceName(view.id)
        return prefs.getInt("${idName}_align_h", ALIGN_H_CENTER)
    }

    fun getAlignmentOnlyV(view: View): Int {
        val idName = getResourceName(view.id)
        return prefs.getInt("${idName}_align_v", ALIGN_V_CENTER)
    }

    // ============================================================================
    // UTILITIES
    // ============================================================================

    private fun getResourceName(id: Int): String {
        return try {
            context.resources.getResourceEntryName(id)
        } catch (e: Exception) {
            "ID:$id"
        }
    }

    fun debugPrintPositions() {
        Logger.d("WidgetMover"){"=== DEBUG: Widget Positions ==="}
        views.forEach { view ->
            val idName = getResourceName(view.id)
            val savedX = prefs.getFloat("${idName}_x", -1f)
            val savedY = prefs.getFloat("${idName}_y", -1f)
            Logger.d("WidgetMover"){"""
                $idName:
                  Visual: x=${view.x}, y=${view.y}
                  Translation: x=${view.translationX}, y=${view.translationY}
                  Saved: x=$savedX, y=$savedY
                  Align H: ${getAlignmentOnlyH(view)}
                  Align V: ${getAlignmentOnlyV(view)}
                  Gravity: ${getInternalGravity(view)}
            """.trimIndent()}
        }
        Logger.d("WidgetMover"){"Mode: $currentMode"}
        Logger.d("WidgetMover"){"================================"}
    }

    // ============================================================================
    // DATA CLASSES
    // ============================================================================

    private data class ViewState(
        val visibility: Int,
        val alpha: Float,
        val scaleX: Float,
        val scaleY: Float
    )
}