package com.nxd1frnt.clockdesk2

import android.os.Build
import android.util.Log
import com.android.volley.toolbox.HurlStack
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A [HurlStack] that forces TLSv1.2 on Android 4.4.
 */
class TlsHurlStack : HurlStack() {
    private val sslSocketFactory by lazy {
        try {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            Tls12SocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            Log.e("TlsHurlStack", "Failed to create Tls12SocketFactory", e)
            null
        }
    }

    override fun createConnection(url: java.net.URL): java.net.HttpURLConnection {
        val connection = super.createConnection(url)
        if (connection is HttpsURLConnection && sslSocketFactory != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            connection.sslSocketFactory = sslSocketFactory
        }
        return connection
    }
}

/**
 * An [SSLSocketFactory] that delegates to a given factory but enables TLSv1.2 on the created sockets.
 */
private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    private val TLS_V12_ONLY = arrayOf("TLSv1.2")

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return patch(delegate.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(address, port, localAddress, localPort))
    }

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) {
            s.enabledProtocols = TLS_V12_ONLY
        }
        return s
    }
}
