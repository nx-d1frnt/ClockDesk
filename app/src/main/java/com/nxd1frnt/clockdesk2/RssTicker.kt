package com.nxd1frnt.clockdesk2

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class RssTicker(private val textView: TextView, private val rssUrl: String) {

    private val handler = Handler(Looper.getMainLooper())
    private var rssItems = listOf<String>()
    private var currentItemIndex = 0

    // A Runnable that updates the text and reschedules itself.
    private val updateTickerTask = object : Runnable {
        override fun run() {
            if (rssItems.isNotEmpty()) {
                textView.text = rssItems[currentItemIndex]
                currentItemIndex = (currentItemIndex + 1) % rssItems.size
                handler.postDelayed(this, 5000)
            }
        }
    }

    fun start() {
        stop()
        fetchRssFeed()
    }

    fun stop() {
        handler.removeCallbacks(updateTickerTask)
    }

    private fun fetchRssFeed() {
        val queue = Volley.newRequestQueue(textView.context, TlsHurlStack())
        val stringRequest = StringRequest(
            Request.Method.GET, rssUrl,
            { response ->
                rssItems = parseRss(response)
                if (rssItems.isNotEmpty()) {
                    // Always cancel any previous tasks and reset index before starting.
                    handler.removeCallbacks(updateTickerTask)
                    currentItemIndex = 0
                    handler.post(updateTickerTask)
                }
            },
            { error ->
                val statusCode = error.networkResponse?.statusCode
                Log.e("RssTicker", "Failed to fetch RSS feed. Status: $statusCode, Error: ${error.toString()}")

                val errorMessage = when {
                    error is com.android.volley.NoConnectionError -> "Error: No internet connection"
                    error is com.android.volley.TimeoutError -> "Error: Connection timed out"
                    statusCode != null -> "Error: Server returned status $statusCode"
                    else -> "Error: Could not fetch RSS feed"
                }
                textView.text = errorMessage
            }
        )
        queue.add(stringRequest)
    }

    private fun parseRss(rssFeed: String): List<String> {
        val items = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(rssFeed))
            var eventType = xpp.eventType
            var text: String? = null
            var inItem = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = xpp.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            inItem = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        text = xpp.text
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            inItem = false
                        } else if (tagName.equals("title", ignoreCase = true) && inItem) {
                            text?.let { items.add(it) }
                        }
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            Log.e("RssTicker", "Error parsing RSS feed", e)
        }
        return items
    }
}
