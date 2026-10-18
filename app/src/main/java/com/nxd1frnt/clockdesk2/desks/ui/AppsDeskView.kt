package com.nxd1frnt.clockdesk2.desks.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        val componentName: ComponentName
    )

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()

    private val editSearch: EditText
    private val btnClearSearch: ImageView
    private val recyclerApps: RecyclerView
    private val txtNoApps: TextView
    private val adapter: AppsAdapter

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_apps_layout, this, true)

        editSearch = findViewById(R.id.edit_search_apps)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        recyclerApps = findViewById(R.id.recycler_apps)
        txtNoApps = findViewById(R.id.txt_no_apps)

        val spanCount = calculateSpanCount()
        recyclerApps.layoutManager = GridLayoutManager(context, spanCount)
        adapter = AppsAdapter(filteredApps) { app ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
        recyclerApps.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterApps(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            editSearch.setText("")
        }

        loadInstalledApps()
    }

    private fun calculateSpanCount(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) 7 else 4
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        recyclerApps.layoutManager = GridLayoutManager(context, calculateSpanCount())
    }

    private fun loadInstalledApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveList = pm.queryIntentActivities(mainIntent, 0)
                val list = mutableListOf<AppItem>()
                for (info in resolveList) {
                    if (info.activityInfo.packageName == context.packageName) continue // don't show self
                    val label = info.loadLabel(pm).toString()
                    val icon = info.loadIcon(pm)
                    val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    list.add(AppItem(label, info.activityInfo.packageName, icon, component))
                }
                list.sortBy { it.label.lowercase() }
                list
            }
            allApps.clear()
            allApps.addAll(apps)
            filterApps(editSearch.text.toString().trim())
        }
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            val lower = query.lowercase()
            for (app in allApps) {
                if (app.label.lowercase().contains(lower) || app.packageName.lowercase().contains(lower)) {
                    filteredApps.add(app)
                }
            }
        }
        adapter.notifyDataSetChanged()
        txtNoApps.visibility = if (filteredApps.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private class AppsAdapter(
        private val items: List<AppItem>,
        private val onClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: ImageView = view.findViewById(R.id.app_icon)
            val labelView: TextView = view.findViewById(R.id.app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = items[position]
            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount(): Int = items.size
    }
}
