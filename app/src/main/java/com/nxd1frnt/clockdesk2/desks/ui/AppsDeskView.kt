package com.nxd1frnt.clockdesk2.desks.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
        val componentName: ComponentName,
        val userHandle: UserHandle? = null
    )

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var loadJob: Job? = null
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()

    private val editSearch: EditText
    private val btnClearSearch: ImageView
    private val recyclerApps: RecyclerView
    private val txtNoApps: TextView
    private val adapter: AppsAdapter

    private var isReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadInstalledApps()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_apps_layout, this, true)

        editSearch = findViewById(R.id.edit_search_apps)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        recyclerApps = findViewById(R.id.recycler_apps)
        txtNoApps = findViewById(R.id.txt_no_apps)

        val spanCount = calculateSpanCount()
        recyclerApps.layoutManager = GridLayoutManager(context, spanCount)
        adapter = AppsAdapter(filteredApps) { app ->
            var launched = false
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null && app.userHandle != null) {
                try {
                    launcherApps.startMainActivity(app.componentName, app.userHandle, null, null)
                    launched = true
                } catch (e: Exception) {
                    // fallback
                }
            }
            if (!launched) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = app.componentName
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    context.startActivity(intent)
                    launched = true
                } catch (e: Exception) {
                    // fallback
                }
            }
            if (!launched) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
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
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        editSearch.setTextColor(palette.onSurface)
        editSearch.setHintTextColor(palette.onSurfaceVariant)
        btnClearSearch.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        txtNoApps.setTextColor(palette.onSurfaceVariant)
        adapter.notifyDataSetChanged()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMonetTheme()
        registerPackageReceiver()
        loadInstalledApps()
    }

    override fun onDetachedFromWindow() {
        unregisterPackageReceiver()
        loadJob?.cancel()
        loadJob = null
        super.onDetachedFromWindow()
    }

    fun dispose() {
        unregisterPackageReceiver()
        scope.cancel()
    }

    private fun registerPackageReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        try {
            ContextCompat.registerReceiver(
                context,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            // ignore if registration failed
        }
    }

    private fun unregisterPackageReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // ignore
        }
        isReceiverRegistered = false
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
        loadJob?.cancel()
        loadJob = scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val seenApps = mutableMapOf<String, AppItem>()
                val pm = context.packageManager

                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

                if (launcherApps != null && userManager != null) {
                    try {
                        val profiles = userManager.userProfiles
                        for (profile in profiles) {
                            val activities = launcherApps.getActivityList(null, profile)
                            for (activityInfo in activities) {
                                try {
                                    val pkg = activityInfo.applicationInfo.packageName
                                    if (pkg == context.packageName) continue
                                    val key = "$pkg/${profile.hashCode()}"
                                    val label = activityInfo.label.toString()
                                    val icon = activityInfo.getBadgedIcon(0)
                                    val component = activityInfo.componentName
                                    val newItem = AppItem(label, pkg, icon, component, profile)

                                    val existing = seenApps[key]
                                    if (existing == null) {
                                        seenApps[key] = newItem
                                    } else {
                                        val appLabel = try {
                                            activityInfo.applicationInfo.loadLabel(pm).toString()
                                        } catch (e: Exception) { label }
                                        // Prefer the item whose label matches the main app label
                                        if (label.equals(appLabel, ignoreCase = true) && !existing.label.equals(appLabel, ignoreCase = true)) {
                                            seenApps[key] = newItem
                                        }
                                    }
                                } catch (e: Exception) {
                                    // skip broken activity
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // fallback to PackageManager below
                    }
                }

                if (seenApps.isEmpty()) {
                    val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                    val resolveList = try {
                        pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                    } catch (e: Exception) {
                        try {
                            pm.queryIntentActivities(mainIntent, 0)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    val userHandle = Process.myUserHandle()
                    for (info in resolveList) {
                        try {
                            val pkg = info.activityInfo.packageName
                            if (pkg == context.packageName) continue
                            val key = "$pkg/${userHandle.hashCode()}"
                            val label = info.loadLabel(pm).toString()
                            val icon = info.loadIcon(pm) ?: pm.defaultActivityIcon
                            val component = ComponentName(pkg, info.activityInfo.name)
                            val newItem = AppItem(label, pkg, icon, component, userHandle)

                            val existing = seenApps[key]
                            if (existing == null) {
                                seenApps[key] = newItem
                            } else {
                                val appLabel = try {
                                    info.activityInfo.applicationInfo.loadLabel(pm).toString()
                                } catch (e: Exception) { label }
                                if (label.equals(appLabel, ignoreCase = true) && !existing.label.equals(appLabel, ignoreCase = true)) {
                                    seenApps[key] = newItem
                                }
                            }
                        } catch (e: Exception) {
                            // skip broken activity
                        }
                    }

                    // If still empty (e.g. Android TV), try LEANBACK_LAUNCHER
                    if (seenApps.isEmpty()) {
                        try {
                            val tvIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }
                            val tvList = pm.queryIntentActivities(tvIntent, 0)
                            for (info in tvList) {
                                try {
                                    val pkg = info.activityInfo.packageName
                                    if (pkg == context.packageName) continue
                                    val key = "$pkg/${userHandle.hashCode()}"
                                    val label = info.loadLabel(pm).toString()
                                    val icon = info.loadIcon(pm) ?: pm.defaultActivityIcon
                                    val component = ComponentName(pkg, info.activityInfo.name)
                                    val newItem = AppItem(label, pkg, icon, component, userHandle)
                                    if (!seenApps.containsKey(key)) {
                                        seenApps[key] = newItem
                                    }
                                } catch (e: Exception) {
                                    // skip broken item
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }

                val list = seenApps.values.toMutableList()
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
        txtNoApps.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private class AppsAdapter(
        private val items: List<AppItem>,
        private val onClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {
        init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: ImageView = view.findViewById(R.id.app_icon)
            val labelView: TextView = view.findViewById(R.id.app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val palette = UiMonetThemeManager.getPalette(holder.itemView.context)
            val app = items[position]
            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            holder.labelView.setTextColor(palette.onSurface)
            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount(): Int = items.size
    }
}
