package com.nxd1frnt.clockdesk2.music.plugins

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ClockDeskMediaService
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.utils.Logger

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SystemSessionPlugin(private val context: Context) : IMusicPlugin {
    override val id = "system_media"
    override val displayName = context.getString(R.string.system_media_plugin_name)
    override val description = context.getString(R.string.system_media_plugin_description)
    override val settingsFragmentClass: Class<out Fragment>? = null

    private var callback: ((PluginState) -> Unit)? = null
    private var isEnabled = true
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, ClockDeskMediaService::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        processControllers(controllers)
    }

    private val callbackMap = mutableMapOf<MediaController, MediaController.Callback>()

    override fun init() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("enable_$id", true)

        if (isEnabled) {
            startSessionMonitoring()
        }

        prefs.registerOnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "enable_$id") {
                isEnabled = sharedPrefs.getBoolean(key, true)
                if (isEnabled) startSessionMonitoring() else stopSessionMonitoring()
            }
        }
    }

    private fun startSessionMonitoring() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            processControllers(controllers)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
        } catch (e: SecurityException) {
            callback?.invoke(PluginState.Disabled)
        } catch (e: Exception) {
            Logger.e("SystemMediaPlugin"){"Error starting monitoring"}
        }
    }

    private fun stopSessionMonitoring() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            callbackMap.keys.forEach { it.unregisterCallback(callbackMap[it]!!) }
            callbackMap.clear()
            callback?.invoke(PluginState.Disabled)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun processControllers(controllers: List<MediaController>?) {
        val currentControllers = controllers ?: emptyList()

        val toRemove = callbackMap.keys - currentControllers.toSet()
        toRemove.forEach {
            try { it.unregisterCallback(callbackMap[it]!!) } catch (e: Exception) {}
            callbackMap.remove(it)
        }

        currentControllers.forEach { controller ->
            if (!callbackMap.containsKey(controller)) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        evaluateOverallState()
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        evaluateOverallState()
                    }
                    override fun onSessionDestroyed() {
                        try {
                            val updatedControllers = mediaSessionManager.getActiveSessions(componentName)
                            processControllers(updatedControllers)
                        } catch (e: Exception) {
                            evaluateOverallState()
                        }
                    }
                }
                controller.registerCallback(cb)
                callbackMap[controller] = cb
            }
        }

        evaluateOverallState()
    }

    private fun evaluateOverallState() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)

            val playingController = controllers.firstOrNull {
                val state = it.playbackState?.state ?: PlaybackState.STATE_NONE
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }

            if (playingController != null) {
                updateStateFromController(playingController)
            } else {
                callback?.invoke(PluginState.Idle)
            }
        } catch (e: Exception) {
            callback?.invoke(PluginState.Idle)
        }
    }

    private fun updateStateFromController(controller: MediaController) {
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                playbackState?.state == PlaybackState.STATE_BUFFERING

        if (isPlaying) {
            val meta = controller.metadata

            val (bitmap, artUri) = extractArtwork(controller, meta)

            val displayIcon = extractDisplayIcon(controller, meta)

            Logger.d("SystemMediaPlugin"){"Update: ${controller.packageName}, hasBitmap=${bitmap != null}, uri=$artUri"}

            val track = MusicTrack(
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                artworkBitmap = bitmap,
                artworkUrl = artUri,
                sourcePackageName = controller.packageName,
                sourceIconBitmap = displayIcon
            )
            callback?.invoke(PluginState.Playing(track))
        } else {
            callback?.invoke(PluginState.Idle)
        }
    }

    private fun extractArtwork(controller: MediaController, meta: MediaMetadata?): Pair<Bitmap?, String?> {
        var bitmap: Bitmap? = null
        var artUri: String? = null

        // 1. Try Metadata Bitmap keys
        val bitmapKeys = arrayOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )

        for (key in bitmapKeys) {
            val b = runCatching { meta?.getBitmap(key) }.getOrNull()
            if (b != null && !b.isRecycled && b.width > 0 && b.height > 0) {
                Logger.d("SystemMediaPlugin") { "Extracted artwork bitmap from metadata key '$key' for ${controller.packageName}" }
                bitmap = ClockDeskMediaService.downscaleIfNeeded(b)
                break
            }
        }

        // 2. Try MediaDescription iconBitmap if metadata bitmap is still null
        if (bitmap == null) {
            val descBitmap = runCatching { meta?.description?.iconBitmap }.getOrNull()
            if (descBitmap != null && !descBitmap.isRecycled && descBitmap.width > 0 && descBitmap.height > 0) {
                Logger.d("SystemMediaPlugin") { "Extracted artwork bitmap from MediaDescription iconBitmap for ${controller.packageName}" }
                bitmap = ClockDeskMediaService.downscaleIfNeeded(descBitmap)
            }
        }

        // 3. Extract Uri strings from Metadata / MediaDescription
        val uriKeys = arrayOf(
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        )

        for (key in uriKeys) {
            val uriStr = runCatching { meta?.getString(key) }.getOrNull()
            if (!uriStr.isNullOrBlank()) {
                artUri = uriStr
                Logger.d("SystemMediaPlugin") { "Found artwork URI '$artUri' from key '$key' for ${controller.packageName}" }
                break
            }
        }

        if (artUri.isNullOrBlank()) {
            val descUriStr = runCatching { meta?.description?.iconUri?.toString() }.getOrNull()
            if (!descUriStr.isNullOrBlank()) {
                artUri = descUriStr
                Logger.d("SystemMediaPlugin") { "Found artwork URI '$artUri' from MediaDescription for ${controller.packageName}" }
            }
        }

        // 4. If artwork bitmap is missing, but artUri is local (content:// or file://), try resolving via ContentResolver
        if (bitmap == null && !artUri.isNullOrBlank()) {
            Logger.d("SystemMediaPlugin") { "Attempting to resolve local artwork URI '$artUri' for ${controller.packageName}" }
            val resolvedBitmap = loadBitmapFromUri(artUri)
            if (resolvedBitmap != null && !resolvedBitmap.isRecycled) {
                Logger.d("SystemMediaPlugin") { "Successfully resolved artwork bitmap from URI '$artUri' for ${controller.packageName}" }
                bitmap = resolvedBitmap
            } else {
                Logger.w("SystemMediaPlugin") { "Could not decode bitmap from URI '$artUri' for ${controller.packageName}" }
            }
        }

        // 5. If artwork bitmap is still missing, fallback to Notification artwork from ClockDeskMediaService
        if (bitmap == null) {
            Logger.d("SystemMediaPlugin") { "Attempting notification artwork fallback for ${controller.packageName}" }
            val notifBitmap = ClockDeskMediaService.getMediaNotificationArtwork(controller.packageName, context)
            if (notifBitmap != null && !notifBitmap.isRecycled && notifBitmap.width > 0 && notifBitmap.height > 0) {
                Logger.d("SystemMediaPlugin") { "Notification artwork fallback succeeded for ${controller.packageName}" }
                bitmap = notifBitmap
            } else {
                Logger.d("SystemMediaPlugin") { "Notification artwork fallback returned null for ${controller.packageName}" }
            }
        }

        return Pair(bitmap, artUri)
    }

    private fun extractDisplayIcon(controller: MediaController, meta: MediaMetadata?): Bitmap? {
        val notifIcon = ClockDeskMediaService.getMediaIconBitmap(controller.packageName, context)
        if (notifIcon != null && !notifIcon.isRecycled) return notifIcon

        val metaIcon = runCatching { meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }.getOrNull()
            ?: runCatching { meta?.description?.iconBitmap }.getOrNull()

        return if (metaIcon != null && !metaIcon.isRecycled) {
            ClockDeskMediaService.downscaleIfNeeded(metaIcon, 256)
        } else null
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val scheme = uri.scheme
            if (scheme == "content" || scheme == "file") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    var sampleSize = 1
                    if (maxDim > 1024) {
                        sampleSize = maxDim / 1024
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize.coerceAtLeast(1)
                    }

                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (decoded != null && !decoded.isRecycled && decoded.width > 0 && decoded.height > 0) {
                        ClockDeskMediaService.downscaleIfNeeded(decoded)
                    } else null
                }
            } else null
        }.getOrNull()
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }

    override fun destroy() {
        stopSessionMonitoring()
    }
}