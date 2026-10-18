package com.nxd1frnt.clockdesk2.desks.ui

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.desks.DeskItem
import com.nxd1frnt.clockdesk2.desks.DeskManager
import com.nxd1frnt.clockdesk2.desks.DeskType

class DesksPagerAdapter(
    private val deskManager: DeskManager,
    private val widgetsView: View,
    private val onWidgetDeskSelected: (DeskItem) -> Unit
) : RecyclerView.Adapter<DesksPagerAdapter.DeskPageViewHolder>() {
    private val desks = deskManager.getDesks().toMutableList()
    private val boundPages = mutableMapOf<String, DeskPageViewHolder>()
    private val pageViews = linkedMapOf<String, View>()
    private val pageStates = mutableMapOf<String, Bundle>()
    val previews = DeskPreviewStore(widgetsView.context)
    private var activeWidgetDeskId: String? = null
    private var selectedDeskId: String? = null
    private var hostResumed = true

    init {
        setHasStableIds(true)
        previews.preload(desks)
    }

    fun selectDesk(position: Int) {
        val desk = desks.getOrNull(position) ?: return
        selectedDeskId = desk.id
        updatePageActivity()
        if (desk.type != DeskType.WIDGETS) return
        if (activeWidgetDeskId != desk.id) {
            activeWidgetDeskId?.let { previousId ->
                if (desks.any { it.id == previousId }) {
                    previews.capture(previousId, widgetsView)
                    boundPages[previousId]?.let { showPreview(it, desks.first { d -> d.id == previousId }) }
                }
            }
            activeWidgetDeskId = desk.id
            boundPages[desk.id]?.let { attach(it, widgetsView) }
            onWidgetDeskSelected(desk)
        } else {
            boundPages[desk.id]?.let { attach(it, widgetsView) }
        }
    }

    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        updatePageActivity()
    }

    private fun updatePageActivity() {
        pageViews.forEach { (id, view) ->
            (view as? WebKioskDeskView)?.setPageActive(hostResumed && id == selectedDeskId)
        }
    }

    fun capturePreviews() {
        activeWidgetDeskId?.let { previews.capture(it, widgetsView) }
        pageViews.forEach { (id, view) -> if (view.parent != null) previews.capture(id, view) }
    }

    private fun showPreview(holder: DeskPageViewHolder, desk: DeskItem) {
        holder.container.removeAllViews()
        holder.container.addView(ImageView(holder.container.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(previews.get(desk))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun attach(holder: DeskPageViewHolder, view: View) {
        if (view.parent === holder.container) return
        (view.parent as? ViewGroup)?.removeView(view)
        holder.container.removeAllViews()
        holder.container.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun updateDesks(newDesks: List<DeskItem>) {
        val ids = newDesks.map { it.id }.toSet()
        if (activeWidgetDeskId != null && activeWidgetDeskId !in ids) {
            (widgetsView.parent as? ViewGroup)?.removeView(widgetsView)
            activeWidgetDeskId = null
        }
        desks.filter { it.id !in ids }.forEach { previews.invalidate(it.id) }
        pageViews.keys.filter { it !in ids }.forEach { id ->
            val view = pageViews.remove(id)!!
            (view.parent as? ViewGroup)?.removeView(view)
            dispose(view)
        }
        pageStates.keys.retainAll(ids)
        boundPages.keys.retainAll(ids)
        desks.clear()
        desks.addAll(newDesks)
        previews.preload(newDesks)
        notifyDataSetChanged()
    }

    override fun getItemCount() = desks.size
    override fun getItemId(position: Int) = desks[position].id.hashCode().toLong()
    override fun getItemViewType(position: Int) = desks[position].type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeskPageViewHolder =
        DeskPageViewHolder(FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            // Page IDs are repeated across desks. Save each hierarchy under its desk ID.
            isSaveFromParentEnabled = false
        })

    override fun onBindViewHolder(holder: DeskPageViewHolder, position: Int) {
        val desk = desks[position]
        holder.deskId?.let { if (boundPages[it] === holder) boundPages.remove(it) }
        holder.deskId = desk.id
        boundPages[desk.id] = holder
        holder.container.removeAllViews()
        if (desk.type == DeskType.WIDGETS) {
            if (desk.id == activeWidgetDeskId) attach(holder, widgetsView) else showPreview(holder, desk)
        } else {
            val view = pageViews.getOrPut(desk.id) {
                createPage(desk).also { restorePage(desk.id, it) }
            }
            if (view is WebKioskDeskView) {
                view.bindDesk(desk) { deskManager.updateDesk(it) }
                view.setPageActive(hostResumed && desk.id == selectedDeskId)
            }
            attach(holder, view)
        }
        trimDetachedPages()
    }

    private fun createPage(desk: DeskItem): View {
        val context = widgetsView.context
        return when (desk.type) {
            DeskType.APPS -> AppsDeskView(context)
            DeskType.DEVICES -> DevicesDeskView(context)
            DeskType.WEATHER -> WeatherDeskView(context)
            DeskType.WEB_KIOSK -> WebKioskDeskView(context)
            DeskType.WIDGETS -> error("Widgets use the activity host")
        }
    }

    private fun savePage(id: String, view: View) {
        val hierarchy = SparseArray<Parcelable>()
        view.saveHierarchyState(hierarchy)
        pageStates[id] = Bundle().apply {
            putSparseParcelableArray("hierarchy", hierarchy)
            if (view is WebKioskDeskView) putBundle("web", view.savePageState())
        }
    }

    @Suppress("DEPRECATION")
    private fun restorePage(id: String, view: View) {
        val state = pageStates[id] ?: return
        state.classLoader = widgetsView.context.classLoader
        state.getSparseParcelableArray<Parcelable>("hierarchy")?.let { view.restoreHierarchyState(it) }
        if (view is WebKioskDeskView) state.getBundle("web")?.let { view.restorePageState(it) }
    }

    fun saveState(): Bundle {
        pageViews.forEach { (id, view) -> savePage(id, view) }
        return Bundle().apply { pageStates.forEach { (id, state) -> putBundle(id, state) } }
    }

    fun restoreState(state: Bundle?) {
        state ?: return
        state.classLoader = widgetsView.context.classLoader
        state.keySet().forEach { id -> state.getBundle(id)?.let { pageStates[id] = it } }
    }

    private fun trimDetachedPages() {
        val detached = pageViews.filterValues { it.parent == null }.keys.toList()
        detached.dropLast(3).forEach { id ->
            val view = pageViews.remove(id)!!
            savePage(id, view)
            dispose(view)
        }
    }

    private fun dispose(view: View) {
        (view as? WebKioskDeskView)?.dispose()
        (view as? AppsDeskView)?.dispose()
    }

    fun destroy() {
        pageViews.values.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            dispose(view)
        }
        pageViews.clear()
        boundPages.clear()
        pageStates.clear()
    }

    override fun onViewRecycled(holder: DeskPageViewHolder) {
        holder.deskId?.let { if (boundPages[it] === holder) boundPages.remove(it) }
        holder.deskId = null
        holder.container.removeAllViews()
        trimDetachedPages()
        super.onViewRecycled(holder)
    }

    class DeskPageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container) {
        var deskId: String? = null
    }
}
