package com.nxd1frnt.clockdesk2.connect.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Clean-room KDE Connect wire protocol packet representation.
 */
data class DeskConnectPacket(
    val id: Long = 0,
    val type: String,
    val body: JSONObject = JSONObject(),
    val payloadSize: Long = 0,
    val payloadTransferInfo: JSONObject? = null
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("id", id)
        root.put("type", type)
        root.put("body", body)
        if (payloadSize > 0) {
            root.put("payloadSize", payloadSize)
        }
        if (payloadTransferInfo != null) {
            root.put("payloadTransferInfo", payloadTransferInfo)
        }
        return root.toString()
    }

    companion object {
        const val TYPE_IDENTITY = "kdeconnect.identity"
        const val TYPE_PAIR = "kdeconnect.pair"
        const val TYPE_BATTERY = "kdeconnect.battery"
        const val TYPE_BATTERY_REQUEST = "kdeconnect.battery.request"
        const val TYPE_NOTIFICATION = "kdeconnect.notification"
        const val TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request"
        const val TYPE_TELEPHONY = "kdeconnect.telephony"
        const val TYPE_MPRIS = "kdeconnect.mpris"
        const val TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"
        const val TYPE_PING = "kdeconnect.ping"

        const val PROTOCOL_VERSION = 7

        val DEFAULT_INCOMING_CAPABILITIES = listOf(
            TYPE_PAIR,
            TYPE_BATTERY,
            TYPE_NOTIFICATION,
            TYPE_TELEPHONY,
            TYPE_MPRIS,
            TYPE_PING
        )

        val DEFAULT_OUTGOING_CAPABILITIES = listOf(
            TYPE_PAIR,
            TYPE_BATTERY_REQUEST,
            TYPE_NOTIFICATION_REQUEST,
            TYPE_MPRIS_REQUEST,
            TYPE_PING
        )

        fun fromJson(jsonStr: String): DeskConnectPacket? {
            return try {
                val json = JSONObject(jsonStr)
                val id = json.optLong("id", 0)
                val type = json.getString("type")
                val body = json.optJSONObject("body") ?: JSONObject()
                val payloadSize = json.optLong("payloadSize", 0)
                val payloadTransferInfo = json.optJSONObject("payloadTransferInfo")
                DeskConnectPacket(id, type, body, payloadSize, payloadTransferInfo)
            } catch (e: Exception) {
                null
            }
        }

        fun createFullIdentity(
            deviceId: String,
            deviceName: String,
            deviceType: String = "tablet",
            tcpPort: Int = 1716,
            incomingCapabilities: List<String> = DEFAULT_INCOMING_CAPABILITIES,
            outgoingCapabilities: List<String> = DEFAULT_OUTGOING_CAPABILITIES
        ): DeskConnectPacket {
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceType", deviceType)
                put("protocolVersion", PROTOCOL_VERSION)
                put("tcpPort", tcpPort)
                put("incomingCapabilities", JSONArray(incomingCapabilities))
                put("outgoingCapabilities", JSONArray(outgoingCapabilities))
            }
            return DeskConnectPacket(id = 0, type = TYPE_IDENTITY, body = body)
        }

        fun createPair(pair: Boolean): DeskConnectPacket {
            val body = JSONObject().apply {
                put("pair", pair)
                put("timestamp", System.currentTimeMillis() / 1000L)
            }
            return DeskConnectPacket(id = 0, type = TYPE_PAIR, body = body)
        }

        fun createPing(message: String = "Ping!"): DeskConnectPacket {
            val body = JSONObject().apply {
                put("message", message)
            }
            return DeskConnectPacket(id = 0, type = TYPE_PING, body = body)
        }

        fun createMprisPlayerListRequest(): DeskConnectPacket {
            val body = JSONObject().apply {
                put("requestPlayerList", true)
                put("supportAlbumArtPayload", true)
            }
            return DeskConnectPacket(id = 0, type = TYPE_MPRIS_REQUEST, body = body)
        }

        fun createMprisPlayerStatusRequest(player: String): DeskConnectPacket {
            val body = JSONObject().apply {
                put("player", player)
                put("requestNowPlaying", true)
                put("requestVolume", true)
                put("supportAlbumArtPayload", true)
            }
            return DeskConnectPacket(id = 0, type = TYPE_MPRIS_REQUEST, body = body)
        }

        fun createMprisAlbumArtRequest(player: String, albumArtUrl: String): DeskConnectPacket {
            val body = JSONObject().apply {
                put("player", player)
                put("albumArtUrl", albumArtUrl)
            }
            return DeskConnectPacket(id = 0, type = TYPE_MPRIS_REQUEST, body = body)
        }

        fun createMprisRequest(player: String, action: String): DeskConnectPacket {
            val body = JSONObject().apply {
                put("player", player)
                put("action", action)
                put("requestAction", action)
            }
            return DeskConnectPacket(id = 0, type = TYPE_MPRIS_REQUEST, body = body)
        }

        fun createNotificationCancel(notificationId: String): DeskConnectPacket {
            val body = JSONObject().apply {
                put("cancel", notificationId)
            }
            return DeskConnectPacket(id = 0, type = TYPE_NOTIFICATION_REQUEST, body = body)
        }
    }
}
