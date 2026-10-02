package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.network.NetworkManager
import org.json.JSONException

object UpdateManager {

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

    var onUpdateStateChanged: (() -> Unit)? = null

    fun isNightlyChannel(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val isNightlyBuild = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)
        return prefs.getBoolean(KEY_UPDATE_NIGHTLY_CHANNEL, isNightlyBuild)
    }

    fun getLastCheckTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
    }

    fun checkForUpdates(context: Context, force: Boolean = false) {
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
                        // Extract 7-character commit hash from release name or body
                        val serverCommit = Regex("""\b([0-9a-f]{7})\b""").find(releaseName)?.value
                            ?: Regex("""\b([0-9a-f]{7})\b""").find(releaseNotes ?: "")?.value

                        val isCurrentNightly = currentVersion.contains("nightly", ignoreCase = true)

                        if (!isCurrentNightly) {
                            // User is currently on stable release and switched to Nightly
                            isUpdateAvailable = true
                            latestVersion = releaseName.ifBlank { "Nightly" }
                        } else if (serverCommit != null) {
                            // Both are nightly builds -> compare commit hashes
                            if (!currentVersion.contains(serverCommit, ignoreCase = true)) {
                                isUpdateAvailable = true
                                latestVersion = releaseName.ifBlank { "Nightly ($serverCommit)" }
                            } else {
                                isUpdateAvailable = false
                                latestVersion = currentVersion
                            }
                        } else {
                            // Fallback: compare release name with current version
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

                    updatePrefs.edit()
                        .putLong(KEY_LAST_CHECK, now)
                        .putBoolean(KEY_LAST_CHECK_SUCCESS, true)
                        .apply()

                    Logger.d("UpdateManager") {
                        "Checked ($apiUrl): current=$currentVersion, available=$isUpdateAvailable, latest=$latestVersion"
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

   private fun isNewer(server: String, current: String): Boolean {
    // 1. Убираем "v" в начале, если есть, и приводим к нижнему регистру
    val sClean = server.removePrefix("v").lowercase()
    val cClean = current.removePrefix("v").lowercase()
    
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
        openReleasePage(context)
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