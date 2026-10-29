package com.nxd1frnt.clockdesk2.agenda

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Parcelable
import android.util.SparseArray
import android.os.Looper
import android.provider.CalendarContract
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.ui.AgendaCalendarView
import com.nxd1frnt.clockdesk2.desks.ui.AgendaDeskView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AgendaCalendarIntegrationTest {
    private val provider = CalendarProvider()
    private lateinit var activity: Activity
    private var view: AgendaDeskView? = null

    @Before fun setup() {
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val controller = Robolectric.buildActivity(Activity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup().visible()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CALENDAR)
    }

    @After fun cleanup() { view?.dispose(); activity.finish() }

    @Test fun `permission is requested only after explicit button tap`() {
        var requests = 0
        val agenda = AgendaDeskView(activity) { requests++ }.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        assertEquals(0, requests)
        assertEquals(0, provider.queries)
        assertEquals(View.VISIBLE, agenda.findViewById<View>(R.id.agenda_state).visibility)
        assertEquals(activity.getString(R.string.agenda_permission_message),
            agenda.findViewById<TextView>(R.id.agenda_message).text.toString())
        agenda.findViewById<MaterialButton>(R.id.agenda_action).performClick()
        assertEquals(1, requests)
    }

    @Test fun `expanded instances retain separate occurrences and filter ended events`() {
        val now = System.currentTimeMillis()
        provider.rows = listOf(
            arrayOf(7L, "First occurrence", now + 60_000, now + 120_000, 0, "Room A", "Work"),
            arrayOf(7L, "Second occurrence", now + 180_000, now + 240_000, 0, "Room B", "Work"),
            arrayOf(8L, "Finished", now - 120_000, now - 60_000, 0, "", "Work")
        )
        val events = AgendaRepository(activity.contentResolver).loadToday(now, TimeZone.getTimeZone("UTC"), CancellationSignal())
        // Keep this test independent of the time of day at which it runs.
        val expected = provider.rows.take(2).count {
            (it[2] as Long) < AgendaRepository.dayBounds(now, TimeZone.getTimeZone("UTC")).second
        }
        assertEquals(expected, events.size)
        assertTrue(provider.lastUri.toString().contains("/instances/when/"))
        assertTrue(provider.lastSelection.orEmpty().contains(CalendarContract.Instances.VISIBLE))
        assertArrayEquals(arrayOf(CalendarContract.Events.STATUS_CANCELED.toString(),
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()), provider.lastArgs)
    }

    @Test fun `grant loads empty state and inactive page stops observing`() {
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR)
        agenda.refreshCalendarAccess()
        await { agenda.findViewById<TextView>(R.id.agenda_message).text.toString() == activity.getString(R.string.agenda_empty) }
        assertEquals(0, agenda.findViewById<RecyclerView>(R.id.agenda_events).adapter!!.itemCount)
        agenda.setPageActive(false)
        val queryCount = provider.queries
        activity.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(2))
        assertEquals(queryCount, provider.queries)
    }

    @Test fun `provider failure offers retry and recovers`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR)
        provider.fail = true
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        await { agenda.findViewById<TextView>(R.id.agenda_message).text.toString() == activity.getString(R.string.agenda_error) }
        provider.fail = false
        agenda.findViewById<MaterialButton>(R.id.agenda_action).performClick()
        await { agenda.findViewById<TextView>(R.id.agenda_message).text.toString() == activity.getString(R.string.agenda_empty) }
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `events render and revoked access clears the list`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR)
        val now = System.currentTimeMillis()
        provider.rows = listOf(arrayOf(9L, "Design review", now - 1000L, now + 3_600_000L, 0, "Room A", "Work"))
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        val list = agenda.findViewById<RecyclerView>(R.id.agenda_events)
        await { list.adapter!!.itemCount == 1 }
        agenda.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        agenda.layout(0, 0, 960, 540)
        val row = list.findViewHolderForAdapterPosition(0)!!.itemView
        assertEquals("Design review", row.findViewById<TextView>(R.id.agenda_event_title).text.toString())
        assertEquals("Room A · Work", row.findViewById<TextView>(R.id.agenda_event_details).text.toString())
        assertEquals(activity.getString(R.string.agenda_next_event, activity.getString(R.string.agenda_now), "Design review"),
            agenda.findViewById<TextView>(R.id.agenda_next).text.toString())
        System.getenv("CLOCKDESK_CALENDAR_PREVIEW")?.let { path ->
            val bitmap = android.graphics.Bitmap.createBitmap(960, 540, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(27, 29, 33))
            agenda.draw(canvas)
            java.io.FileOutputStream(path).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        row.performClick()
        assertEquals("content://com.android.calendar/events/9", shadowOf(activity).nextStartedActivity.data.toString())
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CALENDAR)
        agenda.refreshCalendarAccess()
        assertEquals(0, list.adapter!!.itemCount)
        assertEquals(View.GONE, list.visibility)
        assertEquals(View.VISIBLE, agenda.findViewById<View>(R.id.agenda_action).visibility)
    }

    @Test fun `month navigation works without permission and today restores the current day`() {
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        val calendar = agenda.findViewById<AgendaCalendarView>(R.id.agenda_calendar)
        val today = calendar.selectedDate
        fun buttons(parent: android.view.ViewGroup): List<MaterialButton> =
            (0 until parent.childCount).flatMap { index ->
                when (val child = parent.getChildAt(index)) {
                    is MaterialButton -> listOf(child)
                    is android.view.ViewGroup -> buttons(child)
                    else -> emptyList()
                }
            }
        buttons(calendar).first { it.contentDescription == activity.getString(R.string.agenda_next_month) }.performClick()
        assertEquals(today.shiftMonth(1), calendar.selectedDate)
        assertEquals(0, provider.queries)
        buttons(calendar).first { it.text.toString() == activity.getString(R.string.agenda_today) }.performClick()
        assertEquals(today, calendar.selectedDate)
    }

    @Test fun `date selection changes queried day and hides todays countdown`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR)
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        await { provider.queries > 0 }
        val calendar = agenda.findViewById<AgendaCalendarView>(R.id.agenda_calendar)
        val selected = AgendaDate(2020, java.util.Calendar.FEBRUARY, 29)
        val begin = selected.toMillis()
        provider.rows = listOf(arrayOf(10L, "Historic meeting", begin, begin + 60_000, 0, "", "Work"))
        calendar.selectDate(selected)
        val list = agenda.findViewById<RecyclerView>(R.id.agenda_events)
        await { list.adapter!!.itemCount == 1 }
        assertEquals(View.GONE, agenda.findViewById<View>(R.id.agenda_next_card).visibility)
        val dayStart = AgendaRepository.dayBounds(begin, TimeZone.getDefault()).first
        assertTrue(provider.lastUri.toString().contains("/instances/when/"))
        assertTrue(provider.lastUri!!.pathSegments.takeLast(2).map { it.toLong() }.let { dayStart in it[0]..it[1] })
        assertEquals(java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(java.util.Date(begin)),
            agenda.findViewById<TextView>(R.id.agenda_date).text.toString())
    }

    @Test fun `selected day survives hierarchy restoration`() {
        val first = AgendaCalendarView(activity).apply { id = R.id.agenda_calendar }
        first.selectDate(AgendaDate(2024, java.util.Calendar.FEBRUARY, 29))
        val saved = SparseArray<Parcelable>()
        first.saveHierarchyState(saved)
        val restored = AgendaCalendarView(activity).apply { id = R.id.agenda_calendar }
        var selected: AgendaDate? = null
        restored.onDateSelected = { selected = it }
        restored.restoreHierarchyState(saved)
        assertEquals(first.selectedDate, restored.selectedDate)
        assertEquals(first.selectedDate, selected)
    }

    @Test fun `calendar and event panel adapt to landscape and portrait`() {
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        fun measure(width: Int, height: Int) {
            agenda.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            agenda.layout(0, 0, width, height)
        }
        measure(960, 540)
        assertEquals(android.widget.LinearLayout.HORIZONTAL,
            agenda.findViewById<android.widget.LinearLayout>(R.id.agenda_columns).orientation)
        assertTrue(agenda.findViewById<View>(R.id.agenda_day_panel).measuredWidth > 0)
        measure(400, 900)
        assertEquals(android.widget.LinearLayout.VERTICAL,
            agenda.findViewById<android.widget.LinearLayout>(R.id.agenda_columns).orientation)
        assertTrue(agenda.findViewById<View>(R.id.agenda_day_panel).measuredHeight > 0)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "hdpi")
    @Test fun `calendar card fits short viewports for four five and six week months`() {
        val agenda = AgendaDeskView(activity) {}.also { view = it }
        activity.setContentView(agenda)
        agenda.setPageActive(true)
        val calendar = agenda.findViewById<AgendaCalendarView>(R.id.agenda_calendar)
        for (heightDp in listOf(360, 320)) {
            for (month in listOf(java.util.Calendar.FEBRUARY, java.util.Calendar.OCTOBER, java.util.Calendar.AUGUST)) {
                calendar.selectDate(AgendaDate(2026, month, 1))
                val density = activity.resources.displayMetrics.density
                val width = (640 * density).toInt()
                val height = (heightDp * density).toInt()
                agenda.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                agenda.layout(0, 0, width, height)
                val card = agenda.findViewById<View>(R.id.agenda_calendar_card)
                val panel = agenda.findViewById<android.widget.ScrollView>(R.id.agenda_calendar_panel)
                assertTrue("Calendar in month $month at height $heightDp is taller than its viewport: ${card.height} > ${panel.height}",
                    card.height <= panel.height)
                val today = calendar.getChildAt(calendar.childCount - 1)
                assertTrue(today.bottom + calendar.paddingBottom <= calendar.height)
                assertEquals(0, panel.scrollY)
                if (heightDp == 320 && month == java.util.Calendar.AUGUST) {
                    System.getenv("CLOCKDESK_COMPACT_CALENDAR_PREVIEW")?.let { path ->
                        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.rgb(27, 29, 33))
                        agenda.draw(canvas)
                        java.io.FileOutputStream(path).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("Agenda did not reach the expected state", condition())
    }

    private class CalendarProvider : ContentProvider() {
        var rows = emptyList<Array<Any>>()
        @Volatile var queries = 0
        @Volatile var fail = false
        var lastUri: Uri? = null
        var lastSelection: String? = null
        var lastArgs: Array<out String>? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries++
            lastUri = uri
            lastSelection = selection
            lastArgs = selectionArgs
            if (fail) error("Provider unavailable")
            return MatrixCursor(projection!!).apply { rows.forEach { addRow(it) } }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
