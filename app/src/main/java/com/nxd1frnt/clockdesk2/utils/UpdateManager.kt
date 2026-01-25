package com.nxd1frnt.clockdesk2.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import java.io.File

object UpdateManager {

    private const val GITHUB_OWNER = "nx-d1frnt"
    private const val GITHUB_REPO = "clockdesk"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    var isUpdateAvailable: Boolean = false
        private set
    var downloadUrl: String? = null
        private set
    private var latestVersion: String? = null

    var onUpdateStateChanged: (() -> Unit)? = null

    fun checkForUpdates(context: Context) {
        val request = JsonObjectRequest(Request.Method.GET, API_URL, null,
            { response ->
                try {
                    val tagName = response.getString("tag_name") // "v1.3.0-beta3"
                    val serverVersion = tagName.removePrefix("v")

                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = pInfo.versionName?.removePrefix("v") ?: "0"
                    Logger.d("UpdateManager"){"Current version: $currentVersion, Server version: $serverVersion"}
                    if (isNewer(serverVersion, currentVersion)) {
                        val assets = response.getJSONArray("assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val url = asset.getString("browser_download_url")
                            if (url.endsWith(".apk")) {
                                downloadUrl = url
                                latestVersion = tagName
                                isUpdateAvailable = true
                                onUpdateStateChanged?.invoke()
                                break
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }
        )

        Volley.newRequestQueue(context).add(request)
    }

    private fun isNewer(server: String, current: String): Boolean {
        if (server == current) return false

        Logger.d("UpdateManager"){"Comparing versions: server='$server', current='$current', isNewer=${server > current}"}
        return server > current
    }

    fun downloadAndInstall(context: Context) {
        val url = downloadUrl ?: return
        val fileName = "ClockDesk_Update.apk"

        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (destinationFile.exists()) destinationFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ClockDesk v$latestVersion")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    installApk(ctxt, destinationFile)
                    ctxt.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    }

    private fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Logger.e("UpdateManager"){"File not found: ${file.absolutePath}"}
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}