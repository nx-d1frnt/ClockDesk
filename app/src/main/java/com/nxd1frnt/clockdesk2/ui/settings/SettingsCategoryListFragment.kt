package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R

class SettingsCategoryListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings_categories, container, false)
        recyclerView = view.findViewById(R.id.categories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val categories = SettingsCategoryProvider.getCategories(requireContext())
        adapter = CategoryAdapter(categories) { category ->
            val fragment = category.fragmentClass.getDeclaredConstructor().newInstance()
            parentFragmentManager.beginTransaction()
                .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = adapter
    }

    private class CategoryAdapter(
        private val items: List<SettingsCategory>,
        private val onClick: (SettingsCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

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
            
            // Set unselected styling for phone list items since there is no dual-pane highlighting
            holder.cardContainer.setCardBackgroundColor(
                holder.itemView.context.getColor(android.R.color.transparent)
            )
            val context = holder.itemView.context
            val colorOnSurface = context.getThemeColor(com.google.android.material.R.attr.colorOnSurface)
            val colorOnSurfaceVariant = context.getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val colorPrimary = context.getThemeColor(android.R.attr.colorPrimary)

            holder.title.setTextColor(colorOnSurface)
            holder.subtitle.setTextColor(colorOnSurfaceVariant)
            holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(colorPrimary)

            holder.itemView.setOnClickListener { onClick(item) }
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

private fun Context.getThemeColor(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}
