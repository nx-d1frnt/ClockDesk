package com.nxd1frnt.clockdesk2.network

import android.content.Context
import android.os.Build
import com.android.volley.RequestQueue
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.Volley
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


object NetworkManager {

    /**
     * Returns a RequestQueue that is configured to accept all SSL certificates on Android versions
     * up to and including KitKat (API level 19). For later versions, it returns a standard RequestQueue.
     */
    fun getRequestQueue(context: Context): RequestQueue {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            createUnsafeQueue(context) // Accept all SSL certificates
        } else {
            Volley.newRequestQueue(context.applicationContext)
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

            return Volley.newRequestQueue(context.applicationContext, hurlStack)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Volley.newRequestQueue(context.applicationContext)
    }

    private fun defaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers[0] as X509TrustManager
    }
}