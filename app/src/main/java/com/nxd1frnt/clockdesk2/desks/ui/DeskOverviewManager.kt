package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.DeskItem
import com.nxd1frnt.clockdesk2.desks.DeskManager
import com.nxd1frnt.clockdesk2.desks.DeskType
import java.util.Collections

class DeskOverviewManager(
    private val activity: Activity,
    private val overlayView: View,
    private val deskManager: DeskManager,
    private val onOpenDesk: (Int) -> Unit
) {

    private val recyclerDesks: RecyclerView = overlayView.findViewById(R.id.recycler_overview_desks)
    private val btnDone: MaterialButton = overlayView.findViewById(R.id.btn_overview_done)
    private val adapter: OverviewAdapter

    var isVisible: Boolean = false
        private set

    init {
        recyclerDesks.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        adapter = OverviewAdapter(
            context = activity,
            desks = deskManager.getDesks().toMutableList(),
            onDeskClick = { position ->
                hide()
                onOpenDesk(position)
            },
            onDeleteDesk = { desk ->
                showDeleteConfirm(desk)
            },
            onAddDesk = {
                showAddDeskDialog()
            }
        )
        recyclerDesks.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                val deskCount = adapter.getDesksCount()
                if (fromPos < deskCount && toPos < deskCount) {
                    adapter.moveItem(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                deskManager.reorderDesks(adapter.getCurrentDesks())
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
        itemTouchHelper.attachToRecyclerView(recyclerDesks)

        btnDone.setOnClickListener {
            hide()
        }
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        refreshList()
        overlayView.visibility = View.VISIBLE
        overlayView.alpha = 0f
        overlayView.scaleX = 0.95f
        overlayView.scaleY = 0.95f
        overlayView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)
            .start()
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        overlayView.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(200)
            .withEndAction {
                overlayView.visibility = View.GONE
            }
            .start()
    }

    fun refreshList() {
        adapter.updateList(deskManager.getDesks())
    }

    private fun showDeleteConfirm(desk: DeskItem) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_desk)
            .setMessage(activity.getString(R.string.delete_desk_confirm, desk.title))
            .setPositiveButton(R.string.delete_desk) { _, _ ->
                deskManager.removeDesk(desk.id)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDeskDialog() {
        val types = DeskType.entries.toTypedArray()
        val items = types.map { activity.getString(it.titleResId) }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_desk_type)
            .setItems(items) { _, which ->
                val selectedType = types[which]
                val newDesk = deskManager.addDesk(selectedType)
                refreshList()
                hide()
                onOpenDesk(newDesk.order)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class OverviewAdapter(
        private val context: Context,
        private val desks: MutableList<DeskItem>,
        private val onDeskClick: (Int) -> Unit,
        private val onDeleteDesk: (DeskItem) -> Unit,
        private val onAddDesk: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_DESK = 0
            private const val TYPE_ADD = 1
        }

        fun getDesksCount(): Int = desks.size

        fun getCurrentDesks(): List<DeskItem> = desks.toList()

        fun updateList(newList: List<DeskItem>) {
            desks.clear()
            desks.addAll(newList)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            Collections.swap(desks, from, to)
            notifyItemMoved(from, to)
        }

        override fun getItemViewType(position: Int): Int {
            return if (position < desks.size) TYPE_DESK else TYPE_ADD
        }

        override fun getItemCount(): Int = desks.size + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_DESK) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_overview_card, parent, false)
                DeskViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_overview_add, parent, false)
                AddViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is DeskViewHolder) {
                val desk = desks[position]
                holder.txtTitle.text = desk.title
                holder.txtType.text = context.getString(desk.type.titleResId)
                holder.imgIcon.setImageResource(desk.type.iconResId)

                holder.btnDelete.visibility = if (desks.size > 1) View.VISIBLE else View.GONE
                holder.btnDelete.setOnClickListener {
                    onDeleteDesk(desk)
                }

                holder.card.setOnClickListener {
                    onDeskClick(position)
                }
            } else if (holder is AddViewHolder) {
                holder.card.setOnClickListener {
                    onAddDesk()
                }
            }
        }

        class DeskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_overview_desk)
            val imgIcon: ImageView = view.findViewById(R.id.img_overview_icon)
            val txtTitle: TextView = view.findViewById(R.id.txt_overview_title)
            val txtType: TextView = view.findViewById(R.id.txt_overview_type)
            val btnDelete: ImageView = view.findViewById(R.id.btn_overview_delete)
        }

        class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_overview_add_desk)
        }
    }
}
