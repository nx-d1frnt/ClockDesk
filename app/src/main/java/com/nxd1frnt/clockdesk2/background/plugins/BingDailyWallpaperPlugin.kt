package com.nxd1frnt.clockdesk2.background.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.nxd1frnt.clockdesk2.background.BackgroundPluginContract
import com.nxd1frnt.clockdesk2.background.BackgroundPluginState
import com.nxd1frnt.clockdesk2.background.BackgroundProviderState
import com.nxd1frnt.clockdesk2.background.IBackgroundPlugin
import com.nxd1frnt.clockdesk2.network.NetworkClient
import com.nxd1frnt.clockdesk2.utils.Logger
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Bing Daily Wallpaper plugin - fetches the daily Bing wallpaper
 */
class BingDailyWallpaperPlugin(
    private val context: Context
) : IBackgroundPlugin {
    
    override val id: String = "bing_daily"
    override val displayName: String = "Bing Daily Wallpaper"
    override val description: String = "Automatically fetch and display the daily Bing homepage wallpaper"
    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null
    
    private var callback: ((BackgroundPluginState) -> Unit)? = null
    private var currentState: BackgroundProviderState? = null
    private var pluginScope: CoroutineScope? = null
    
    companion object {
        private const val BING_API_URL = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"
        private const val CACHE_FILE_NAME = "bing_wallpaper_cache.jpg"
    }
    
    override fun init() {
        pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        // Try to load cached wallpaper first
        loadCachedWallpaper()
        // Then fetch fresh wallpaper
        requestUpdate()
    }
    
    override fun destroy() {
        pluginScope?.cancel()
        pluginScope = null
        callback = null
    }
    
    override fun setCallback(callback: (BackgroundPluginState) -> Unit) {
        this.callback = callback
    }
    
    override fun requestUpdate() {
        pluginScope?.launch {
            try {
                callback?.invoke(BackgroundPluginState.Loading)
                
                val imageUrl = withContext(Dispatchers.IO) {
                    fetchBingWallpaperUrl()
                }
                
                if (imageUrl != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        downloadAndCacheImage(imageUrl)
                    }
                    
                    if (bitmap != null) {
                        val drawable = BitmapDrawable(context.resources, bitmap)
                        currentState = BackgroundProviderState(
                            id = id,
                            displayName = displayName,
                            previewDrawable = drawable,
                            data = bitmap,
                            isLoading = false
                        )
                        callback?.invoke(BackgroundPluginState.Ready(currentState!!))
                    } else {
                        val error = "Failed to download image"
                        currentState = currentState?.copy(isLoading = false, error = error)
                        callback?.invoke(BackgroundPluginState.Error(error))
                    }
                } else {
                    val error = "Failed to fetch wallpaper URL"
                    currentState = currentState?.copy(isLoading = false, error = error)
                    callback?.invoke(BackgroundPluginState.Error(error))
                }
            } catch (e: Exception) {
                Logger.e("BingDailyPlugin") { "Error fetching wallpaper: ${e.message}" }
                val error = "Error: ${e.message}"
                currentState = currentState?.copy(isLoading = false, error = error)
                callback?.invoke(BackgroundPluginState.Error(error))
            }
        }
    }
    
    override fun getCurrentState(): BackgroundProviderState? = currentState
    
    private fun loadCachedWallpaper() {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    currentState = BackgroundProviderState(
                        id = id,
                        displayName = displayName,
                        previewDrawable = drawable,
                        data = bitmap,
                        isLoading = false
                    )
                    callback?.invoke(BackgroundPluginState.Ready(currentState!!))
                }
            }
        } catch (e: Exception) {
            Logger.e("BingDailyPlugin") { "Error loading cached wallpaper: ${e.message}" }
        }
    }
    
    private suspend fun fetchBingWallpaperUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val response = suspendCoroutine<String?> { continuation ->
                NetworkClient.getInstance().getString(
                    BING_API_URL,
                    onSuccess = { result -> continuation.resume(result) },
                    onError = { error ->
                        Logger.e("BingDailyPlugin") { "Network error: ${error.message}" }
                        continuation.resume(null)
                    }
                )
            }
            
            if (response == null) return@withContext null
            
            // Parse JSON to extract image URL
            // Bing returns: {"images":[{"url":"/th?id=OHR.XXX_YYYY.jpg","...}]}
            val urlStart = response.indexOf("\"url\":\"/")
            if (urlStart != -1) {
                val urlEnd = response.indexOf("\"", urlStart + 7)
                if (urlEnd != -1) {
                    val relativeUrl = response.substring(urlStart + 7, urlEnd)
                    return@withContext "https://www.bing.com$relativeUrl"
                }
            }
            null
        } catch (e: Exception) {
            Logger.e("BingDailyPlugin") { "Error parsing Bing API response: ${e.message}" }
            null
        }
    }
    
    private suspend fun downloadAndCacheImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            val inputStream = URL(imageUrl).openStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // Cache the image
            cacheFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            
            bitmap
        } catch (e: Exception) {
            Logger.e("BingDailyPlugin") { "Error downloading image: ${e.message}" }
            null
        }
    }
}
