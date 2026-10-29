package com.nxd1frnt.clockdesk2.notifications

/** Keeps a dismissed low-battery episode suppressed, including across reconnects. */
internal class BatteryAlertPolicy {
    private val episodes = mutableSetOf<String>()
    data class Decision(val low: Boolean, val newEpisode: Boolean = false)

    fun update(deviceId: String, charge: Int, charging: Boolean, threshold: Int): Decision {
        if (charge !in 0..100) return Decision(false)
        if (charging || charge >= (threshold + 5).coerceAtMost(100)) {
            episodes.remove(deviceId)
            return Decision(false)
        }
        if (charge > threshold) return Decision(false)
        return Decision(true, episodes.add(deviceId))
    }

    fun forget(deviceId: String) { episodes.remove(deviceId) }
    fun reset() { episodes.clear() }
}
