package com.nxd1frnt.clockdesk2.ui.settings

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
import com.google.android.material.shape.ShapeAppearanceModel
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
            val context = holder.itemView.context

            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.icon.setImageResource(item.iconResId)

            val isFirst = position == 0 || items[position - 1].groupId != item.groupId
            val isLast = position == items.lastIndex || items[position + 1].groupId != item.groupId

            val shapeRes = when {
                isFirst && isLast -> R.style.ShapeAppearance_ClockDesk_Single
                isFirst -> R.style.ShapeAppearance_ClockDesk_Top
                isLast -> R.style.ShapeAppearance_ClockDesk_Bottom
                else -> R.style.ShapeAppearance_ClockDesk_Middle
            }
            holder.cardContainer.shapeAppearanceModel = ShapeAppearanceModel.builder(context, shapeRes, 0).build()

            val marginBottomDp = if (isLast) 16 else 4
            (holder.cardContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = (marginBottomDp * context.resources.displayMetrics.density).toInt()
                holder.cardContainer.layoutParams = lp
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardContainer: MaterialCardView = view.findViewById(R.id.card_container)
            val iconBadge: MaterialCardView = view.findViewById(R.id.icon_badge)
            val icon: ImageView = view.findViewById(R.id.category_icon)
            val title: TextView = view.findViewById(R.id.category_title)
            val subtitle: TextView = view.findViewById(R.id.category_subtitle)
        }
    }
}
