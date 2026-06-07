package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ui.LastFmSettingsFragment
import com.nxd1frnt.clockdesk2.music.ui.MusicSourcesFragment
import com.nxd1frnt.clockdesk2.smartchips.ui.SmartChipsPluginsFragment

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var collapsingToolbar: CollapsingToolbarLayout

    private var isTablet: Boolean = false
    private var categoryRecyclerView: RecyclerView? = null
    private var tabletAdapter: TabletCategoryAdapter? = null
    private var selectedCategoryId: String = "general"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dynamic colors for Material You
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_settings)
        toolbar = findViewById(R.id.toolbar)
        collapsingToolbar = findViewById(R.id.collapsingToolbar)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keep landscape for desk clock consistency
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        isTablet = resources.getBoolean(R.bool.is_tablet)

        setupToolbar()

        if (isTablet) {
            categoryRecyclerView = findViewById(R.id.category_recycler_view)
            setupTabletNavigation(savedInstanceState)
        } else {
            // Phone single-pane default layout is inflated via XML (SettingsCategoryListFragment)
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsCategoryListFragment())
                    .commit()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarTitle()
        }

        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }

        updateToolbarTitle()
    }

    private fun updateToolbarTitle() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.settings_container)
        val newTitle = when (currentFragment) {
            is GeneralSettingsFragment -> getString(R.string.sun_sunrise_api_settings)
            is MusicSettingsFragment -> getString(R.string.music_settings_title)
            is DisplaySettingsFragment -> getString(R.string.display_settings_title)
            is BatterySettingsFragment -> getString(R.string.battery_saver_settings_title)
            is PerformanceSettingsFragment -> getString(R.string.performance_settings)
            is BackupSettingsFragment -> getString(R.string.backup_restore_title)
            is SmartChipsPluginsFragment -> getString(R.string.smart_chips_plugins_title)
            is MusicSourcesFragment -> getString(R.string.music_sources_title)
            is LastFmSettingsFragment -> getString(R.string.lastfm_plugin_name)
            is AboutFragment -> getString(R.string.about_title)
            else -> getString(R.string.settings_title)
        }
        collapsingToolbar.title = newTitle
    }

    private fun setupTabletNavigation(savedInstanceState: Bundle?) {
        val categories = SettingsCategoryProvider.getCategories(this)
        
        if (savedInstanceState != null) {
            selectedCategoryId = savedInstanceState.getString("selected_category", "general")
        }

        tabletAdapter = TabletCategoryAdapter(categories, selectedCategoryId) { category ->
            selectCategory(category)
        }

        categoryRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = tabletAdapter
        }

        if (savedInstanceState == null) {
            val defaultCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()
            selectCategory(defaultCategory)
        }
    }

    private fun selectCategory(category: SettingsCategory) {
        selectedCategoryId = category.id
        tabletAdapter?.setSelectedId(selectedCategoryId)

        // Clear any back stack inside the detail card to start fresh in the new category
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        val fragment = category.fragmentClass.getDeclaredConstructor().newInstance()
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.settings_container, fragment)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_category", selectedCategoryId)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
        }
        
        // Replace current fragment and add to backstack
        supportFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }

    private class TabletCategoryAdapter(
        private val items: List<SettingsCategory>,
        private var selectedId: String,
        private val onSelected: (SettingsCategory) -> Unit
    ) : RecyclerView.Adapter<TabletCategoryAdapter.ViewHolder>() {

        fun setSelectedId(newId: String) {
            if (selectedId != newId) {
                val oldIndex = items.indexOfFirst { it.id == selectedId }
                val newIndex = items.indexOfFirst { it.id == newId }
                selectedId = newId
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_settings_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.icon.setImageResource(item.iconResId)

            val isSelected = item.id == selectedId
            val ctx = holder.itemView.context

            if (isSelected) {
                holder.cardContainer.setCardBackgroundColor(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                )
                holder.title.setTextColor(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                holder.subtitle.setTextColor(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            } else {
                holder.cardContainer.setCardBackgroundColor(
                    ctx.getColor(android.R.color.transparent)
                )
                holder.title.setTextColor(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorOnSurface)
                )
                holder.subtitle.setTextColor(
                    ctx.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getThemeColor(android.R.attr.colorPrimary)
                )
            }

            holder.itemView.setOnClickListener { onSelected(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardContainer: MaterialCardView = view.findViewById(R.id.card_container)
            val icon: ImageView = view.findViewById(R.id.category_icon)
            val title: TextView = view.findViewById(R.id.category_title)
            val subtitle: TextView = view.findViewById(R.id.category_subtitle)
        }
    }
}

// Extension function to load colors from active theme attributes
private fun Context.getThemeColor(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}
