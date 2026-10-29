package com.nxd1frnt.clockdesk2.notifications

import org.junit.Assert.*
import org.junit.Test

class BatteryAlertPolicyTest {
    @Test fun alertsOncePerEpisodeAndRearmsAfterCharging() {
        val policy = BatteryAlertPolicy()
        assertFalse(policy.update("phone", 50, false, 20).low)
        assertTrue(policy.update("phone", 20, false, 20).newEpisode)
        assertFalse(policy.update("phone", 19, false, 20).newEpisode)
        assertFalse(policy.update("phone", 0, false, 20).newEpisode)
        assertFalse(policy.update("phone", 0, true, 20).low)
        assertTrue(policy.update("phone", 10, false, 20).newEpisode)
    }

    @Test fun thresholdFluctuationsDoNotRepeatAlertsButRecoveryRearms() {
        val policy = BatteryAlertPolicy()
        assertTrue(policy.update("phone", 20, false, 20).newEpisode)
        assertFalse(policy.update("phone", 21, false, 20).low)
        assertFalse(policy.update("phone", 20, false, 20).newEpisode)
        assertFalse(policy.update("phone", 25, false, 20).low)
        assertTrue(policy.update("phone", 20, false, 20).newEpisode)
    }

    @Test fun devicesAreIndependentAndInvalidSamplesDoNotRearm() {
        val policy = BatteryAlertPolicy()
        assertTrue(policy.update("phone", 0, false, 20).newEpisode)
        assertTrue(policy.update("tablet", 10, false, 20).newEpisode)
        assertFalse(policy.update("phone", -1, true, 20).low)
        assertFalse(policy.update("phone", 101, true, 20).low)
        assertFalse(policy.update("phone", 5, false, 20).newEpisode)
    }
}
