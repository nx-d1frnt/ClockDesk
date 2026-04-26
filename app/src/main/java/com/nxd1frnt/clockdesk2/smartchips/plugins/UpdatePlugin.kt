package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.UpdateManager

class UpdatePlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_updates"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 6 * 60 * 60 * 1000L

    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            UpdateManager.checkForUpdates(context, force = false)
            Logger.d("UpdatePlugin"){"Periodic update check triggered"}
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener

        UpdateManager.onUpdateStateChanged = {
            stateChangeListener?.invoke()
        }
    }

    override fun startListening() {
        if (isListening) return
        isListening = true
        handler.post(periodicCheckRunnable)
    }

    override fun stopListening() {
        if (!isListening) return
        isListening = false
        handler.removeCallbacks(periodicCheckRunnable)
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
        ?.replace(Regex("###|##|#|\\*\\*|__"), "")
        ?.trim()

        val materialDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
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
            textView.text = context.getString(R.string.checking_updates)
            iconView.setImageResource(R.drawable.update)
            return true
        }

        if (!UpdateManager.isUpdateAvailable) return false

        iconView.setImageResource(context.resources.getIdentifier("update", "drawable", context.packageName))
        textView.text = context.getString(R.string.update_available_text)

        return true
    }
}