package com.nxd1frnt.clockdesk2.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.color.DynamicColors
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.GeocodingHelper
import com.nxd1frnt.clockdesk2.utils.GeocodingResult
import java.util.Locale

object CitySearchDialog {

    fun show(context: Context, onCitySelected: (GeocodingResult) -> Unit) {
        var activityContext: Context? = context
        while (activityContext is ContextWrapper && activityContext !is Activity) {
            activityContext = activityContext.baseContext
        }
        val targetContext = activityContext ?: context
        val dialogContext = DynamicColors.wrapContextIfAvailable(targetContext)

        val view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_city_search, null, false)

        val editText = view.findViewById<TextInputEditText>(R.id.city_search_edit_text)
        val progressBar = view.findViewById<ProgressBar>(R.id.city_search_progress)
        val statusText = view.findViewById<TextView>(R.id.city_search_status_text)
        val recyclerView = view.findViewById<RecyclerView>(R.id.city_search_results_recycler)

        val adapter = CityResultAdapter { result ->
            onCitySelected(result)
        }

        recyclerView.layoutManager = LinearLayoutManager(targetContext)
        recyclerView.adapter = adapter

        var alertDialog: AlertDialog? = null

        val handler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        fun performSearch(query: String) {
            if (query.isBlank()) {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                statusText.visibility = View.VISIBLE
                statusText.text = targetContext.getString(R.string.city_search_empty_prompt)
                adapter.submitList(emptyList())
                return
            }

            progressBar.visibility = View.VISIBLE
            statusText.visibility = View.GONE

            GeocodingHelper.searchCities(
                context = targetContext,
                query = query,
                count = 10,
                language = Locale.getDefault().language,
                onSuccess = { results ->
                    progressBar.visibility = View.GONE
                    if (results.isNotEmpty()) {
                        statusText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(results)
                    } else {
                        recyclerView.visibility = View.GONE
                        statusText.visibility = View.VISIBLE
                        statusText.text = targetContext.getString(R.string.city_search_no_results)
                        adapter.submitList(emptyList())
                    }
                },
                onError = {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    statusText.visibility = View.VISIBLE
                    statusText.text = targetContext.getString(R.string.city_search_no_results)
                    adapter.submitList(emptyList())
                }
            )
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                val query = s?.toString().orEmpty()
                val runnable = Runnable { performSearch(query) }
                searchRunnable = runnable
                handler.postDelayed(runnable, 350L)
            }
        })

        adapter.onItemClickListener = { result ->
            onCitySelected(result)
            alertDialog?.dismiss()
        }

        alertDialog = MaterialAlertDialogBuilder(dialogContext)
            .setTitle(targetContext.getString(R.string.city_search_title))
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        alertDialog.show()
    }

    private class CityResultAdapter(
        var onItemClickListener: ((GeocodingResult) -> Unit)? = null
    ) : RecyclerView.Adapter<CityResultAdapter.ViewHolder>() {

        private val items = mutableListOf<GeocodingResult>()

        fun submitList(newList: List<GeocodingResult>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_city_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, onItemClickListener)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val displayNameText = itemView.findViewById<TextView>(R.id.city_display_name)
            private val coordsText = itemView.findViewById<TextView>(R.id.city_coords_text)

            fun bind(item: GeocodingResult, listener: ((GeocodingResult) -> Unit)?) {
                displayNameText.text = item.displayName
                coordsText.text = String.format(Locale.US, "%.4f, %.4f", item.latitude, item.longitude)
                itemView.setOnClickListener {
                    listener?.invoke(item)
                }
            }
        }
    }
}
