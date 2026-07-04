package com.nxd1frnt.clockdesk2.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.session.MediaController
import android.media.MediaMetadata
import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.music.ClockDeskMediaService
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import com.nxd1frnt.clockdesk2.utils.Logger
import dalvik.system.DexClassLoader
import com.dokar.quickjs.*
import com.dokar.quickjs.binding.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.util.zip.ZipInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream

class DashboardManager(
    private val context: Context,
    private val onTilesLoaded: (List<DashboardTile>) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient = OkHttpClient()
    private val activePlugins = mutableMapOf<String, ActivePlugin>()
    private val allTiles = mutableListOf<DashboardTile>()
    
    var adapter: DashboardAdapter? = null

    private class ActivePlugin(
        val id: String,
        val quickJs: QuickJs
    )

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateMediaProgress()
            mainHandler.postDelayed(this, 1000)
        }
    }

    init {
        loadPluginsFromFilesDir()
        loadNativeApkPlugins()
        mainHandler.post(progressRunnable)
    }

    private fun copyAssetFolder(srcName: String, destDir: File) {
        try {
            val files = context.assets.list(srcName) ?: return
            if (files.isEmpty()) {
                copyAssetFile(srcName, destDir)
            } else {
                destDir.mkdirs()
                for (file in files) {
                    val srcFile = if (srcName.isEmpty()) file else "$srcName/$file"
                    val destFile = File(destDir, file)
                    copyAssetFolder(srcFile, destFile)
                }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to copy asset folder $srcName: ${e.message}" }
        }
    }

    private fun copyAssetFile(srcFile: String, destFile: File) {
        try {
            destFile.parentFile?.mkdirs()
            context.assets.open(srcFile).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to copy asset file $srcFile: ${e.message}" }
        }
    }

    private fun loadPluginsFromFilesDir() {
        val pluginsDir = File(context.filesDir, "plugins")
        if (!pluginsDir.exists() || pluginsDir.list()?.isEmpty() == true) {
            copyAssetFolder("plugins", pluginsDir)
        }

        // Force overwrite the built-in plugins' logic.js with assets version to ensure up-to-date javascript logic
        try {
            val builtInPlugins = listOf("music", "weather")
            for (p in builtInPlugins) {
                val destDir = File(pluginsDir, p)
                if (destDir.exists()) {
                    val logicAsset = context.assets.open("plugins/$p/logic.js").bufferedReader().use { it.readText() }
                    File(destDir, "logic.js").writeText(logicAsset)
                }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed updating built-in plugins logic on startup: ${e.message}" }
        }

        try {
            val pluginDirs = pluginsDir.listFiles { file -> file.isDirectory } ?: return
            for (dirFile in pluginDirs) {
                val dir = dirFile.name
                if (dir.startsWith(".")) continue
                
                try {
                    val layoutFile = File(dirFile, "layout.json")
                    val logicFile = File(dirFile, "logic.js")
                    if (!layoutFile.exists() || !logicFile.exists()) continue
                    
                    val layoutStr = layoutFile.readText()
                    val logicJs = logicFile.readText()
                    
                    // Parse layout.json
                    val layoutObj = JSONObject(layoutStr)
                    val tilesArray = layoutObj.optJSONArray("tiles") ?: continue
                    
                    val pluginTiles = mutableListOf<DashboardTile>()
                    for (i in 0 until tilesArray.length()) {
                        val tileObj = tilesArray.getJSONObject(i)
                        val id = tileObj.getString("id")
                        val type = tileObj.getString("type")
                        
                        val tile = DashboardTile(
                            id = id,
                            type = type,
                            pluginId = dir
                        )
                        
                        // Populate default values from json
                        val updates = mutableMapOf<String, Any?>()
                        tileObj.keys().forEach { key ->
                            updates[key] = tileObj.get(key)
                        }
                        tile.updateWith(updates)
                        pluginTiles.add(tile)
                    }

                    allTiles.addAll(pluginTiles)

                    // 3. Initialize QuickJS
                    val quickJs = QuickJs.create(kotlinx.coroutines.Dispatchers.Default)
                    val activePlugin = ActivePlugin(dir, quickJs)
                    activePlugins[dir] = activePlugin
                    
                    // Expose bindings to JS
                    setupJsBridge(dir, quickJs)
                    
                    // Load and run logic.js
                    runBlocking { quickJs.evaluate<Any?>(logicJs) }
                    
                    // Call init() if defined
                    try {
                        runBlocking { quickJs.evaluate<Any?>("if (typeof init === 'function') init();") }
                    } catch (e: Exception) {
                        Logger.e("DashboardManager") { "Failed to call init() in plugin $dir: ${e.message}" }
                    }

                    // Push initial weather state if it's the weather plugin
                    if (dir == "weather") {
                        pushWeatherState()
                    }

                } catch (e: Exception) {
                    Logger.e("DashboardManager") { "Error loading plugin '$dir': ${e.message}" }
                }
            }
            sortTilesBySavedOrder()
            onTilesLoaded(allTiles)
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed listing filesDir plugins: ${e.message}" }
        }
    }

    fun createOrUpdatePlugin(pluginId: String, layoutJson: String, logicJs: String) {
        try {
            val pluginDir = File(context.filesDir, "plugins/$pluginId")
            pluginDir.mkdirs()
            File(pluginDir, "layout.json").writeText(layoutJson)
            File(pluginDir, "logic.js").writeText(logicJs)
            reload()
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to create/update plugin $pluginId: ${e.message}" }
        }
    }

    fun deletePlugin(pluginId: String) {
        try {
            val pluginDir = File(context.filesDir, "plugins/$pluginId")
            if (pluginDir.exists()) {
                pluginDir.deleteRecursively()
            }
            reload()
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to delete plugin $pluginId: ${e.message}" }
        }
    }

    fun restoreDefaultPlugins() {
        try {
            val pluginsDir = File(context.filesDir, "plugins")
            if (pluginsDir.exists()) {
                pluginsDir.deleteRecursively()
            }
            reload()
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to restore default plugins: ${e.message}" }
        }
    }

    fun importPluginFromZip(zipUri: android.net.Uri, zipFileName: String): Boolean {
        val tempDir = File(context.cacheDir, "temp_unzip_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val tempZipFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.zip")
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tempZipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile))).use { zis ->
                var entry = zis.nextEntry
                val targetCanonical = tempDir.canonicalPath
                while (entry != null) {
                    val entryName = entry.name
                    if (!entryName.contains("__MACOSX") && !entryName.startsWith(".")) {
                        val file = File(tempDir, entryName)
                        if (!file.canonicalPath.startsWith(targetCanonical)) {
                            throw SecurityException("Zip slip validation failed")
                        }
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            
            tempZipFile.delete()
            
            var rootDir = tempDir
            val files = tempDir.listFiles() ?: return false
            val visibleFiles = files.filter { !it.name.startsWith(".") }
            if (visibleFiles.size == 1 && visibleFiles[0].isDirectory) {
                rootDir = visibleFiles[0]
            }
            
            val layoutFile = File(rootDir, "layout.json")
            val logicFile = File(rootDir, "logic.js")
            if (!layoutFile.exists() || !logicFile.exists()) {
                tempDir.deleteRecursively()
                return false
            }
            
            val pluginId = if (rootDir != tempDir) {
                rootDir.name
            } else {
                zipFileName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_]"), "_")
            }
            
            val finalDest = File(context.filesDir, "plugins/$pluginId")
            if (finalDest.exists()) {
                finalDest.deleteRecursively()
            }
            finalDest.mkdirs()
            
            rootDir.copyRecursively(finalDest, overwrite = true)
            tempDir.deleteRecursively()
            reload()
            return true
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed importing zip: ${e.message}" }
            tempDir.deleteRecursively()
            return false
        }
    }

    fun addBuiltInWidget(folderName: String): Boolean {
        val destDir = File(context.filesDir, "plugins/$folderName")
        try {
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            copyAssetFolder("plugins/$folderName", destDir)
            reload()
            return true
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to add built-in widget $folderName: ${e.message}" }
            return false
        }
    }

    private fun sortTilesBySavedOrder() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val orderStr = prefs.getString("dashboard_tiles_order", null)
        if (!orderStr.isNullOrEmpty()) {
            try {
                val orderArray = JSONArray(orderStr)
                val orderMap = mutableMapOf<String, Int>()
                for (i in 0 until orderArray.length()) {
                    orderMap[orderArray.getString(i)] = i
                }
                allTiles.sortWith(Comparator { t1, t2 ->
                    val idx1 = orderMap[t1.id] ?: Int.MAX_VALUE
                    val idx2 = orderMap[t2.id] ?: Int.MAX_VALUE
                    idx1.compareTo(idx2)
                })
            } catch (e: Exception) {
                Logger.e("DashboardManager") { "Failed sorting tiles: ${e.message}" }
            }
        }
    }

    fun reload() {
        activePlugins.values.forEach {
            try {
                runBlocking { it.quickJs.close() }
            } catch (e: Exception) { /* ignore */ }
        }
        activePlugins.clear()
        allTiles.clear()
        
        loadPluginsFromFilesDir()
        loadNativeApkPlugins()
    }

    private fun setupJsBridge(pluginId: String, quickJs: QuickJs) {
        // Expose a raw bridge class
        quickJs.define("ClockDeskBridge") {
            function("updateState") { args ->
                val jsonStr = (if (args.size > 0) args[0] else null) as? String ?: return@function
                updateTileState(pluginId, jsonStr)
            }
            
            function("controlMedia") { args ->
                val action = (if (args.size > 0) args[0] else null) as? String ?: return@function
                controlMedia(action)
            }

            function("getWeatherData") { args ->
                getWeatherDataJson() ?: ""
            }

            function("fetchAsync") { args ->
                val url = (if (args.size > 0) args[0] else null) as? String ?: return@function
                val optionsStr = (if (args.size > 1) args[1] else null) as? String ?: "{}"
                val reqId = (if (args.size > 2) args[2] else null) as? String ?: return@function
                performOkHttpCall(pluginId, url, optionsStr, reqId)
            }

            function("setTimeoutHelper") { args ->
                val id = (if (args.size > 0) args[0] else null) as? String ?: return@function
                val delay = ((if (args.size > 1) args[1] else null) as? Number)?.toLong() ?: 0L
                mainHandler.postDelayed({
                    try {
                        runBlocking { quickJs.evaluate<Any?>("if (globalThis._timeouts['$id']) { globalThis._timeouts['$id'](); delete globalThis._timeouts['$id']; }") }
                    } catch (e: Exception) {}
                }, delay)
            }

            function("setIntervalHelper") { args ->
                val id = (if (args.size > 0) args[0] else null) as? String ?: return@function
                val delay = ((if (args.size > 1) args[1] else null) as? Number)?.toLong() ?: 0L
                val runnable = object : Runnable {
                    override fun run() {
                        try {
                            runBlocking { quickJs.evaluate<Any?>("if (globalThis._intervals['$id']) globalThis._intervals['$id']();") }
                            mainHandler.postDelayed(this, delay)
                        } catch (e: Exception) {}
                    }
                }
                mainHandler.postDelayed(runnable, delay)
            }
        }

        // Define premium ClockDesk wrapper APIs
        val wrapperScript = """
            const ClockDesk = {
                updateState: function(state) {
                    ClockDeskBridge.updateState(JSON.stringify(state));
                },
                controlMedia: function(action) {
                    ClockDeskBridge.controlMedia(action);
                },
                getWeatherData: function() {
                    return ClockDeskBridge.getWeatherData();
                },
                fetch: function(url, options) {
                    return new Promise((resolve, reject) => {
                        const reqId = Math.random().toString(36).substring(2);
                        if (!globalThis._fetchResolvers) {
                            globalThis._fetchResolvers = {};
                        }
                        globalThis._fetchResolvers[reqId] = { resolve, reject };
                        ClockDeskBridge.fetchAsync(url, JSON.stringify(options || {}), reqId);
                    });
                }
            };

            globalThis.setTimeout = function(fn, delay) {
                const id = Math.random().toString();
                if (!globalThis._timeouts) globalThis._timeouts = {};
                globalThis._timeouts[id] = fn;
                ClockDeskBridge.setTimeoutHelper(id, delay);
                return id;
            };

            globalThis.setInterval = function(fn, delay) {
                const id = Math.random().toString();
                if (!globalThis._intervals) globalThis._intervals = {};
                globalThis._intervals[id] = fn;
                ClockDeskBridge.setIntervalHelper(id, delay);
                return id;
            };
        """.trimIndent()
        
        runBlocking { quickJs.evaluate<Any?>(wrapperScript) }
    }

    private fun updateTileState(pluginId: String, jsonStr: String) {
        mainHandler.post {
            try {
                val jsonObject = JSONObject(jsonStr)
                var hasChanges = false
                jsonObject.keys().forEach { tileId ->
                    val tileUpdates = jsonObject.optJSONObject(tileId) ?: return@forEach
                    val updateMap = mutableMapOf<String, Any?>()
                    tileUpdates.keys().forEach { key ->
                        val value = tileUpdates.get(key)
                        if (value == JSONObject.NULL) {
                            updateMap[key] = null
                        } else if (value is JSONArray) {
                            updateMap[key] = jsonArrayToList(value)
                        } else if (value is JSONObject) {
                            updateMap[key] = jsonObjectToMap(value)
                        } else {
                            updateMap[key] = value
                        }
                    }

                    val tile = allTiles.firstOrNull { it.id == tileId && it.pluginId == pluginId }
                    if (tile != null) {
                        tile.updateWith(updateMap)
                        hasChanges = true
                    }
                }
                if (hasChanges) {
                    adapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Logger.e("DashboardManager") { "Failed parsing state update: ${e.message}" }
            }
        }
    }

    private fun jsonArrayToList(array: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            val item = array.get(i)
            if (item is JSONObject) {
                list.add(jsonObjectToMap(item))
            } else if (item is JSONArray) {
                list.add(jsonArrayToList(item))
            } else if (item != JSONObject.NULL) {
                list.add(item)
            }
        }
        return list
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { key ->
            val value = obj.get(key)
            if (value == JSONObject.NULL) {
                map[key] = null
            } else if (value is JSONArray) {
                map[key] = jsonArrayToList(value)
            } else if (value is JSONObject) {
                map[key] = jsonObjectToMap(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun performOkHttpCall(pluginId: String, url: String, optionsStr: String, reqId: String) {
        val requestBuilder = Request.Builder().url(url)
        try {
            val options = JSONObject(optionsStr)
            val method = options.optString("method", "GET").uppercase()
            val headers = options.optJSONObject("headers")
            val body = options.optString("body", null)

            headers?.keys()?.forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }

            if (method != "GET") {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = RequestBody.create(mediaType, body ?: "")
                requestBuilder.method(method, requestBody)
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Error parsing request options: ${e.message}" }
        }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = e.message ?: "Network request failed"
                mainHandler.post {
                    resolveFetch(pluginId, reqId, errorMsg, isError = true)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                mainHandler.post {
                    resolveFetch(pluginId, reqId, body, isError = false)
                }
            }
        })
    }

    private fun resolveFetch(pluginId: String, reqId: String, data: String, isError: Boolean) {
        val plugin = activePlugins[pluginId] ?: return
        val escapedData = JSONObject.quote(data)
        val script = if (isError) {
            """
            (function() {
                const resolver = globalThis._fetchResolvers['$reqId'];
                if (resolver) {
                    delete globalThis._fetchResolvers['$reqId'];
                    resolver.reject(new Error($escapedData));
                }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                const resolver = globalThis._fetchResolvers['$reqId'];
                if (resolver) {
                    delete globalThis._fetchResolvers['$reqId'];
                    resolver.resolve($escapedData);
                }
            })();
            """.trimIndent()
        }
        
        try {
            runBlocking { plugin.quickJs.evaluate<Any?>(script) }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed evaluating fetch resolve: ${e.message}" }
        }
    }

    // --- Tile interaction execution ---

    fun handleTileClick(tile: DashboardTile) {
        val action = tile.action ?: return
        evaluateJs(tile.pluginId, action)
    }

    fun handleToggleChange(tile: DashboardTile, state: Boolean) {
        val action = tile.action ?: return
        val resolvedAction = action.replace("state", state.toString())
        evaluateJs(tile.pluginId, resolvedAction)
    }

    fun handleSliderChange(tile: DashboardTile, value: Float) {
        val action = tile.action ?: return
        val resolvedAction = action.replace("value", value.toString())
        evaluateJs(tile.pluginId, resolvedAction)
    }

    fun evaluateJs(pluginId: String, script: String) {
        val plugin = activePlugins[pluginId] ?: return
        try {
            runBlocking { plugin.quickJs.evaluate<Any?>(script) }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed evaluating action script: ${e.message}" }
        }
    }

    // --- Pushing music states into JS ---

    fun pushMusicState(state: PluginState) {
        // Find if there is a music plugin running
        val musicPlugin = activePlugins["music"] ?: return
        
        val songObj = JSONObject()
        if (state is PluginState.Playing) {
            val track = state.track
            songObj.put("trackTitle", track.title)
            songObj.put("trackArtist", track.artist)
            songObj.put("isPlaying", true)
            songObj.put("artworkUrl", track.artworkUrl ?: "")
            // Note: bitmap cannot be passed directly to JS, so we keep it in Kotlin extraData
            // But we push the text data to JS
        } else {
            songObj.put("trackTitle", "No Media Playing")
            songObj.put("trackArtist", "")
            songObj.put("isPlaying", false)
        }

        // Push state to JS and let JS trigger updateState
        val script = "if (typeof onMusicStateChanged === 'function') onMusicStateChanged(${songObj.toString()});"
        try {
            runBlocking { musicPlugin.quickJs.evaluate<Any?>(script) }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to call onMusicStateChanged: ${e.message}" }
        }

        // Keep bitmap in our adapter's tile directly
        val musicTile = allTiles.firstOrNull { it.id == "music_player" && it.pluginId == "music" }
        if (musicTile != null) {
            val currentExtra = musicTile.extraData?.toMutableMap() ?: mutableMapOf()
            if (state is PluginState.Playing) {
                val track = state.track
                currentExtra["trackTitle"] = track.title
                currentExtra["trackArtist"] = track.artist
                currentExtra["isPlaying"] = true
                currentExtra["artworkBitmap"] = track.artworkBitmap
                currentExtra["artworkUrl"] = track.artworkUrl
                currentExtra["sourcePackageName"] = track.sourcePackageName
                currentExtra["sourceIconBitmap"] = track.sourceIconBitmap
            } else {
                currentExtra["trackTitle"] = "No Media Playing"
                currentExtra["trackArtist"] = ""
                currentExtra["isPlaying"] = false
                currentExtra["artworkBitmap"] = null
                currentExtra["artworkUrl"] = null
                currentExtra["sourcePackageName"] = null
                currentExtra["sourceIconBitmap"] = null
            }
            musicTile.extraData = currentExtra
            mainHandler.post { adapter?.notifyDataSetChanged() }
        }
    }

    // --- Semantic / group scenario controls ---

    fun triggerMasterScenario(category: String, actionType: String) {
        for (tile in allTiles) {
            if (tile.deviceCategory == category && tile.deviceAction == actionType) {
                val actionCode = tile.action ?: continue
                // Resolve standard replacements if any
                val resolvedCode = if (actionType == "TURN_OFF") {
                    actionCode.replace("state", "false").replace("value", "0")
                } else {
                    actionCode
                }
                evaluateJs(tile.pluginId, resolvedCode)
            }
        }
    }

    private fun controlMedia(action: String) {
        try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(context, ClockDeskMediaService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val controller = controllers.firstOrNull()
            if (controller != null) {
                if (action.startsWith("seek:")) {
                    val pos = action.substringAfter("seek:").toLongOrNull()
                    if (pos != null) {
                        controller.transportControls.seekTo(pos)
                    }
                    return
                }
                when (action) {
                    "play" -> controller.transportControls.play()
                    "pause" -> controller.transportControls.pause()
                    "playPause" -> {
                        val state = controller.playbackState?.state
                        if (state == PlaybackState.STATE_PLAYING) {
                            controller.transportControls.pause()
                        } else {
                            controller.transportControls.play()
                        }
                    }
                    "next" -> controller.transportControls.skipToNext()
                    "previous" -> controller.transportControls.skipToPrevious()
                }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed controlling media session: ${e.message}" }
        }
    }

    // --- NATIVE_APK dynamically loaded plugins ---

    private fun loadNativeApkPlugins() {
        val pm = context.packageManager
        val intent = Intent("com.nxd1frnt.clockdesk2.action.DASHBOARD_PLUGIN")
        val resolveInfos = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)

        for (resolveInfo in resolveInfos) {
            val serviceInfo = resolveInfo.serviceInfo ?: continue
            val packageName = serviceInfo.packageName
            val metaData = serviceInfo.metaData ?: continue
            val className = metaData.getString("plugin_class") ?: continue

            if (!verifySignature(context, packageName)) {
                Logger.w("DashboardManager") { "Plugin package signature mismatch! Skipping: $packageName" }
                continue
            }

            try {
                val pluginContext = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
                val dexPath = pluginContext.packageCodePath
                val optimizedDir = context.cacheDir.absolutePath
                val classLoader = DexClassLoader(dexPath, optimizedDir, null, context.classLoader)

                val clazz = classLoader.loadClass(className)
                val pluginInstance = clazz.getDeclaredConstructor().newInstance() as NativeDashboardPlugin

                mainHandler.post {
                    try {
                        val nativeView = pluginInstance.createWidgetView(pluginContext)
                        val tile = DashboardTile(
                            id = "native_${packageName}",
                            type = "NATIVE_APK",
                            pluginId = "native_${packageName}",
                            span = 4,
                            nativeView = nativeView
                        )
                        allTiles.add(tile)
                        sortTilesBySavedOrder()
                        adapter?.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Logger.e("DashboardManager") { "Failed generating native view for $packageName: ${e.message}" }
                    }
                }
            } catch (e: Exception) {
                Logger.e("DashboardManager") { "Failed loading native plugin $packageName: ${e.message}" }
            }
        }
    }

    private fun verifySignature(context: Context, packageName: String): Boolean {
        try {
            if (packageName == context.packageName) return true
            val pm = context.packageManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo ?: return false
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                
                val hostPackageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val hostSigningInfo = hostPackageInfo.signingInfo ?: return false
                val hostSignatures = if (hostSigningInfo.hasMultipleSigners()) {
                    hostSigningInfo.apkContentsSigners
                } else {
                    hostSigningInfo.signingCertificateHistory
                }
                
                return signatures.any { sig -> hostSignatures.any { hostSig -> sig.toCharsString() == hostSig.toCharsString() } }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures ?: return false
                
                @Suppress("DEPRECATION")
                val hostPackageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val hostSignatures = hostPackageInfo.signatures ?: return false
                
                return signatures.any { sig -> hostSignatures.any { hostSig -> sig.toCharsString() == hostSig.toCharsString() } }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed signature check for $packageName: ${e.message}" }
            return false
        }
    }

    // --- Helper to read assets ---

    private fun readAssetFile(path: String): String? {
        return try {
            context.assets.open(path).use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to read asset: $path" }
            null
        }
    }

    private fun updateMediaProgress() {
        try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(context, ClockDeskMediaService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val controller = controllers.firstOrNull()
            val musicTile = allTiles.firstOrNull { it.id == "music_player" && it.pluginId == "music" }
            
            if (controller != null && musicTile != null) {
                val playbackState = controller.playbackState
                val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                        playbackState?.state == PlaybackState.STATE_BUFFERING
                
                val currentPosition = playbackState?.position ?: 0L
                val metadata = controller.metadata
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                
                val currentExtra = musicTile.extraData?.toMutableMap() ?: mutableMapOf()
                currentExtra["progress"] = currentPosition
                currentExtra["maxProgress"] = if (duration > 0) duration else 100L
                currentExtra["isPlaying"] = isPlaying
                
                // Keep artwork and info updated if missing or mismatch
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                
                if (currentExtra["trackTitle"] != title || currentExtra["trackArtist"] != artist) {
                    currentExtra["trackTitle"] = title
                    currentExtra["trackArtist"] = artist
                    
                    val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    val displayIcon = ClockDeskMediaService.getMediaIconBitmap(controller.packageName, context)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    
                    currentExtra["artworkBitmap"] = bitmap
                    currentExtra["artworkUrl"] = artUri ?: ""
                    currentExtra["sourcePackageName"] = controller.packageName
                    currentExtra["sourceIconBitmap"] = displayIcon
                }
                
                musicTile.extraData = currentExtra
                adapter?.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            // Ignore session retrieval exceptions
        }
    }

    fun getWeatherDataJson(): String? {
        val tempVal = WeatherGetter.cachedTemperature ?: return null
        val codeVal = WeatherGetter.cachedWeatherCode ?: 0
        
        val root = JSONObject()
        root.put("temp", "${Math.round(tempVal)}°C")
        
        // Map current condition code to desc
        val description = when (codeVal) {
            0 -> "Clear sky"
            in 1..3 -> "Partly cloudy"
            in 4..48 -> "Foggy"
            in 49..67 -> "Rainy"
            in 68..77 -> "Snowy"
            else -> "Thunderstorm"
        }
        root.put("description", description)
        
        val forecastArray = JSONArray()
        val dailyCodes = WeatherGetter.cachedDailyCodes
        val dailyMaxTemps = WeatherGetter.cachedDailyMaxTemps
        
        val sdf = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        val calendar = java.util.Calendar.getInstance()
        
        val len = minOf(dailyCodes.size, dailyMaxTemps.size, 4)
        for (i in 0 until len) {
            val item = JSONObject()
            val cal = calendar.clone() as java.util.Calendar
            cal.add(java.util.Calendar.DAY_OF_YEAR, i)
            
            val dailyCode = dailyCodes[i]
            val dailyIcon = when (dailyCode) {
                0 -> "ic_clear_day"
                in 1..3 -> "ic_mostly_cloudy_day"
                in 4..48 -> "ic_fog"
                in 49..67 -> "ic_rain"
                in 68..77 -> "ic_snow"
                else -> "ic_thunderstorm"
            }
            
            item.put("day", sdf.format(cal.time))
            item.put("temp", "${Math.round(dailyMaxTemps[i])}°")
            item.put("icon", dailyIcon)
            forecastArray.put(item)
        }
        root.put("forecast", forecastArray)
        return root.toString()
    }

    fun pushWeatherState() {
        val weatherPlugin = activePlugins["weather"] ?: return
        val weatherDataJson = getWeatherDataJson() ?: return
        
        val script = "if (typeof onWeatherUpdated === 'function') onWeatherUpdated('$weatherDataJson');"
        try {
            runBlocking { weatherPlugin.quickJs.evaluate<Any?>(script) }
        } catch (e: Exception) {
            Logger.e("DashboardManager") { "Failed to call onWeatherUpdated: ${e.message}" }
        }
    }

    fun destroy() {
        mainHandler.removeCallbacks(progressRunnable)
        activePlugins.values.forEach {
            try {
                runBlocking { it.quickJs.close() }
            } catch (e: Exception) { /* ignore */ }
        }
        activePlugins.clear()
    }
}
