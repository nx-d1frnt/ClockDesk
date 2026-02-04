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
        UpdateManager.onUpdateStateChanged = {
        }
        UpdateManager.checkForUpdates(context, true)
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)

        view.setOnClickListener {
            if (UpdateManager.isUpdateAvailable) {          
                showAppUpdateDialog()
            }
        }
        return view
    }

    private fun showAppUpdateDialog(){
        val cleanNotes = UpdateManager.releaseNotes
        ?.replace(Regex("###|##|#|\\*\\*|__"), "") // Убираем тяжелую разметку
        ?.trim()

        val materialDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
        .setTitle(context.getString(R.string.new_version_title))
        .setMessage(cleanNotes ?: context.getString(R.string.update_description_default))
        .setPositiveButton(context.getString(R.string.install_action)) { _, _ ->
            UpdateManager.downloadAndInstall(context)
        }
        .setNegativeButton(context.getString(R.string.later_action), null)
        .create()

        materialDialog.show()
    }

override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
    val iconView = view.findViewById<ImageView>(R.id.chip_icon)
    val textView = view.findViewById<TextView>(R.id.chip_text)

    if (UpdateManager.isChecking) {
        textView.text = context.getString(R.string.checking_updates) // "Проверка обновлений..."
        iconView.setImageResource(R.drawable.update) // Желательно анимированный VectorDrawable
        return true 
    }

    if (!UpdateManager.isUpdateAvailable) return false

    iconView.setImageResource(context.resources.getIdentifier("update", "drawable", context.packageName))
    textView.text = context.getString(R.string.update_available_text)
    
    return true
}
}