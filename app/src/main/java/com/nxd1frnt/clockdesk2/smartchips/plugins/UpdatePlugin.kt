package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.utils.UpdateManager

class UpdatePlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_updates"

    override val priority: Int = 150

    init {
        UpdateManager.checkForUpdates(context)
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)

        view.setOnClickListener {
            if (UpdateManager.isUpdateAvailable) {
                UpdateManager.downloadAndInstall(context)
            }
        }
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        if (!UpdateManager.isUpdateAvailable) return false

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        iconView.setImageResource(context.resources.getIdentifier("update", "drawable", context.packageName))

        textView.text = context.getString(R.string.update_available_text)

        return true
    }
}