package com.nxd1frnt.clockdesk2.background.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager
import com.nxd1frnt.clockdesk2.background.provider.ExternalBackgroundPlugin
import com.nxd1frnt.clockdesk2.background.provider.IBackgroundProvider
import com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider
import com.nxd1frnt.clockdesk2.ui.dialog.SolidColorPickerDialog
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class BackgroundPluginsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BackgroundProvidersAdapter
    private lateinit var providerManager: BackgroundProviderManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
            setPadding(0, 16, 0, 16)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        providerManager = BackgroundProviderManager.getInstance(requireContext())
        setupAdapter()
    }

    private fun setupAdapter() {
        val providers = providerManager.getAllProviders()
        adapter = BackgroundProvidersAdapter(
            context = requireContext(),
            providers = providers,
            activeId = providerManager.getActiveProviderId(),
            onSelect = { provider ->
                providerManager.setActiveProviderId(provider.id)
                adapter.setActiveId(provider.id)
                Toast.makeText(
                    requireContext(),
                    "${provider.displayName} selected",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onConfigure = { provider ->
                if (provider.id == SolidColorBackgroundProvider.ID) {
                    val currentColor = providerManager.solidColorProvider.getColor(requireContext())
                    SolidColorPickerDialog.show(requireContext(), currentColor) { newColor ->
                        providerManager.solidColorProvider.setColor(requireContext(), newColor)
                        adapter.notifyDataSetChanged()
                    }
                } else if (provider is ExternalBackgroundPlugin && !provider.settingsActivity.isNullOrEmpty()) {
                    try {
                        val intent = Intent().setClassName(provider.packageName, provider.settingsActivity)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.error_opening_settings, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        recyclerView.adapter = adapter
    }

    private class BackgroundProvidersAdapter(
        private val context: Context,
        private val providers: List<IBackgroundProvider>,
        private var activeId: String,
        private val onSelect: (IBackgroundProvider) -> Unit,
        private val onConfigure: (IBackgroundProvider) -> Unit
    ) : RecyclerView.Adapter<BackgroundProvidersAdapter.ViewHolder>() {

        fun setActiveId(id: String) {
            activeId = id
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val icon: ImageView = view.findViewById(R.id.plugin_icon)
            val title: TextView = view.findViewById(R.id.plugin_title)
            val desc: TextView = view.findViewById(R.id.plugin_desc)
            val settingsIcon: ImageView = view.findViewById(R.id.settings_indicator)
            val activeBadge: View = view.findViewById(R.id.drag_handle)
            val toggleSwitch: com.google.android.material.materialswitch.MaterialSwitch = view.findViewById(R.id.plugin_switch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plugin_source, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val provider = providers[position]
            val isActive = provider.id == activeId
            val palette = UiMonetThemeManager.getPalette(holder.itemView.context)

            holder.title.text = provider.displayName
            holder.desc.text = provider.description

            if (provider.id == SolidColorBackgroundProvider.ID) {
                val color = SolidColorBackgroundProvider().getColor(context)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(color)
                }
                holder.icon.background = bg
                holder.icon.setImageResource(R.drawable.ic_palette_swatch)
                val isLight = androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    if (isLight) Color.BLACK else Color.WHITE
                )
            } else {
                holder.icon.background = null
                holder.icon.setImageResource(provider.iconResId)
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            }

            val hasConfig = provider.capabilities.supportsConfig ||
                    (provider is ExternalBackgroundPlugin && !provider.settingsActivity.isNullOrEmpty())

            holder.settingsIcon.visibility = if (hasConfig) View.VISIBLE else View.GONE
            holder.settingsIcon.setOnClickListener {
                onConfigure(provider)
            }

            holder.toggleSwitch.visibility = View.VISIBLE
            holder.toggleSwitch.setOnCheckedChangeListener(null)
            holder.toggleSwitch.isChecked = isActive
            holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    onSelect(provider)
                }
            }

            holder.card.strokeWidth = if (isActive) (2 * context.resources.displayMetrics.density).toInt() else 0
            holder.card.strokeColor = palette.primary

            holder.itemView.setOnClickListener {
                if (hasConfig && isActive) {
                    onConfigure(provider)
                } else {
                    onSelect(provider)
                }
            }

            UiMonetThemeManager.applyLiveToViewHierarchy(holder.itemView)
        }

        override fun getItemCount(): Int = providers.size
    }
}
