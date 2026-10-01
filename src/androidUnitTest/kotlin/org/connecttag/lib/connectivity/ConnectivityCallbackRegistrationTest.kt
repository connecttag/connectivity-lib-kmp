package org.connecttag.lib.connectivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivityCallbackRegistrationTest {
    @Test
    fun `registration success is observable`() {
        assertTrue(attemptConnectivityCallbackRegistration { })
    }

    @Test
    fun `registration failure is contained and observable`() {
        val registered = attemptConnectivityCallbackRegistration {
            error("callback registration unavailable")
        }
        val snapshot = ConnectivitySnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Other,
        ).withMonitoringRegistrationResult(registered)

        assertFalse(registered)
        assertFalse(snapshot.isMonitoringActive)
        assertEquals(ConnectivityState.Available, snapshot.state)
        assertEquals(ConnectivityType.Other, snapshot.type)
    }
}
