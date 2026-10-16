package com.nxd1frnt.clockdesk2.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.network.NetworkManager
import org.json.JSONException
import java.io.File
import java.util.Locale

object UpdateManager {

    enum class DownloadState {
        IDLE,
        DOWNLOADING,
        READY_TO_INSTALL,
        FAILED
    }

    private const val GITHUB_OWNER = "nx-d1frnt"
    private const val GITHUB_REPO = "clockdesk"
    private const val STABLE_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val NIGHTLY_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/nightly"
    private const val STABLE_RELEASE_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val NIGHTLY_RELEASE_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/nightly"

    private const val PREFS_SETTINGS = "ClockDeskPrefs"
    private const val PREFS_UPDATE = "update_prefs"
    const val KEY_UPDATE_NIGHTLY_CHANNEL = "update_nightly_channel"
    const val KEY_LAST_CHECK = "last_check_time"
    const val KEY_LAST_CHECK_SUCCESS = "last_check_success"

    var isChecking: Boolean = false
        private set
    var releaseNotes: String? = null
        private set
    var isUpdateAvailable: Boolean = false
        private set
    var downloadUrl: String? = null
        private set
    var latestVersion: String? = null
        private set
    var lastError: String? = null
        private set

    // In-app download tracking
    var downloadState: DownloadState = DownloadState.IDLE
        private set
    var downloadProgress: Int = 0 // 0 to 100
        private set
    var downloadedBytes: Long = 0L
        private set
    var totalBytes: Long = 0L
        private set
    var apkDownloadUrl: String? = null
        private set
    var apkFileName: String? = null
        private set

    private var activeDownloadId: Long = -1L
    private val pollHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    var onUpdateStateChanged: (() -> Unit)? = null

    private val progressPollRunnable = object : Runnable {
        override fun run() {
            if (downloadState != DownloadState.DOWNLOADING || activeDownloadId == -1L) return
            val ctx = appContext ?: return
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val query = DownloadManager.Query().setFilterById(activeDownloadId)
            val cursor = dm.query(query)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        downloadedBytes = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                        totalBytes = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L

                        if (totalBytes > 0) {
                            downloadProgress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloadState = DownloadState.READY_TO_INSTALL
                                downloadProgress = 100
                                onUpdateStateChanged?.invoke()
                                // Automatically prompt install once download finishes
                                installApk(ctx)
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                downloadState = DownloadState.FAILED
                                onUpdateStateChanged?.invoke()
                                return
                            }
                            else -> {
                                onUpdateStateChanged?.invoke()
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
            pollHandler.postDelayed(this, 500)
        }
    }

    fun isNightlyChannel(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val isNightlyBuild = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)
        return prefs.getBoolean(KEY_UPDATE_NIGHTLY_CHANNEL, isNightlyBuild)
    }

