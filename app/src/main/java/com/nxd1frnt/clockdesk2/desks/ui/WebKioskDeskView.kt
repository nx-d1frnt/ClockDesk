package com.nxd1frnt.clockdesk2.desks.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
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
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        } else {
            emptyLayout.visibility = View.VISIBLE
            webView.visibility = View.GONE
        }

        scheduleAutoRefresh()
    }

    private fun scheduleAutoRefresh() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
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
                val newInterval = editInterval.text.toString().toIntOrNull() ?: 0

                desk.customUrl = newUrl
                desk.autoRefreshMinutes = newInterval

                onUpdatedCallback?.invoke(desk)
                bindDesk(desk, onUpdatedCallback ?: {})
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
    }
}
