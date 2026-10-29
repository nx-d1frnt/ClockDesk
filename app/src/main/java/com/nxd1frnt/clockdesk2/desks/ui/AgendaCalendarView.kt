package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.agenda.AgendaDate
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class AgendaCalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var selectedDate: AgendaDate = AgendaDate.fromMillis(System.currentTimeMillis())
        private set
    var onDateSelected: ((AgendaDate) -> Unit)? = null
    private var followToday = true
    private val monthTitle = TextView(context).apply {
        textSize = 18f
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        ViewCompat.setAccessibilityHeading(this, true)
    }
    private val previous = navButton(R.drawable.ic_arrow_left, R.string.agenda_previous_month) { moveMonth(-1) }
    private val next = navButton(R.drawable.ic_arrow_right, R.string.agenda_next_month) { moveMonth(1) }
    private val today = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(R.string.agenda_today)
        setOnClickListener { selectDate(AgendaDate.fromMillis(System.currentTimeMillis())) }
    }
    private val grid = GridLayout(context).apply { columnCount = 7; rowCount = 7 }
    private val days = mutableListOf<Pair<MaterialButton, AgendaDate>>()
    private val header = LinearLayout(context)
    private var weekCount = 6
    private var viewportHeight = 0

    init {
        orientation = VERTICAL
        isSaveEnabled = true
        addView(header.apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(previous, LayoutParams(dp(48), dp(48)))
            addView(monthTitle, LayoutParams(0, -2, 1f))
            addView(next, LayoutParams(dp(48), dp(48)))
        }, LayoutParams(-1, -2))
        addView(grid, LayoutParams(-1, -2))
        addView(today, LayoutParams(-2, dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        render()
    }

    private fun navButton(icon: Int, description: Int, click: () -> Unit) = MaterialButton(context).apply {
        setIconResource(icon)
        contentDescription = context.getString(description)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconPadding = 0
        setPadding(dp(12), 0, dp(12), 0)
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setOnClickListener { click() }
    }

    private fun moveMonth(amount: Int) { selectDate(selectedDate.shiftMonth(amount)) }

    fun selectDate(date: AgendaDate) {
        selectedDate = date
        followToday = date == AgendaDate.fromMillis(System.currentTimeMillis())
        render()
        onDateSelected?.invoke(date)
    }

    /** Follow midnight only while the user is viewing today. */
    fun refreshToday() {
        val today = AgendaDate.fromMillis(System.currentTimeMillis())
        if (followToday && selectedDate != today) {
            selectedDate = today
            render()
        } else refreshMonetTheme()
    }

    private fun render() {
        val locale = resources.configuration.localesCompat()
        monthTitle.text = SimpleDateFormat("LLLL yyyy", locale).format(selectedDate.toMillis())
        grid.removeAllViews()
        days.clear()
        val firstDay = Calendar.getInstance(locale).firstDayOfWeek
        val weekdays = DateFormatSymbols(locale).shortWeekdays
        repeat(7) { column ->
            val day = (firstDay - 1 + column) % 7 + 1
            grid.addView(TextView(context).apply {
                text = weekdays[day]
                textSize = 12f
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, cellParams(column, 0, 28))
        }
        val formatter = DateFormat.getDateInstance(DateFormat.FULL, locale)
        val cells = selectedDate.monthCells(firstDay)
        weekCount = (cells.indexOfLast { it != null } / 7) + 1
        grid.rowCount = weekCount + 1
        cells.take(weekCount * 7).forEachIndexed { index, date ->
            val button = MaterialButton(context).apply {
                textSize = 14f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                insetTop = dp(2)
                insetBottom = dp(2)
                cornerRadius = dp(20)
                if (date == null) {
                    visibility = INVISIBLE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } else {
                    text = java.text.NumberFormat.getIntegerInstance(locale).format(date.day)
                    contentDescription = formatter.format(date.toMillis())
                    setOnClickListener { selectDate(date) }
                    days += this to date
                }
            }
            grid.addView(button, cellParams(index % 7, index / 7 + 1, 40))
        }
        refreshMonetTheme()
    }

    private fun cellParams(column: Int, row: Int, height: Int) = GridLayout.LayoutParams(
        GridLayout.spec(row), GridLayout.spec(column, 1f)).apply {
        width = 0
        this.height = dp(height)
    }

    /** The scroll container measures its child without a height limit; retain its actual viewport. */
    fun setViewportHeight(height: Int): Boolean {
        if (viewportHeight == height) return false
        viewportHeight = height
        requestLayout()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val naturalHeight = paddingTop + paddingBottom + dp(48 + 28 + weekCount * 40 + 48)
        val compact = viewportHeight > 0 && viewportHeight < naturalHeight
        val titleHeight = ceil(monthTitle.paint.fontMetrics.run { bottom - top }.toDouble()).toInt()
        val headerHeight = max(dp(if (compact) 40 else 48), titleHeight + dp(4))
        val footerHeight = max(dp(if (compact) 40 else 48),
            ceil(today.paint.fontMetrics.run { bottom - top }.toDouble()).toInt() + dp(12))
        val weekdayHeight = max(dp(if (compact) 24 else 28), (0 until minOf(7, grid.childCount)).maxOfOrNull {
            val label = grid.getChildAt(it) as TextView
            ceil(label.paint.fontMetrics.run { bottom - top }.toDouble()).toInt() + dp(4)
        } ?: 0)
        val minimumDayHeight = max(dp(24), days.maxOfOrNull { (button, _) ->
            ceil(button.paint.fontMetrics.run { bottom - top }.toDouble()).toInt() + dp(4)
        } ?: 0)
        val spaceForDays = viewportHeight - paddingTop - paddingBottom - headerHeight - footerHeight - weekdayHeight
        val dayHeight = if (viewportHeight > 0) (spaceForDays / weekCount)
            .coerceIn(minimumDayHeight, max(minimumDayHeight, dp(40))) else max(minimumDayHeight, dp(40))
        setHeight(header, headerHeight)
        setHeight(previous, headerHeight)
        setHeight(next, headerHeight)
        setHeight(today, footerHeight)
        for (index in 0 until grid.childCount) {
            setHeight(grid.getChildAt(index), if (index < 7) weekdayHeight else dayHeight)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun setHeight(view: View, height: Int) {
        val params = view.layoutParams
        if (params.height != height) {
            params.height = height
            view.layoutParams = params
        }
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        monthTitle.setTextColor(palette.onSurface)
        listOf(previous, next).forEach {
            it.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            it.iconTint = ColorStateList.valueOf(palette.onSurface)
        }
        val todayDate = AgendaDate.fromMillis(System.currentTimeMillis())
        days.forEach { (button, date) ->
            val selected = date == selectedDate
            button.isSelected = selected
            button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.primary else Color.TRANSPARENT)
            button.setTextColor(if (selected) palette.onPrimary else palette.onSurface)
            button.strokeWidth = if (date == todayDate && !selected) dp(1) else 0
            button.strokeColor = ColorStateList.valueOf(palette.primary)
        }
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("super", super.onSaveInstanceState())
        putInt("year", selectedDate.year)
        putInt("month", selectedDate.month)
        putInt("day", selectedDate.day)
        putBoolean("followToday", followToday)
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) { super.onRestoreInstanceState(state); return }
        super.onRestoreInstanceState(state.getParcelable("super"))
        selectedDate = AgendaDate(state.getInt("year"), state.getInt("month"), state.getInt("day"))
        followToday = state.getBoolean("followToday", true)
        render()
        onDateSelected?.invoke(selectedDate)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun android.content.res.Configuration.localesCompat(): Locale =
        if (android.os.Build.VERSION.SDK_INT >= 24) locales[0] else locale
}
