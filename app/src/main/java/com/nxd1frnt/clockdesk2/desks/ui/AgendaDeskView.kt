package com.nxd1frnt.clockdesk2.desks.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.agenda.AgendaDate
import com.nxd1frnt.clockdesk2.agenda.AgendaEvent
import com.nxd1frnt.clockdesk2.agenda.AgendaRepository
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.Executors

class AgendaDeskView(context: Context, private val requestCalendarPermission: () -> Unit) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val repository = AgendaRepository(context.applicationContext.contentResolver)
    private val calendar: AgendaCalendarView
    private val columns: LinearLayout
    private var landscape: Boolean? = null
    private var layoutHeight = 0
    private val date: TextView
    private val nextCard: View
    private val next: TextView
    private val state: View
    private val message: TextView
    private val action: MaterialButton
    private val events: RecyclerView
    private val adapter = EventsAdapter()
    private var active = false
    private var running = false
    private var observing = false
    private var generation = 0
    private var cancellation: CancellationSignal? = null
    private val reload = Runnable { loadEvents() }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            loadEvents()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reload)
            handler.postDelayed(reload, 250L)
        }
    }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { loadEvents() }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_agenda_layout, this, true)
        columns = findViewById(R.id.agenda_columns)
        calendar = findViewById(R.id.agenda_calendar)
        date = findViewById(R.id.agenda_date)
        nextCard = findViewById(R.id.agenda_next_card)
        next = findViewById(R.id.agenda_next)
        state = findViewById(R.id.agenda_state)
        message = findViewById(R.id.agenda_message)
        action = findViewById(R.id.agenda_action)
        events = findViewById(R.id.agenda_events)
        events.layoutManager = LinearLayoutManager(context)
        events.adapter = adapter
        events.itemAnimator = null
        calendar.onDateSelected = {
            adapter.items = emptyList()
            adapter.notifyDataSetChanged()
            nextCard.visibility = GONE
            updateDateLabel()
            loadEvents()
        }
        updateDateLabel()
    }

    fun setPageActive(value: Boolean) {
        active = value
        if (active && isAttachedToWindow) start() else stop()
    }

    fun refreshCalendarAccess() {
        stop()
        if (active && isAttachedToWindow) start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMonetTheme()
        if (active) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun start() {
        if (running) return
        ContextCompat.registerReceiver(context, clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        running = true
        if (hasPermission()) {
            try {
                context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                observing = true
            } catch (_: SecurityException) {
                // Permission may have been revoked between the check and registration.
            }
        }
        tick.run()
    }

    private fun stop() {
        generation++
        cancellation?.cancel()
        cancellation = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(reload)
        if (observing) context.contentResolver.unregisterContentObserver(observer)
        observing = false
        if (running) context.unregisterReceiver(clockReceiver)
        running = false
    }

    private fun loadEvents() {
        if (!running) return
        val now = System.currentTimeMillis()
        calendar.refreshToday()
        updateDateLabel()
        cancellation?.cancel()
        val token = ++generation
        if (!hasPermission()) {
            showState(R.string.agenda_permission_message, R.string.agenda_allow_calendar, requestCalendarPermission)
            return
        }
        if (adapter.items.isEmpty()) showState(R.string.agenda_loading)
        val signal = CancellationSignal().also { cancellation = it }
        val zone = TimeZone.getDefault()
        val selectedDay = calendar.selectedDate.toMillis(zone)
        executor.execute {
            val result = runCatching { repository.loadDay(selectedDay, now, zone, signal) }
            handler.post {
                if (!running || generation != token || signal.isCanceled) return@post
                result.fold(::showEvents) {
                    if (!hasPermission()) {
                        showState(R.string.agenda_permission_message, R.string.agenda_allow_calendar, requestCalendarPermission)
                    } else showState(R.string.agenda_error, R.string.agenda_retry) { loadEvents() }
                }
            }
        }
    }

    private fun showState(text: Int, button: Int? = null, onClick: (() -> Unit)? = null) {
        adapter.items = emptyList()
        adapter.notifyDataSetChanged()
        events.visibility = GONE
        nextCard.visibility = GONE
        state.visibility = VISIBLE
        message.setText(text)
        action.visibility = if (button == null) GONE else VISIBLE
        button?.let { action.setText(it) }
        action.setOnClickListener { onClick?.invoke() }
        refreshMonetTheme()
    }

    private fun showEvents(items: List<AgendaEvent>) {
        if (items.isEmpty()) {
            showState(if (calendar.selectedDate == AgendaDate.fromMillis(System.currentTimeMillis()))
                R.string.agenda_empty else R.string.agenda_day_empty)
            return
        }
        state.visibility = GONE
        events.visibility = VISIBLE
        val now = System.currentTimeMillis()
        val upcoming = items.firstOrNull { !it.allDay && it.begin > now }
        val ongoing = items.firstOrNull { !it.allDay && it.begin <= now && it.end > now }
        val featured = if (calendar.selectedDate == AgendaDate.fromMillis(now)) ongoing ?: upcoming else null
        nextCard.visibility = if (featured == null) GONE else VISIBLE
        if (featured != null) {
            val label = if (ongoing != null) context.getString(R.string.agenda_now)
            else {
                val minutes = ((featured.begin - now + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                context.resources.getQuantityString(R.plurals.agenda_in_minutes, minutes, minutes)
            }
            next.text = context.getString(R.string.agenda_next_event, label, title(featured))
        }
        adapter.items = items
        adapter.notifyDataSetChanged()
        refreshMonetTheme()
    }

    private fun updateDateLabel() {
        date.text = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL)
            .format(Date(calendar.selectedDate.toMillis()))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val horizontal = width >= height
        if (landscape != horizontal || layoutHeight != height) {
            layoutHeight = height
            landscape = horizontal
            columns.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val spacing = (16 * resources.displayMetrics.density).toInt()
            findViewById<View>(R.id.agenda_calendar_panel).layoutParams =
                if (horizontal) LinearLayout.LayoutParams(0, -1, 0.43f).apply { marginEnd = spacing }
                else LinearLayout.LayoutParams(-1, (height * 0.48f).toInt()).apply { bottomMargin = spacing }
            findViewById<View>(R.id.agenda_day_panel).layoutParams =
                if (horizontal) LinearLayout.LayoutParams(0, -1, 0.57f)
                else LinearLayout.LayoutParams(-1, 0, 1f)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val panel = findViewById<View>(R.id.agenda_calendar_panel)
        if (calendar.setViewportHeight(panel.measuredHeight - panel.paddingTop - panel.paddingBottom)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun title(event: AgendaEvent) = event.title.ifBlank { context.getString(R.string.agenda_untitled) }

    private fun openEvent(event: AgendaEvent) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.agenda_no_calendar_app, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.agenda_no_calendar_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        DeskForegroundStyle.apply(this)
        (nextCard as MaterialCardView).setCardBackgroundColor(palette.primaryContainer)
        next.setTextColor(palette.onPrimaryContainer)
        calendar.refreshMonetTheme(palette)
    }

    fun dispose() {
        stop()
        executor.shutdownNow()
    }

    private inner class EventsAdapter : RecyclerView.Adapter<EventHolder>() {
        var items: List<AgendaEvent> = emptyList()
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EventHolder(
            LayoutInflater.from(context).inflate(R.layout.item_agenda_event, parent, false))
        override fun onBindViewHolder(holder: EventHolder, position: Int) {
            val event = items[position]
            holder.title.text = title(event)
            val formatter = DateFormat.getTimeFormat(context)
            holder.time.text = if (event.allDay) context.getString(R.string.agenda_all_day)
                else "${formatter.format(Date(event.begin))}\n${formatter.format(Date(event.end))}"
            holder.details.text = listOf(event.location, event.calendarName).filter { it.isNotBlank() }.joinToString(" · ")
            holder.details.visibility = if (holder.details.text.isEmpty()) GONE else VISIBLE
            holder.itemView.setOnClickListener { openEvent(event) }
            val palette = UiMonetThemeManager.getPalette(context)
            UiMonetThemeManager.applyLiveToViewHierarchy(holder.itemView, palette)
            holder.time.setTextColor(palette.primary)
            holder.details.setTextColor(palette.onSurfaceVariant)
        }
    }

    private class EventHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.agenda_event_time)
        val title: TextView = view.findViewById(R.id.agenda_event_title)
        val details: TextView = view.findViewById(R.id.agenda_event_details)
    }
}
