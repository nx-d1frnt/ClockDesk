package com.nxd1frnt.clockdesk2.network

import android.content.Context
import android.os.Build
import com.android.volley.RequestQueue
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.Volley
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkManager {

    @Volatile
    private var requestQueue: RequestQueue? = null

    /**
     * Returns a thread-safe singleton RequestQueue to prevent creating multiple queues
     * and spawning unnecessary dispatcher threads.
     */
    fun getRequestQueue(context: Context): RequestQueue {
        return requestQueue ?: synchronized(this) {
            requestQueue ?: createQueue(context.applicationContext).also {
                requestQueue = it
            }
        }
    }

    private fun createQueue(context: Context): RequestQueue {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            createUnsafeQueue(context)
        } else {
            Volley.newRequestQueue(context)
        }
    }

    private fun createUnsafeQueue(context: Context): RequestQueue {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val hurlStack = HurlStack(null, sslContext.socketFactory)

            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

            return Volley.newRequestQueue(context, hurlStack)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Volley.newRequestQueue(context)
    }
}