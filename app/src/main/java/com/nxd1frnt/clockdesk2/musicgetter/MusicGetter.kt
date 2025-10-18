package com.nxd1frnt.clockdesk2.musicgetter

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import javax.security.auth.callback.Callback

open class MusicGetter(private val context: Context, private val callback: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    val requestQueue = Volley.newRequestQueue(context.applicationContext)

    var enabled = false
    var currentTrack: String? = null

    val updateRunnable = object : Runnable {
        override fun run() {
            fetch()
            handler.postDelayed(this, 30000) // Update every 30 seconds
        }
    }

    open fun startUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        callback()
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    open fun fetch() {
        currentTrack = null
        enabled = false
    }
}