    fun getLastCheckTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
    }

    fun getDownloadedFile(context: Context): File? {
        val name = apkFileName ?: return null
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(dir, name)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun checkForUpdates(context: Context, force: Boolean = false) {
        appContext = context.applicationContext
        val updatePrefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        val lastCheck = updatePrefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        // Check every 24 hours if not forced
        if (!force && (now - lastCheck) < 24 * 60 * 60 * 1000) return

        val isNightly = isNightlyChannel(context)
        val apiUrl = if (isNightly) NIGHTLY_API_URL else STABLE_API_URL
        val fallbackReleaseUrl = if (isNightly) NIGHTLY_RELEASE_PAGE_URL else STABLE_RELEASE_PAGE_URL

        isChecking = true
        lastError = null
        onUpdateStateChanged?.invoke()

        val request = JsonObjectRequest(Request.Method.GET, apiUrl, null,
            { response ->
                isChecking = false
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = pInfo.versionName ?: "0"

                    val tagName = response.optString("tag_name", "")
                    val releaseName = response.optString("name", "")
                    releaseNotes = response.optString("body", "")
                    downloadUrl = response.optString("html_url", fallbackReleaseUrl)

                    if (isNightly) {
                        // Nightly Channel Comparison:
                        val serverCommit = Regex("""\b([0-9a-f]{7})\b""").find(releaseName)?.value
                            ?: Regex("""\b([0-9a-f]{7})\b""").find(releaseNotes ?: "")?.value

                        val isCurrentNightly = currentVersion.contains("nightly", ignoreCase = true)

                        if (!isCurrentNightly) {
                            isUpdateAvailable = true
                            latestVersion = releaseName.ifBlank { "Nightly" }
                        } else if (serverCommit != null) {
                            if (!currentVersion.contains(serverCommit, ignoreCase = true)) {
                                isUpdateAvailable = true
                                latestVersion = releaseName.ifBlank { "Nightly ($serverCommit)" }
                            } else {
                                isUpdateAvailable = false
                                latestVersion = currentVersion
                            }
                        } else {
                            isUpdateAvailable = (releaseName != currentVersion)
                            latestVersion = releaseName.ifBlank { "Nightly" }
                        }
                    } else {
                        // Stable Channel Comparison:
                        val serverVersion = tagName.removePrefix("v")
                        val cleanCurrent = currentVersion.removePrefix("v")
                        if (isNewer(serverVersion, cleanCurrent)) {
                            latestVersion = tagName
                            isUpdateAvailable = true
                        } else {
                            isUpdateAvailable = false
                            latestVersion = currentVersion
                        }
                    }

                    // Resolve optimal APK asset matching device architecture
                    if (isUpdateAvailable) {
                        resolveApkAsset(response)
                        val downloaded = getDownloadedFile(context)
                        if (downloaded != null) {
                            downloadState = DownloadState.READY_TO_INSTALL
                            downloadProgress = 100
                        } else if (downloadState != DownloadState.DOWNLOADING) {
                            downloadState = DownloadState.IDLE
                        }
                    } else {
                        downloadState = DownloadState.IDLE
                    }

                    updatePrefs.edit()
                        .putLong(KEY_LAST_CHECK, now)
                        .putBoolean(KEY_LAST_CHECK_SUCCESS, true)
                        .apply()

                    Logger.d("UpdateManager") {
                        "Checked ($apiUrl): current=$currentVersion, available=$isUpdateAvailable, latest=$latestVersion, apk=$apkFileName"
                    }
                    onUpdateStateChanged?.invoke()
                } catch (e: Exception) {
                    e.printStackTrace()
                    isChecking = false
                    lastError = e.message
                    onUpdateStateChanged?.invoke()
                }
            },
            { error ->
                error.printStackTrace()
                isChecking = false
                lastError = error.message ?: "Network error"
                updatePrefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                onUpdateStateChanged?.invoke()
            }
        )

        NetworkManager.getRequestQueue(context).add(request)
    }

    private fun resolveApkAsset(response: org.json.JSONObject) {
        val assets = response.optJSONArray("assets") ?: return
        var bestUrl: String? = null
        var bestName: String? = null

        val supportedAbis = Build.SUPPORTED_ABIS

        // 1. Try to find APK matching device ABI
        for (abi in supportedAbis) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true) && name.contains("-$abi-", ignoreCase = true)) {
                    bestUrl = asset.optString("browser_download_url")
                    bestName = name
                    break
                }
            }
            if (bestUrl != null) break
        }

        // 2. Fallback to universal APK
        if (bestUrl == null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true) && name.contains("-universal-", ignoreCase = true)) {
                    bestUrl = asset.optString("browser_download_url")
                    bestName = name
                    break
                }
            }
        }

        // 3. Fallback to any APK
        if (bestUrl == null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    bestUrl = asset.optString("browser_download_url")
                    bestName = name
                    break
                }
            }
        }

        apkDownloadUrl = bestUrl
        apkFileName = bestName
    }

    fun startDownload(context: Context) {
        appContext = context.applicationContext
        val url = apkDownloadUrl ?: run {
            openReleasePage(context)
            return
        }
        val fileName = apkFileName ?: "ClockDesk-update.apk"
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
            openReleasePage(context)
            return
        }

        val destinationFile = File(downloadDir, fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: run {
            openReleasePage(context)
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(context.getString(R.string.downloading_update_title))
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destinationFile))
                .setMimeType("application/vnd.android.package-archive")

            activeDownloadId = downloadManager.enqueue(request)
            downloadState = DownloadState.DOWNLOADING
            downloadProgress = 0
            downloadedBytes = 0L
            totalBytes = 0L
            onUpdateStateChanged?.invoke()

            pollHandler.removeCallbacks(progressPollRunnable)
            pollHandler.post(progressPollRunnable)
        } catch (e: Exception) {
            Logger.e("UpdateManager") { "Failed to start download: ${e.message}" }
            downloadState = DownloadState.FAILED
            onUpdateStateChanged?.invoke()
            openReleasePage(context)
        }
    }

    fun installApk(context: Context) {
        val file = getDownloadedFile(context)
        if (file == null || !file.exists()) {
            startDownload(context)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, R.string.permission_unknown_apps_required, Toast.LENGTH_LONG).show()
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Logger.e("UpdateManager") { "Install intent failed: ${e.message}" }
            Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadAndInstall(context: Context) {
        when (downloadState) {
            DownloadState.READY_TO_INSTALL -> installApk(context)
            DownloadState.DOWNLOADING -> {
                Toast.makeText(context, R.string.downloading_update_title, Toast.LENGTH_SHORT).show()
            }
            else -> {
                val file = getDownloadedFile(context)
                if (file != null) {
                    downloadState = DownloadState.READY_TO_INSTALL
                    installApk(context)
                } else {
                    startDownload(context)
                }
            }
        }
    }

    private fun isNewer(server: String, current: String): Boolean {
        val sClean = server.removePrefix("v").lowercase()
        val cClean = current.removePrefix("v").lowercase()

        if (sClean == cClean) return false

        val sParts = sClean.split("[.-]".toRegex())
        val cParts = cClean.split("[.-]".toRegex())

        val length = maxOf(sParts.size, cParts.size)

        for (i in 0 until length) {
            val sPart = sParts.getOrElse(i) { "" }
            val cPart = cParts.getOrElse(i) { "" }

            if (sPart == cPart) continue

            val sNum = sPart.toIntOrNull()
            val cNum = cPart.toIntOrNull()

            if (sNum != null && cNum != null) {
                if (sNum > cNum) return true
                if (sNum < cNum) return false
            } else {
                if (sPart.isEmpty()) return true
                if (cPart.isEmpty()) return false
                return sPart > cPart
            }
        }
        return false
    }

    fun openReleasePage(context: Context) {
        val fallback = if (isNightlyChannel(context)) NIGHTLY_RELEASE_PAGE_URL else STABLE_RELEASE_PAGE_URL
        val url = downloadUrl ?: fallback

        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Logger.e("UpdateManager") { "Could not open browser: ${e.message}" }
        }
    }
}