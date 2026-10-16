package com.nxd1frnt.clockdesk2.connect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.bumptech.glide.Glide
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class DeskConnectArtCache private constructor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetchExecutor = Executors.newFixedThreadPool(2)

    // In-memory cache: up to 20MB of album art bitmaps
    private val memoryCache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val listeners = CopyOnWriteArrayList<(url: String, bitmap: Bitmap) -> Unit>()
    private val fetchingUrls = ConcurrentHashMap.newKeySet<String>()

    fun registerListener(listener: (url: String, bitmap: Bitmap) -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: (url: String, bitmap: Bitmap) -> Unit) {
        listeners.remove(listener)
    }

    fun get(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return memoryCache.get(url)
    }

    fun put(url: String, bitmap: Bitmap) {
        memoryCache.put(url, bitmap)
        notifyArtAvailable(url, bitmap)
    }

    fun put(url: String, bytes: ByteArray): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                memoryCache.put(url, bitmap)
                notifyArtAvailable(url, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Logger.e("DeskConnectArtCache") { "Failed to decode artwork bytes for $url: ${e.message}" }
            null
        }
    }

    fun fetchHttpArt(url: String) {
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) return
        if (memoryCache.get(url) != null) return
        if (!fetchingUrls.add(url)) return

        fetchExecutor.execute {
            try {
                Logger.d("DeskConnectArtCache") { "Fetching HTTP album art for: $url" }
                val bitmap = Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()

                if (bitmap != null) {
                    mainHandler.post {
                        put(url, bitmap)
                    }
                }
            } catch (e: Exception) {
                Logger.w("DeskConnectArtCache") { "Failed to fetch HTTP art for $url: ${e.message}" }
            } finally {
                fetchingUrls.remove(url)
            }
        }
    }

    private fun notifyArtAvailable(url: String, bitmap: Bitmap) {
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener(url, bitmap)
                } catch (e: Exception) {
                    Logger.e("DeskConnectArtCache") { "Error in art listener: ${e.message}" }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: DeskConnectArtCache? = null

        fun getInstance(context: Context): DeskConnectArtCache {
            return instance ?: synchronized(this) {
                instance ?: DeskConnectArtCache(context.applicationContext).also { instance = it }
            }
        }
    }
}
