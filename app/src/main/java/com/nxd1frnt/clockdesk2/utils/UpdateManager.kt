package com.nxd1frnt.clockdesk2.utils

import android.app.Activity
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
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.network.NetworkManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UpdateManager {

    data class ReleaseHistoryItem(
        val tagName: String,
        val name: String,
        val date: String,
        val rawBody: String,
        val cleanNotes: String,
        val isPrerelease: Boolean,
        val htmlUrl: String
    )

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
    private const val RELEASES_LIST_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=20"
    private const val STABLE_RELEASE_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val NIGHTLY_RELEASE_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/nightly"
    private const val CHANGELOG_START_TAG = "<!-- changelog-start -->"
    private const val CHANGELOG_END_TAG = "<!-- changelog-end -->"

    private const val PREFS_SETTINGS = "ClockDeskPrefs"
    private const val PREFS_UPDATE = "update_prefs"
    const val KEY_UPDATE_NIGHTLY_CHANNEL = "update_nightly_channel"
    const val KEY_LAST_CHECK = "last_check_time"
    const val KEY_LAST_CHECK_SUCCESS = "last_check_success"
    private const val KEY_LAST_SUCCESS_TIME = "last_success_time"
    private const val KEY_SUCCESS_CHANNEL = "last_success_channel"
    const val KEY_CACHED_RELEASES_JSON = "cached_releases_json"
    const val KEY_LAST_RUN_VERSION_CODE = "last_run_version_code"
    const val KEY_LAST_RUN_VERSION_NAME = "last_run_version_name"
    const val KEY_PENDING_UPDATE_CHANGELOG = "pending_update_changelog"
    const val KEY_PENDING_UPDATE_VERSION = "pending_update_version"
    const val KEY_CHANGELOG_SHOWN_VERSION_CODE = "changelog_shown_version_code"
    const val KEY_CHANGELOG_SHOWN_VERSION_NAME = "changelog_shown_version_name"
    private var checkedChannel: Boolean? = null

    var isChecking: Boolean = false
        private set
    var releaseNotes: String? = null
        private set
    var rawReleaseNotes: String? = null
        private set
    var isUpdateAvailable: Boolean = false
        private set
    var downloadUrl: String? = null
        private set
    var latestVersion: String? = null
        private set
    var lastError: String? = null
        private set

    var releaseHistory: List<ReleaseHistoryItem> = emptyList()
        private set
    var isFetchingHistory: Boolean = false
        private set
    private val pendingHistoryCallbacks = mutableListOf<(List<ReleaseHistoryItem>, String?) -> Unit>()

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
    private val pollHandler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null

    var onUpdateStateChanged: (() -> Unit)? = null
    private val stateListeners = mutableSetOf<() -> Unit>()

    fun addStateListener(listener: () -> Unit) { stateListeners.add(listener) }

    fun removeStateListener(listener: () -> Unit) { stateListeners.remove(listener) }

    private fun notifyStateChanged() {
        onUpdateStateChanged?.invoke()
        stateListeners.toList().forEach { it() }
    }

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
                                notifyStateChanged()
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                downloadState = DownloadState.FAILED
                                notifyStateChanged()
                                return
                            }
                            else -> {
                                notifyStateChanged()
                            }
                        }
                    } else {
                        downloadState = DownloadState.FAILED
                        notifyStateChanged()
                        return
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

    fun getLastSuccessfulCheckTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SUCCESS_CHANNEL) ||
            prefs.getBoolean(KEY_SUCCESS_CHANNEL, false) != isNightlyChannel(context)) return 0L
        return prefs.getLong(KEY_LAST_SUCCESS_TIME, 0L)
    }

    fun cancelDownload(context: Context) {
        pollHandler.removeCallbacks(progressPollRunnable)
        if (activeDownloadId != -1L) {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)?.remove(activeDownloadId)
        }
        activeDownloadId = -1L
        downloadState = DownloadState.IDLE
        downloadProgress = 0
        downloadedBytes = 0L
        totalBytes = 0L
        notifyStateChanged()
    }

    fun getDownloadedFile(context: Context): File? {
        val name = apkFileName ?: return null
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(dir, name)
        // DownloadManager writes directly to the destination; a failed download may leave a partial file.
        if (!file.exists() || file.length() == 0L) return null
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        return if (archive?.packageName == context.packageName) file else null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun checkForUpdates(context: Context, force: Boolean = false) {
        if (isChecking || downloadState == DownloadState.DOWNLOADING) return
        appContext = context.applicationContext
        val updatePrefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        val lastCheck = updatePrefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        // Check every 24 hours if not forced
        if (!force && (now - lastCheck) < 24 * 60 * 60 * 1000) return

        val isNightly = isNightlyChannel(context)
        if (checkedChannel != isNightly) {
            isUpdateAvailable = false
            latestVersion = null
            releaseNotes = null
            rawReleaseNotes = null
            downloadUrl = null
            apkDownloadUrl = null
            apkFileName = null
            downloadState = DownloadState.IDLE
            checkedChannel = isNightly
        }
        val apiUrl = if (isNightly) NIGHTLY_API_URL else STABLE_API_URL
        val fallbackReleaseUrl = if (isNightly) NIGHTLY_RELEASE_PAGE_URL else STABLE_RELEASE_PAGE_URL

        isChecking = true
        lastError = null
        notifyStateChanged()

        val request = JsonObjectRequest(Request.Method.GET, apiUrl, null,
            { response ->
                isChecking = false
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = pInfo.versionName ?: "0"

                    val tagName = response.optString("tag_name", "")
                    val releaseName = response.optString("name", "")
                    val rawBody = response.optString("body", "")
                    rawReleaseNotes = rawBody
                    releaseNotes = parseChangelog(rawBody)
                    downloadUrl = response.optString("html_url", fallbackReleaseUrl)

                    if (isNightly) {
                        // Nightly Channel Comparison:
                        val serverCommit = Regex("""\b([0-9a-f]{7})\b""").find(releaseName)?.value
                            ?: Regex("""\b([0-9a-f]{7})\b""").find(rawBody)?.value

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

                    val updateEditor = updatePrefs.edit()
                        .putLong(KEY_LAST_CHECK, now)
                        .putLong(KEY_LAST_SUCCESS_TIME, now)
                        .putBoolean(KEY_SUCCESS_CHANNEL, isNightly)
                        .putBoolean(KEY_LAST_CHECK_SUCCESS, true)

                    if (isUpdateAvailable && !releaseNotes.isNullOrBlank()) {
                        updateEditor.putString(KEY_PENDING_UPDATE_CHANGELOG, releaseNotes)
                        updateEditor.putString(KEY_PENDING_UPDATE_VERSION, latestVersion)
                    }
                    updateEditor.apply()

                    Logger.d("UpdateManager") {
                        "Checked ($apiUrl): current=$currentVersion, available=$isUpdateAvailable, latest=$latestVersion, apk=$apkFileName"
                    }
                    notifyStateChanged()
                } catch (e: Exception) {
                    e.printStackTrace()
                    isChecking = false
                    lastError = e.message
                    notifyStateChanged()
                }
            },
            { error ->
                error.printStackTrace()
                isChecking = false
                lastError = error.message ?: "Network error"
                updatePrefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                notifyStateChanged()
            }
        )

        NetworkManager.getRequestQueue(context).add(request)
    }

    private fun resolveApkAsset(response: org.json.JSONObject) {
        apkDownloadUrl = null
        apkFileName = null
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
        if (downloadState == DownloadState.DOWNLOADING) return
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
            notifyStateChanged()

            pollHandler.removeCallbacks(progressPollRunnable)
            pollHandler.post(progressPollRunnable)
        } catch (e: Exception) {
            Logger.e("UpdateManager") { "Failed to start download: ${e.message}" }
            downloadState = DownloadState.FAILED
            notifyStateChanged()
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

    internal fun isNewer(server: String, current: String): Boolean {
        val sClean = server.trim().removePrefix("v").removePrefix("V")
        val cClean = current.trim().removePrefix("v").removePrefix("V")

        if (sClean.equals(cClean, ignoreCase = true)) return false

        // Normalize versions: strip build metadata (+...) and strip nightly tags from current if needed
        val sNormalized = sClean.substringBefore('+')
        val cNormalized = cClean.substringBefore('+').replace(Regex("-nightly.*", RegexOption.IGNORE_CASE), "")

        if (sNormalized.equals(cNormalized, ignoreCase = true)) {
            // If current was a nightly snapshot of the exact same version, the release is considered newer
            return current.contains("nightly", ignoreCase = true) && !server.contains("nightly", ignoreCase = true)
        }

        // Split Core and Pre-release (e.g. "2.0.0-rc2.1" -> core "2.0.0", pre "rc2.1")
        val sCore = sNormalized.substringBefore('-')
        val sPre = if (sNormalized.contains('-')) sNormalized.substringAfter('-') else null

        val cCore = cNormalized.substringBefore('-')
        val cPre = if (cNormalized.contains('-')) cNormalized.substringAfter('-') else null

        // 1. Compare Core version numbers: e.g. [2, 0, 0] vs [2, 0, 0]
        val sCoreParts = sCore.split('.').mapNotNull { it.toIntOrNull() }
        val cCoreParts = cCore.split('.').mapNotNull { it.toIntOrNull() }
        val maxCoreLen = maxOf(sCoreParts.size, cCoreParts.size)

        for (i in 0 until maxCoreLen) {
            val sNum = sCoreParts.getOrElse(i) { 0 }
            val cNum = cCoreParts.getOrElse(i) { 0 }
            if (sNum > cNum) return true
            if (sNum < cNum) return false
        }

        // 2. Core versions are equal. In SemVer, stable (no pre-release) > pre-release
        if (sPre == null && cPre != null) return true
        if (sPre != null && cPre == null) return false
        if (sPre == null || cPre == null) return false

        // 3. Both have pre-releases: e.g. "rc2.1" vs "rc2"
        return comparePreReleases(sPre, cPre) > 0
    }

    private fun comparePreReleases(sPre: String, cPre: String): Int {
        val sTokens = tokenizePreRelease(sPre)
        val cTokens = tokenizePreRelease(cPre)
        val minLen = minOf(sTokens.size, cTokens.size)

        for (i in 0 until minLen) {
            val s = sTokens[i]
            val c = cTokens[i]

            if (s == c) continue

            return when {
                s is Int && c is Int -> s.compareTo(c)
                s is String && c is String -> {
                    val rankS = preReleaseRank(s)
                    val rankC = preReleaseRank(c)
                    if (rankS != rankC) {
                        rankS.compareTo(rankC)
                    } else {
                        s.compareTo(c)
                    }
                }
                s is Int && c is String -> -1
                s is String && c is Int -> 1
                else -> 0
            }
        }

        return sTokens.size.compareTo(cTokens.size)
    }

    private fun tokenizePreRelease(pre: String): List<Any> {
        val tokens = mutableListOf<Any>()
        val rawParts = pre.lowercase().split('.', '-')
        val chunkRegex = Regex("([0-9]+|[a-zA-Z]+)")
        for (part in rawParts) {
            for (match in chunkRegex.findAll(part)) {
                val str = match.value
                val num = str.toIntOrNull()
                if (num != null) {
                    tokens.add(num)
                } else {
                    tokens.add(str)
                }
            }
        }
        return tokens
    }

    private fun preReleaseRank(str: String): Int = when (str.lowercase()) {
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "rc", "preview", "pre" -> 3
        else -> 0
    }

    fun parseChangelog(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val startIdx = raw.indexOf(CHANGELOG_START_TAG, ignoreCase = true)
        val endIdx = if (startIdx >= 0) raw.indexOf(CHANGELOG_END_TAG, startIdx + CHANGELOG_START_TAG.length, ignoreCase = true) else -1

        val sectionText = if (startIdx >= 0 && endIdx > startIdx) {
            raw.substring(startIdx + CHANGELOG_START_TAG.length, endIdx)
        } else {
            fallbackExtractChangelog(raw)
        }

        return formatChangelogText(sectionText)
    }

    private fun fallbackExtractChangelog(raw: String): String {
        val lines = raw.lines()
        val startIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.matches(Regex("""^#{1,4}\s*(?:What'?s\s+Changed|Changes).*""", RegexOption.IGNORE_CASE))
        }

        val candidateLines = if (startIndex >= 0) {
            lines.drop(startIndex + 1)
        } else {
            // No "What's Changed" heading found. Filter out top boilerplate metadata/admonitions.
            lines.dropWhile { line ->
                val trimmed = line.trim()
                trimmed.startsWith("###") || trimmed.startsWith("#") ||
                    trimmed.startsWith("- **Branch:") || trimmed.startsWith("- **Commit:") ||
                    trimmed.startsWith("- **Date:") || trimmed.startsWith(">") ||
                    trimmed.isBlank()
            }
        }

        // Cut off at downstream sections like Downloads, Checksums, Full Changelog links
        val result = mutableListOf<String>()
        for (line in candidateLines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("""^#{1,4}\s*.*(?:Downloads|Checksums|SHA-256).*""", RegexOption.IGNORE_CASE)) ||
                trimmed.contains("SHA-256 Checksums", ignoreCase = true) ||
                trimmed.startsWith("**Full Changelog**:", ignoreCase = true) ||
                trimmed.startsWith("**Full Changes**:", ignoreCase = true) ||
                trimmed.startsWith("Download the APK", ignoreCase = true)
            ) {
                break
            }
            result.add(line)
        }

        return result.joinToString("\n")
    }

    private fun formatChangelogText(text: String): String {
        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        // Regex for stripping GitHub commit link badges like " ([6e5396c](https://github.com/...))"
        val commitLinkRegex = Regex("""\s*\(\[[0-9a-zA-Z]{6,}\]\(https?://[^)]+\)\)""")
        val boldRegex = Regex("""\*\*|__""")

        for (rawLine in lines) {
            var line = rawLine.trim()

            // Skip blockquote warning/note markers if any leak in
            if (line.startsWith(">")) continue

            // Strip commit badges: ([sha](https://...))
            line = commitLinkRegex.replace(line, "").trim()

            // Handle headings: #### Features -> Features:
            if (line.startsWith("#")) {
                val headerContent = line.replace(Regex("""^#{1,6}\s*"""), "").replace(boldRegex, "").trim()
                if (headerContent.isNotBlank()) {
                    val formattedHeader = if (headerContent.endsWith(":")) headerContent else "$headerContent:"
                    cleanedLines.add(formattedHeader)
                }
                continue
            }

            // Handle list bullets: "- foo" or "* foo" -> "• foo"
            if (line.startsWith("- ") || line.startsWith("* ")) {
                val item = line.substring(2).trim()
                if (item.isNotBlank()) {
                    cleanedLines.add("• $item")
                }
                continue
            }

            // Normal text line
            if (line.isNotBlank()) {
                cleanedLines.add(line)
            } else if (cleanedLines.isNotEmpty() && cleanedLines.last().isNotBlank()) {
                // Keep at most one blank line between paragraphs/sections
                cleanedLines.add("")
            }
        }

        val result = cleanedLines.joinToString("\n").trim()
        return if (result.isBlank()) {
            "Routine maintenance and stability build."
        } else {
            result
        }
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

    fun loadCachedReleaseHistory(context: Context): List<ReleaseHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CACHED_RELEASES_JSON, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ReleaseHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ReleaseHistoryItem(
                    tagName = obj.optString("tagName"),
                    name = obj.optString("name"),
                    date = obj.optString("date"),
                    rawBody = obj.optString("rawBody"),
                    cleanNotes = obj.optString("cleanNotes"),
                    isPrerelease = obj.optBoolean("isPrerelease"),
                    htmlUrl = obj.optString("htmlUrl")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveReleaseHistoryCache(context: Context, items: List<ReleaseHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("tagName", item.tagName)
                put("name", item.name)
                put("date", item.date)
                put("rawBody", item.rawBody)
                put("cleanNotes", item.cleanNotes)
                put("isPrerelease", item.isPrerelease)
                put("htmlUrl", item.htmlUrl)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CACHED_RELEASES_JSON, jsonArray.toString()).apply()
    }

    fun isReleaseMatch(item: ReleaseHistoryItem, version: String): Boolean {
        val cleanVer = version.removePrefix("v").trim()
        val cleanTag = item.tagName.removePrefix("v").trim()
        if (cleanTag.equals(cleanVer, ignoreCase = true)) return true
        val escapedVer = Regex.escape(cleanVer)
        val namePattern = Regex("""(?:^|[\s_vV])${escapedVer}(?:$|[\s_])""", RegexOption.IGNORE_CASE)
        if (namePattern.containsMatchIn(item.name)) return true
        if (version.contains("nightly", ignoreCase = true) && item.tagName.equals("nightly", ignoreCase = true)) {
            val installedCommit = Regex("nightly-([0-9a-f]{7,40})$", RegexOption.IGNORE_CASE)
                .find(cleanVer)?.groupValues?.get(1) ?: return false
            val releaseCommit = Regex("""\b([0-9a-f]{7,40})\b""", RegexOption.IGNORE_CASE)
                .find(item.name)?.value ?: return false
            return installedCommit.equals(releaseCommit, ignoreCase = true)
        }
        val commit = Regex("""\b([0-9a-f]{7,8})\b""").find(version)?.value
        if (commit != null && (item.name.contains(commit, ignoreCase = true) || item.rawBody.contains(commit, ignoreCase = true))) {
            return true
        }
        return false
    }

    fun fetchReleaseHistory(
        context: Context,
        force: Boolean = false,
        onComplete: ((List<ReleaseHistoryItem>, String?) -> Unit)? = null
    ) {
        if (releaseHistory.isEmpty()) {
            releaseHistory = loadCachedReleaseHistory(context)
        }

        if (!force && releaseHistory.isNotEmpty()) {
            onComplete?.invoke(releaseHistory, null)
            return
        }

        if (onComplete != null) {
            synchronized(pendingHistoryCallbacks) {
                pendingHistoryCallbacks.add(onComplete)
            }
        }

        if (isFetchingHistory) return
        isFetchingHistory = true

        val request = JsonArrayRequest(
            Request.Method.GET,
            RELEASES_LIST_API_URL,
            null,
            { responseArray ->
                val callbacks = synchronized(pendingHistoryCallbacks) {
                    val list = pendingHistoryCallbacks.toList()
                    pendingHistoryCallbacks.clear()
                    list
                }
                isFetchingHistory = false
                try {
                    val items = mutableListOf<ReleaseHistoryItem>()
                    for (i in 0 until responseArray.length()) {
                        val obj = responseArray.getJSONObject(i)
                        val tagName = obj.optString("tag_name", "")
                        val name = obj.optString("name", "").ifBlank { tagName }
                        val publishedAt = obj.optString("published_at", "")
                        val dateStr = formatIsoDate(context, publishedAt)
                        val rawBody = obj.optString("body", "")
                        val clean = parseChangelog(rawBody) ?: "Routine maintenance and stability build."
                        val isPrerelease = obj.optBoolean("prerelease", false)
                        val htmlUrl = obj.optString("html_url", "")

                        items.add(ReleaseHistoryItem(
                            tagName = tagName,
                            name = name,
                            date = dateStr,
                            rawBody = rawBody,
                            cleanNotes = clean,
                            isPrerelease = isPrerelease,
                            htmlUrl = htmlUrl
                        ))
                    }
                    releaseHistory = items
                    saveReleaseHistoryCache(context, items)
                    callbacks.forEach { it.invoke(items, null) }
                } catch (e: Exception) {
                    callbacks.forEach { it.invoke(releaseHistory, e.message) }
                }
            },
            { error ->
                val callbacks = synchronized(pendingHistoryCallbacks) {
                    val list = pendingHistoryCallbacks.toList()
                    pendingHistoryCallbacks.clear()
                    list
                }
                isFetchingHistory = false
                callbacks.forEach { it.invoke(releaseHistory, error.message) }
            }
        )
        request.setShouldCache(!force)
        NetworkManager.getRequestQueue(context).add(request)
    }

    private fun formatIsoDate(context: Context, isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = parser.parse(isoString.substringBefore('.'))
            if (date != null) {
                android.text.format.DateFormat.getMediumDateFormat(context).format(date)
            } else {
                isoString.substringBefore('T')
            }
        } catch (e: Exception) {
            isoString.substringBefore('T')
        }
    }

    fun checkAndShowPostUpdateChangelog(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
        val lastRunCode = prefs.getInt(KEY_LAST_RUN_VERSION_CODE, -1)
        val lastRunName = prefs.getString(KEY_LAST_RUN_VERSION_NAME, null)
        val shownCode = prefs.getInt(KEY_CHANGELOG_SHOWN_VERSION_CODE, -1)
        val currentCode = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_CODE
        val currentName = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_NAME

        val shownName = prefs.getString(KEY_CHANGELOG_SHOWN_VERSION_NAME, null) ?: lastRunName
        if (shownCode == currentCode && shownName == currentName) {
            return
        }

        val clockDeskPrefs = activity.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val isFirstLaunch = clockDeskPrefs.getBoolean("isFirstLaunch", true)
        val hasExistingSettings = clockDeskPrefs.all.isNotEmpty()

        val isPackageUpdated = try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            pInfo.firstInstallTime != pInfo.lastUpdateTime
        } catch (e: Exception) {
            false
        }

        val pendingNotes = prefs.getString(KEY_PENDING_UPDATE_CHANGELOG, null)
        val isUpdate = (lastRunCode != -1 && (lastRunCode != currentCode || lastRunName != currentName))
            || !pendingNotes.isNullOrBlank()
            || isPackageUpdated
            || (!isFirstLaunch && hasExistingSettings)

        if (!isUpdate) {
            prefs.edit()
                .putInt(KEY_LAST_RUN_VERSION_CODE, currentCode)
                .putString(KEY_LAST_RUN_VERSION_NAME, currentName)
                .putInt(KEY_CHANGELOG_SHOWN_VERSION_CODE, currentCode)
                .putString(KEY_CHANGELOG_SHOWN_VERSION_NAME, currentName)
                .apply()
            return
        }

        val markShown = {
            prefs.edit()
                .putInt(KEY_LAST_RUN_VERSION_CODE, currentCode)
                .putString(KEY_LAST_RUN_VERSION_NAME, currentName)
                .putInt(KEY_CHANGELOG_SHOWN_VERSION_CODE, currentCode)
                .putString(KEY_CHANGELOG_SHOWN_VERSION_NAME, currentName)
                .remove(KEY_PENDING_UPDATE_CHANGELOG)
                .remove(KEY_PENDING_UPDATE_VERSION)
                .apply()
        }

        if (!pendingNotes.isNullOrBlank()) {
            markShown()
            showPostUpdateDialog(activity, currentName, pendingNotes)
        } else {
            if (releaseHistory.isEmpty()) {
                releaseHistory = loadCachedReleaseHistory(activity)
            }
            val cached = releaseHistory.firstOrNull { isReleaseMatch(it, currentName) }?.cleanNotes

            if (!cached.isNullOrBlank()) {
                markShown()
                showPostUpdateDialog(activity, currentName, cached)
            } else {
                fetchReleaseHistory(activity, force = true) { items, _ ->
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        val fetched = items.firstOrNull { isReleaseMatch(it, currentName) }?.cleanNotes
                        markShown()
                        showPostUpdateDialog(activity, currentName, fetched)
                    }
                }
            }
        }
    }

    private fun showPostUpdateDialog(activity: Activity, version: String, notes: String?) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            val msg = notes?.trim().takeIf { !it.isNullOrBlank() }
                ?: activity.getString(R.string.update_post_update_generic_message)

            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.whats_new_in_version, version))
                .setMessage(msg)
                .setPositiveButton(R.string.got_it, null)
                .setNeutralButton(R.string.changelog_history_title) { _, _ ->
                    openChangelogHistory(activity)
                }
                .create()

            dialog.show()
            UiMonetThemeManager.styleAlertDialog(dialog)
        }
    }

    fun openChangelogHistory(context: Context) {
        try {
            val intent = Intent(context, com.nxd1frnt.clockdesk2.ui.settings.SettingsActivity::class.java).apply {
                putExtra("target_fragment", "changelog_history")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e("UpdateManager") { "Could not open changelog history: ${e.message}" }
        }
    }
}
