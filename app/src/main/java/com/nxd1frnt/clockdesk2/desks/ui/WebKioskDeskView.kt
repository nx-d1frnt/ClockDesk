package com.nxd1frnt.clockdesk2.desks.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.DeskItem

class WebKioskDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView
    private val progressIndicator: LinearProgressIndicator
    private val emptyLayout: LinearLayout
    private val btnSetupUrl: MaterialButton
    private val btnBack: ImageView
    private val btnReload: ImageView
    private val btnSettings: ImageView

    private var currentDesk: DeskItem? = null
    private var onUpdatedCallback: ((DeskItem) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var configuredUrl: String? = null
    private var isPageActive = false
    private var disposed = false
    private var restoredScroll: Pair<Int, Int>? = null

    private val edgeMarginPx = (40 * resources.displayMetrics.density)

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_web_kiosk_layout, this, true)

        webView = findViewById(R.id.kiosk_web_view)
        progressIndicator = findViewById(R.id.kiosk_progress)
        emptyLayout = findViewById(R.id.kiosk_empty_layout)
        btnSetupUrl = findViewById(R.id.btn_kiosk_setup_url)
        btnBack = findViewById(R.id.btn_kiosk_back)
        btnReload = findViewById(R.id.btn_kiosk_reload)
        btnSettings = findViewById(R.id.btn_kiosk_settings)

        initWebView()

        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnReload.setOnClickListener {
            webView.reload()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnSetupUrl.setOnClickListener {
            showSettingsDialog()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressIndicator.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressIndicator.visibility = View.GONE
                restoredScroll?.let { (x, y) ->
                    restoredScroll = null
                    webView.post { if (!disposed) webView.scrollTo(x, y) }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                progressIndicator.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100) {
                    progressIndicator.visibility = View.GONE
                }
            }
        }

        // Edge swipe detection to allow horizontal page transitions to adjacent desks
        webView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val isNearEdge = event.x < edgeMarginPx || event.x > (v.width - edgeMarginPx)
                    if (isNearEdge) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Stay respectful of parent if near edge
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    fun bindDesk(desk: DeskItem, onUpdated: (DeskItem) -> Unit) {
        currentDesk = desk
        onUpdatedCallback = onUpdated

        val url = desk.customUrl.trim()
        if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
            emptyLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            if (configuredUrl != url) {
                restoredScroll = null
                webView.loadUrl(url)
            }
        } else {
            emptyLayout.visibility = View.VISIBLE
            webView.visibility = View.GONE
        }
        configuredUrl = url

        scheduleAutoRefresh()
    }

    private fun scheduleAutoRefresh() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
        if (!isPageActive || !isAttachedToWindow || disposed) return
        val minutes = currentDesk?.autoRefreshMinutes ?: 0
        if (minutes > 0) {
            val intervalMs = minutes * 60 * 1000L
            val runnable = object : Runnable {
                override fun run() {
                    webView.reload()
                    mainHandler.postDelayed(this, intervalMs)
                }
            }
            refreshRunnable = runnable
            mainHandler.postDelayed(runnable, intervalMs)
        }
    }

    private fun showSettingsDialog() {
        val desk = currentDesk ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_kiosk_settings, null, false)
        val editUrl = dialogView.findViewById<EditText>(R.id.edit_kiosk_url)
        val editInterval = dialogView.findViewById<EditText>(R.id.edit_kiosk_interval)

        editUrl.setText(desk.customUrl)
        editInterval.setText(if (desk.autoRefreshMinutes > 0) desk.autoRefreshMinutes.toString() else "0")

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.kiosk_url_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.apply) { _, _ ->
                var newUrl = editUrl.text.toString().trim()
                if (newUrl.isNotEmpty() && !newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    newUrl = "https://$newUrl"
                }
                val newInterval = (editInterval.text.toString().toIntOrNull() ?: 0).coerceIn(0, 1440)

                val updated = desk.copy(customUrl = newUrl, autoRefreshMinutes = newInterval)
                bindDesk(updated, onUpdatedCallback ?: {})
                onUpdatedCallback?.invoke(updated)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDetachedFromWindow() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        webView.onPause()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateActivity()
    }

    fun setPageActive(active: Boolean) {
        isPageActive = active
        updateActivity()
    }

    private fun updateActivity() {
        if (disposed) return
        if (isPageActive && isAttachedToWindow) webView.onResume() else webView.onPause()
        scheduleAutoRefresh()
    }

    fun savePageState(): Bundle = Bundle().apply {
        putString("configured_url", configuredUrl)
        putInt("scroll_x", webView.scrollX)
        putInt("scroll_y", webView.scrollY)
        webView.saveState(this)
    }

    fun restorePageState(state: Bundle) {
        restoredScroll = state.getInt("scroll_x") to state.getInt("scroll_y")
        if (webView.restoreState(state) != null) configuredUrl = state.getString("configured_url")
        else restoredScroll = null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        mainHandler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
        onUpdatedCallback = null
    }
}
