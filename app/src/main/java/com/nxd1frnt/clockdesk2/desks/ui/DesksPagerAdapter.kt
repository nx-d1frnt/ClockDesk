package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.DeskItem
import com.nxd1frnt.clockdesk2.desks.DeskManager
import com.nxd1frnt.clockdesk2.desks.DeskType

class DesksPagerAdapter(
    private val activity: Activity,
    private val deskManager: DeskManager,
    private val primaryWidgetsView: View
) : RecyclerView.Adapter<DesksPagerAdapter.DeskPageViewHolder>() {

    private val desks = mutableListOf<DeskItem>()

    init {
        setHasStableIds(true)
        desks.addAll(deskManager.getDesks())
    }

    fun updateDesks(newDesks: List<DeskItem>) {
        desks.clear()
        desks.addAll(newDesks)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = desks.size

    override fun getItemId(position: Int): Long {
        return desks[position].id.hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return desks[position].type.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeskPageViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return DeskPageViewHolder(container)
    }

    override fun onBindViewHolder(holder: DeskPageViewHolder, position: Int) {
        val desk = desks[position]
        val context = holder.container.context

        holder.container.removeAllViews()

        when (desk.type) {
            DeskType.WIDGETS -> {
                if (desk.id == "desk_widgets_main" || position == 0) {
                    (primaryWidgetsView.parent as? ViewGroup)?.removeView(primaryWidgetsView)
                    holder.container.addView(primaryWidgetsView)
                } else {
                    val secondaryWidgets = LayoutInflater.from(context).inflate(R.layout.layout_desk_widgets, holder.container, false)
                    holder.container.addView(secondaryWidgets)
                }
            }
            DeskType.APPS -> {
                val appsView = AppsDeskView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                holder.container.addView(appsView)
            }
            DeskType.DEVICES -> {
                val devicesView = DevicesDeskView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                holder.container.addView(devicesView)
            }
            DeskType.WEATHER -> {
                val weatherView = WeatherDeskView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                holder.container.addView(weatherView)
            }
            DeskType.WEB_KIOSK -> {
                val kioskView = WebKioskDeskView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                kioskView.bindDesk(desk) { updatedDesk ->
                    deskManager.updateDesk(updatedDesk)
                }
                holder.container.addView(kioskView)
            }
        }
    }

    class DeskPageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
