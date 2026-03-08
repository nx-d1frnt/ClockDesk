package com.nxd1frnt.clockdesk2.shared.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.nxd1frnt.clockdesk2.R

class DraggablePluginAdapter(
    private val items: List<PluginUiModel>,
    private val onClick: (String) -> Unit,
    private val onSwitchChanged: (String, Boolean) -> Unit
    ): RecyclerView.Adapter<DraggablePluginAdapter.ViewHolder>() {

    class ViewHolder(view: View): RecyclerView.ViewHolder(view){
        val icon: ImageView = view.findViewById(R.id.plugin_icon)
        val title: TextView = view.findViewById(R.id.plugin_title)
        val desc: TextView = view.findViewById(R.id.plugin_desc)
        val handle: ImageView = view.findViewById(R.id.drag_handle)
        val settingsIcon: ImageView = view.findViewById(R.id.settings_indicator)
        val switch: MaterialSwitch = view.findViewById(R.id.plugin_switch)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plugin_source, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (item.iconDrawable != null) {
            holder.icon.setImageDrawable(item.iconDrawable)
            holder.icon.visibility = View.VISIBLE
        } else {
            holder.icon.visibility = View.GONE
        }

        if (item.isExternal) {
            holder.desc.text = "${item.desc} • External"
            holder.desc.alpha = 0.7f
        } else {
            holder.desc.text = item.desc
            holder.desc.alpha = 1.0f
        }
        holder.title.text = item.name
        holder.desc.text = item.desc

        holder.settingsIcon.visibility = if (item.hasSettings) View.VISIBLE else View.GONE
        val clickListener = View.OnClickListener {
            if (item.hasSettings) onClick(item.id)
        }
        holder.settingsIcon.setOnClickListener(clickListener)
        holder.itemView.setOnClickListener(clickListener)

        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = item.isEnabled
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            item.isEnabled = isChecked
            onSwitchChanged(item.id, isChecked)
        }
    }

    override fun getItemCount() = items.size
}