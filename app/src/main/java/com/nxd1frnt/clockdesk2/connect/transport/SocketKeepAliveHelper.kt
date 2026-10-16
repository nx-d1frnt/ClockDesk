package com.nxd1frnt.clockdesk2.connect.transport

import android.os.ParcelFileDescriptor
import android.system.Os
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.FileDescriptor
import java.net.Socket

object SocketKeepAliveHelper {
    // Linux TCP socket options (from linux/tcp.h)
    private const val SOL_TCP = 6 // IPPROTO_TCP
    private const val TCP_KEEPIDLE = 4 // Start keepalives after this many seconds of idle
    private const val TCP_KEEPINTVL = 5 // Send keepalive every this many seconds
    private const val TCP_KEEPCNT = 6 // Number of keepalives before closing connection
    private const val TCP_USER_TIMEOUT = 18 // Max time in ms unacknowledged data/keepalives can remain

    fun configureFastKeepAlive(socket: Socket) {
        try {
            socket.keepAlive = true
            socket.tcpNoDelay = true

            val fd = extractFileDescriptor(socket)
            if (fd != null && fd.valid()) {
                try {
                    Os.setsockoptInt(fd, SOL_TCP, TCP_KEEPIDLE, 5) // Probe after 5s idle
                } catch (e: Exception) {
                    Logger.d("SocketKeepAlive") { "TCP_KEEPIDLE failed: ${e.message}" }
                }
                try {
                    Os.setsockoptInt(fd, SOL_TCP, TCP_KEEPINTVL, 3) // Probe every 3s
                } catch (e: Exception) {
                    Logger.d("SocketKeepAlive") { "TCP_KEEPINTVL failed: ${e.message}" }
                }
                try {
                    Os.setsockoptInt(fd, SOL_TCP, TCP_KEEPCNT, 3) // 3 probes = ~14s total
                } catch (e: Exception) {
                    Logger.d("SocketKeepAlive") { "TCP_KEEPCNT failed: ${e.message}" }
                }
                try {
                    Os.setsockoptInt(fd, SOL_TCP, TCP_USER_TIMEOUT, 14000) // 14s timeout
                } catch (e: Exception) {
                    Logger.d("SocketKeepAlive") { "TCP_USER_TIMEOUT failed: ${e.message}" }
                }
                Logger.i("DeskConnect/Connection") { "Fast TCP keepalive configured (idle=5s, intvl=3s, cnt=3, timeout=14s) on ${socket.inetAddress}" }
            } else {
                Logger.w("DeskConnect/Connection") { "Could not obtain valid FileDescriptor for socket to ${socket.inetAddress}" }
            }
        } catch (e: Exception) {
            Logger.w("DeskConnect/Connection") { "Failed to configure socket keepalive: ${e.message}" }
        }
    }

    private fun extractFileDescriptor(socket: Socket): FileDescriptor? {
        return try {
            val method = Socket::class.java.getMethod("getFileDescriptor$")
            method.invoke(socket) as? FileDescriptor
        } catch (e: Exception) {
            try {
                val implField = Socket::class.java.getDeclaredField("impl").apply { isAccessible = true }
                val impl = implField.get(socket)
                val fdField = java.net.SocketImpl::class.java.getDeclaredField("fd").apply { isAccessible = true }
                fdField.get(impl) as? FileDescriptor
            } catch (e2: Exception) {
                try {
                    ParcelFileDescriptor.fromSocket(socket)?.fileDescriptor
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }
}
