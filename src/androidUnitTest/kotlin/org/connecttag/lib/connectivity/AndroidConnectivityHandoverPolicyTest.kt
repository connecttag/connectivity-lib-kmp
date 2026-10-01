package org.connecttag.lib.connectivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidConnectivityHandoverPolicyTest {
    @Test
    fun `losing old network preserves a validated fallback of any transport`() {
        val fallback = ConnectivitySnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Other,
        )

        val resolved = AndroidConnectivityHandoverPolicy.resolveAfterNetworkLost(
            lostNetworkRemainsActive = false,
            activeSnapshot = fallback,
        )

        assertEquals(fallback, resolved)
        assertTrue(resolved.isInternetAvailable)
    }

    @Test
    fun `losing the active network reports unavailable until a fallback is active`() {
        val resolved = AndroidConnectivityHandoverPolicy.resolveAfterNetworkLost(
            lostNetworkRemainsActive = true,
            activeSnapshot = ConnectivitySnapshot(
                state = ConnectivityState.Available,
                type = ConnectivityType.Cellular,
            ),
        )

        assertEquals(ConnectivityState.Unavailable, resolved.state)
        assertEquals(ConnectivityType.None, resolved.type)
        assertFalse(resolved.isInternetAvailable)
    }

    @Test
    fun `wifi to cellular handover keeps the validated replacement online`() {
        val cellular = ConnectivitySnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Cellular,
            isMetered = true,
        )

        val resolved = AndroidConnectivityHandoverPolicy.resolveAfterNetworkLost(
            lostNetworkRemainsActive = false,
            activeSnapshot = cellular,
        )

        assertEquals(ConnectivityType.Cellular, resolved.type)
        assertTrue(resolved.isInternetAvailable)
        assertTrue(resolved.isMetered)
    }
}
