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
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.File

object UpdateManager {

    private const val GITHUB_OWNER = "nx-d1frnt"
    private const val GITHUB_REPO = "clockdesk"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_time"

    var isChecking: Boolean = false
        private set
    var releaseNotes: String? = null
        private set
    var isUpdateAvailable: Boolean = false
        private set
    var downloadUrl: String? = null
        private set
    private var latestVersion: String? = null

    var onUpdateStateChanged: (() -> Unit)? = null

  fun checkForUpdates(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        // Проверяем раз в 24 часа, если не форсировано
        if (!force && (now - lastCheck) < 24 * 60 * 60 * 1000) return

        isChecking = true
        onUpdateStateChanged?.invoke()

        val request = JsonObjectRequest(Request.Method.GET, API_URL, null,
            { response ->
                isChecking = false
                try {
                    val tagName = response.getString("tag_name")
                    releaseNotes = response.optString("body", "") // Получаем ChangeLog
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
                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                    onUpdateStateChanged?.invoke()
                } catch (e: JSONException) {
                    e.printStackTrace()
                    isChecking = false
                    onUpdateStateChanged?.invoke()
                }
            },
            { error -> 
            error.printStackTrace() 
            isChecking = false
            onUpdateStateChanged?.invoke()}
        )

        Volley.newRequestQueue(context).add(request)
    }

   private fun isNewer(server: String, current: String): Boolean {
    // 1. Убираем "v" в начале, если есть
    val sClean = server.removePrefix("v")
    val cClean = current.removePrefix("v")
    
    // Если строки идентичны — обновления нет
    if (sClean == cClean) return false

    // 2. Разбиваем на токены по точкам и дефисам
    // Пример: "1.3.0-Beta5" -> ["1", "3", "0", "Beta5"]
    val sParts = sClean.split("[.-]".toRegex())
    val cParts = cClean.split("[.-]".toRegex())

    val length = maxOf(sParts.size, cParts.size)

    for (i in 0 until length) {
        val sPart = sParts.getOrElse(i) { "" }
        val cPart = cParts.getOrElse(i) { "" }

        // Если части равны, идем дальше
        if (sPart == cPart) continue

        // Пытаемся превратить части в числа
        val sNum = sPart.toIntOrNull()
        val cNum = cPart.toIntOrNull()

        // Логика сравнения:
        if (sNum != null && cNum != null) {
            // Оба числа (пример: 3 vs 4) -> сравниваем как числа
            if (sNum > cNum) return true
            if (sNum < cNum) return false
        } else {
            // Хотя бы одна часть — текст (или пустота).
            // Нюанс: Обычно "1.0.0" > "1.0.0-beta". 
            // То есть отсутствие суффикса круче, чем наличие суффикса.
            
            if (sPart.isEmpty()) return true // Server "1.0", App "1.0-beta" -> Server win
            if (cPart.isEmpty()) return false // Server "1.0-beta", App "1.0" -> App win (no update)
            
            // Сравниваем как строки (Beta5 vs Beta6)
            // Внимание: "Beta10" лексически меньше "Beta2". 
            // Но для простых случаев (Alpha < Beta < RC) это сработает.
            return sPart > cPart
        }
        Logger.d("UpdateManager"){"Compared parts: server='$sPart', current='$cPart'"}
    }
    return false
}

    fun downloadAndInstall(context: Context) {
        val url = downloadUrl ?: return

        try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(browserIntent)
        } catch (e: Exception) {
        Logger.e("UpdateManager") { "Could not open browser: ${e.message}" }
        }
        // val fileName = "ClockDesk_Update.apk"

        // val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // if (destinationFile.exists()) destinationFile.delete()

        // val request = DownloadManager.Request(Uri.parse(url))
        //     .setTitle("ClockDesk $latestVersion")
        //     .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        //     .setMimeType("application/vnd.android.package-archive")
        //     .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        // val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // val downloadId = manager.enqueue(request)

        // val onComplete = object : BroadcastReceiver() {
        //     override fun onReceive(ctxt: Context, intent: Intent) {
        //         val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        //         if (downloadId == id) {
        //             installApk(ctxt, destinationFile)
        //             ctxt.unregisterReceiver(this)
        //         }
        //     }
        // }
        // context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
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