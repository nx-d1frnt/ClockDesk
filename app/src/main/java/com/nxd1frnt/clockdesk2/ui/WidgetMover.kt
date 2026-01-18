package com.nxd1frnt.clockdesk2.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.util.Log
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Refactored WidgetMover with improved state management and bug fixes
 */
class WidgetMover(
    private val context: Context,
    private val views: List<View>,
    private val parentView: View
) {
    private val TAG = "WidgetMover"
    private val isLoggingEnabled = true

    private val prefs: SharedPreferences =
        context.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)

    // State Management
    private var currentMode: LayoutMode = LayoutMode.StackMode
    private var isEditMode = false
    private var isGridSnapEnabled = true

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
        log("WidgetMover initialized with ${views.size} views")
        loadInitialState()
    }

    private fun loadInitialState() {
        currentMode = if (prefs.getBoolean("free_movement_beta_enabled", false)) {
            LayoutMode.FreeMode
        } else {
            LayoutMode.StackMode
        }
        isGridSnapEnabled = prefs.getBoolean("grid_snap_enabled", true)
        log("Initial mode: $currentMode")
    }

    fun setOnInteractionListener(listener: (Boolean) -> Unit) {
        this.onInteractionListener = listener
    }

    private fun log(message: String) {
        if (isLoggingEnabled) Log.d(TAG, message)
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

    fun setFreeMovementEnabled(enabled: Boolean) {
        val targetMode = if (enabled) LayoutMode.FreeMode else LayoutMode.StackMode

        if (currentMode == targetMode) {
            log("Already in $targetMode, skipping")
            return
        }

        log("Switching mode: $currentMode -> $targetMode")

        // Save current positions before switching
        if (currentMode == LayoutMode.FreeMode) {
            views.forEach { savePosition(it) }
        }

        currentMode = targetMode
        prefs.edit().putBoolean("free_movement_beta_enabled", enabled).apply()
        prefs.edit().putBoolean("is_vertical_stack_mode", !enabled).apply()

        when (targetMode) {
            LayoutMode.StackMode -> {
                applySmartStack(saveOrder = true)
            }
            LayoutMode.FreeMode -> {
                val hasValidPositions = checkSavedPositions()
                if (hasValidPositions) {
                    restoreFreeMode()
                } else {
                    initializeDefaultFreePositions()
                }
            }
        }
    }

    fun isFreeMovementEnabled(): Boolean = currentMode is LayoutMode.FreeMode

    private fun checkSavedPositions(): Boolean {
        return views.any { view ->
            val idName = getResourceName(view.id)
            prefs.contains("${idName}_x") && prefs.contains("${idName}_y")
        }
    }

    private fun validateState(): Boolean {
        val savedStackMode = prefs.getBoolean("is_vertical_stack_mode", true)
        val savedFreeMode = prefs.getBoolean("free_movement_beta_enabled", false)

        // Fix inconsistent state
        if (savedFreeMode && savedStackMode) {
            log("Inconsistent state detected! Forcing Stack Mode.")
            prefs.edit()
                .putBoolean("free_movement_beta_enabled", false)
                .putBoolean("is_vertical_stack_mode", true)
                .apply()
            currentMode = LayoutMode.StackMode
            return false
        }

        return true
    }

    // ============================================================================
    // INITIALIZATION & DEFAULTS
    // ============================================================================

    private fun checkAndInitializeDefaults() {
        if (!prefs.contains("is_layout_initialized")) {
            log("First run detected! Applying default layout settings.")
            val editor = prefs.edit()
            editor.putBoolean("is_vertical_stack_mode", true)
            editor.putBoolean("free_movement_beta_enabled", false)
            editor.putBoolean("grid_snap_enabled", true)

            views.forEachIndexed { index, view ->
                val idName = getResourceName(view.id)
                editor.putInt("${idName}_order_index", index)
                editor.putInt("${idName}_align_h", ALIGN_H_LEFT)
                editor.putInt("${idName}_align_v", ALIGN_V_BOTTOM)
                editor.putInt("${idName}_internal_gravity", GRAVITY_CENTER)
                editor.putFloat("${idName}_x", 0f)
                editor.putFloat("${idName}_y", 0f)
            }

            editor.putBoolean("is_layout_initialized", true)
            editor.apply()
            log("Default settings initialized")
        }
    }

    private fun initializeDefaultFreePositions() {
        if (parentView !is ConstraintLayout) return

        log("Initializing default Free Mode positions...")

        parentView.post {
            val set = ConstraintSet()
            set.clone(parentView)

            val density = context.resources.displayMetrics.density
            val startY = 100f * density
            val spacing = 150f * density

            views.forEachIndexed { index, view ->
                // Clear all constraints
                clearAllConstraints(set, view)

                // Anchor to top-left
                set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP)
                set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
                set.setHorizontalBias(view.id, 0f)
                set.setVerticalBias(view.id, 0f)

                val x = 50f * density
                val y = startY + (index * spacing)

                view.post {
                    sanitizeAndApplyPosition(view, x, y)
                    savePosition(view)
                }
            }

            beginLayoutTransition() // Animate initialization if possible
            set.applyTo(parentView)
        }
    }

    // ============================================================================
    // RESTORE LOGIC
    // ============================================================================

    fun restoreOrderAndPositions() {
        if (parentView !is ConstraintLayout) {
            log("Parent is not ConstraintLayout, cannot restore")
            return
        }

        log("=== Restoring Order and Positions ===")

        // Load state
        loadInitialState()
        checkAndInitializeDefaults()

        if (!validateState()) {
            applySmartStack(saveOrder = false)
            return
        }

        parentView.post {
            views.forEach { view ->
                val idName = getResourceName(view.id)
                val internalGravity = prefs.getInt("${idName}_internal_gravity", GRAVITY_CENTER)
                applyInternalGravity(view, internalGravity)
            }

            // Step 2: Apply layout based on mode
            when (currentMode) {
                LayoutMode.StackMode -> {
                    log("Restoring Stack Mode")
                    applySmartStack(saveOrder = false)
                }
                LayoutMode.FreeMode -> {
                    log("Restoring Free Mode")
                    restoreFreeMode()
                }
            }
        }
    }

    private fun restoreFreeMode() {
        if (parentView !is ConstraintLayout) return

        log("Applying Free Mode layout...")

        val set = ConstraintSet()
        set.clone(parentView)

        views.forEach { view ->
            val idName = getResourceName(view.id)

            clearAllConstraints(set, view)

            set.connect(view.id, ConstraintSet.TOP, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.TOP)
            set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
            set.setHorizontalBias(view.id, 0f)
            set.setVerticalBias(view.id, 0f)

            // Load saved positions
            val savedX = prefs.getFloat("${idName}_x", 0f)
            val savedY = prefs.getFloat("${idName}_y", 0f)

            log("Restoring $idName: x=$savedX, y=$savedY")

            parentView.post {
                sanitizeAndApplyPosition(view, savedX, savedY)
            }
        }

        beginLayoutTransition()
        set.applyTo(parentView)
    }

    private fun applySmartStack(saveOrder: Boolean) {
        if (parentView !is ConstraintLayout) return

        log("Applying Smart Stack (saveOrder=$saveOrder)...")

        val set = ConstraintSet()
        set.clone(parentView)

        val sortedViews = views.sortedBy {
            prefs.getInt("${getResourceName(it.id)}_order_index", 0)
        }

        val verticalGap = (10 * context.resources.displayMetrics.density).toInt()

        sortedViews.forEachIndexed { index, view ->
            val idName = getResourceName(view.id)

            if (saveOrder) {
                prefs.edit().putInt("${idName}_order_index", index).apply()
            }

            set.clear(view.id, ConstraintSet.TOP)
            set.clear(view.id, ConstraintSet.BOTTOM)

            // Apply horizontal alignment
            val alignH = prefs.getInt("${idName}_align_h", ALIGN_H_CENTER)
            applyHorizontalConstraintToSet(set, view, alignH)

            // Reset translation animations safely
            view.animate().cancel()
            view.translationX = 0f
            view.translationY = 0f

            if (saveOrder) {
                savePositionRaw(view, 0f, 0f)
            }

            val prevView = if (index > 0) sortedViews[index - 1] else null
            val nextView = if (index < sortedViews.size - 1) sortedViews[index + 1] else null

            val topAnchorId = prevView?.id ?: ConstraintLayout.LayoutParams.PARENT_ID
            val topAnchorSide = if (prevView != null) ConstraintSet.BOTTOM else ConstraintSet.TOP
            val bottomAnchorId = nextView?.id ?: ConstraintLayout.LayoutParams.PARENT_ID
            val bottomAnchorSide = if (nextView != null) ConstraintSet.TOP else ConstraintSet.BOTTOM

            set.connect(view.id, ConstraintSet.TOP, topAnchorId, topAnchorSide, verticalGap)
            set.connect(view.id, ConstraintSet.BOTTOM, bottomAnchorId, bottomAnchorSide, verticalGap)

            // Apply vertical bias
            val alignV = prefs.getInt("${idName}_align_v", ALIGN_V_CENTER)
            val bias = when (alignV) {
                ALIGN_V_TOP -> 0.0f
                ALIGN_V_BOTTOM -> 1.0f
                else -> 0.5f
            }
            set.setVerticalBias(view.id, bias)

            // Set chain style on first element
            if (index == 0) {
                set.setVerticalChainStyle(view.id, ConstraintSet.CHAIN_PACKED)
            }
        }

        beginLayoutTransition()
        set.applyTo(parentView)
        log("✓ Smart Stack applied")
    }

    // ============================================================================
    // POSITION UTILITIES
    // ============================================================================

    private fun sanitizeAndApplyPosition(view: View, desiredX: Float, desiredY: Float) {
        // Wait for layout if not ready
        if (parentView.width == 0 || parentView.height == 0 || view.width == 0 || view.height == 0) {
            parentView.post {
                sanitizeAndApplyPosition(view, desiredX, desiredY)
            }
            return
        }

        val minTransX = -view.left.toFloat()
        val maxTransX = (parentView.width - view.width).toFloat() - view.left
        val minTransY = -view.top.toFloat()
        val maxTransY = (parentView.height - view.height).toFloat() - view.top

        val finalX = desiredX.coerceIn(minTransX, maxTransX)
        val finalY = desiredY.coerceIn(minTransY, maxTransY)

        view.translationX = finalX
        view.translationY = finalY

        log("Sanitized position for ${getResourceName(view.id)}: ($desiredX, $desiredY) -> ($finalX, $finalY)")
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
        if (currentMode !is LayoutMode.FreeMode) {
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

        log("Started dragging ${getResourceName(view.id)}")

        // Prepare layout for drag
        transitionToFreeMode()
        healOrphans(view)
        prepareViewForDrag(view)

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
        if (checkCollision(view)) {
            log("Collision detected, reverting position")
            Toast.makeText(context, "Cannot place here - overlaps another widget", Toast.LENGTH_SHORT).show()
            restoreOrderAndPositions()
        } else {
            log("Drag ended, saving position")
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

    private fun transitionToFreeMode() {
        if (currentMode is LayoutMode.StackMode) {
            log("Transitioning from Stack to Free Mode")
            currentMode = LayoutMode.FreeMode
            prefs.edit()
                .putBoolean("is_vertical_stack_mode", false)
                .putBoolean("free_movement_beta_enabled", true)
                .apply()
        }
    }

    private fun updateCollisionFeedback(view: View) {
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

        log("Healing orphans (excluding ${getResourceName(draggingView.id)})")

        val set = ConstraintSet()
        set.clone(parentView)

        views.forEach { view ->
            if (view === draggingView) return@forEach

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

        // Reset constraints to top-left anchor
        val lp = view.layoutParams as? ConstraintLayout.LayoutParams ?: return

        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
        lp.horizontalBias = 0f
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        lp.topToBottom = ConstraintLayout.LayoutParams.UNSET
        lp.verticalBias = 0f
        lp.setMargins(0, 0, 0, 0)

        view.layoutParams = lp
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
                log("Collision: ${getResourceName(activeView.id)} <-> ${getResourceName(other.id)}")
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
        log("Move order: ${getResourceName(activeView.id)} ${if (moveUp) "UP" else "DOWN"}")

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

        // Switch to stack mode and save new order
        currentMode = LayoutMode.StackMode
        prefs.edit().putBoolean("is_vertical_stack_mode", true).apply()
        prefs.edit().putBoolean("free_movement_beta_enabled", false).apply()

        sortedViews.forEachIndexed { index, view ->
            prefs.edit().putInt("${getResourceName(view.id)}_order_index", index).apply()
        }

        applySmartStack(saveOrder = true)
    }

    fun alignViewVertical(view: View, mode: Int) {
        log("Align vertical: ${getResourceName(view.id)} -> $mode")
        saveAlignmentOnlyV(view, mode)

        currentMode = LayoutMode.StackMode
        prefs.edit().putBoolean("is_vertical_stack_mode", true).apply()
        prefs.edit().putBoolean("free_movement_beta_enabled", false).apply()

        applySmartStack(saveOrder = false)
    }

    fun alignViewHorizontal(view: View, mode: Int) {
        log("Align horizontal: ${getResourceName(view.id)} -> $mode")

        //start the layout transition
        beginLayoutTransition()

        applyConstraintAlignment(view, mode)

        view.animate().cancel()
        view.translationX = 0f

        savePositionRaw(view, 0f, view.translationY)
        saveAlignmentOnlyH(view, mode)
    }

    fun setTextGravity(view: View, mode: Int) {
        log("Set text gravity: ${getResourceName(view.id)} -> $mode")
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

    private fun applyConstraintAlignment(view: View, mode: Int) {
        val lp = view.layoutParams as? ConstraintLayout.LayoutParams ?: return

        when (mode) {
            ALIGN_H_LEFT -> {
                lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
                lp.horizontalBias = 0f
            }
            ALIGN_H_CENTER -> {
                lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                lp.horizontalBias = 0.5f
            }
            ALIGN_H_RIGHT -> {
                lp.startToStart = ConstraintLayout.LayoutParams.UNSET
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                lp.horizontalBias = 1f
            }
        }

        view.layoutParams = lp
    }

    private fun applyHorizontalConstraintToSet(set: ConstraintSet, view: View, mode: Int) {
        when (mode) {
            ALIGN_H_LEFT -> {
                set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
                set.clear(view.id, ConstraintSet.END)
                set.setHorizontalBias(view.id, 0f)
            }
            ALIGN_H_CENTER -> {
                set.connect(view.id, ConstraintSet.START, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.START)
                set.connect(view.id, ConstraintSet.END, ConstraintLayout.LayoutParams.PARENT_ID, ConstraintSet.END)
                set.setHorizontalBias(view.id, 0.5f)
            }
            ALIGN_H_RIGHT -> {
                set.clear(view.id, ConstraintSet.START)
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
        log("Edit mode: $enabled")

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
        log("Grid snap: $enabled")
    }

    fun isGridSnapEnabled(): Boolean = isGridSnapEnabled

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
        log("Saved position for $idName: ($x, $y)")
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
        if (!isLoggingEnabled) return

        log("=== DEBUG: Widget Positions ===")
        views.forEach { view ->
            val idName = getResourceName(view.id)
            val savedX = prefs.getFloat("${idName}_x", -1f)
            val savedY = prefs.getFloat("${idName}_y", -1f)
            log("""
                $idName:
                  Visual: x=${view.x}, y=${view.y}
                  Translation: x=${view.translationX}, y=${view.translationY}
                  Saved: x=$savedX, y=$savedY
                  Align H: ${getAlignmentOnlyH(view)}
                  Align V: ${getAlignmentOnlyV(view)}
                  Gravity: ${getInternalGravity(view)}
            """.trimIndent())
        }
        log("Mode: $currentMode")
        log("================================")
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