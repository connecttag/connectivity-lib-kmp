package org.connecttag.lib.connectivity

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivitySnapshotTest {
    @Test
    fun `snapshot derives availability and vpn flags from service state`() {
        val service = FakePlatformConnectivityService(
            initialState = ConnectivityState.AvailableThroughVpn,
            initialType = ConnectivityType.Vpn,
        )

        val snapshot = service.currentSnapshot()

        assertTrue(snapshot.isInternetAvailable)
        assertTrue(snapshot.isAvailableThroughVpn)
        assertTrue(snapshot.isVpnTransport)
        assertFalse(snapshot.isUnavailable)
        assertFalse(snapshot.isLosing)
        assertTrue(service.isInternetAvailable())
        assertTrue(service.isVpnActive())
    }

    @Test
    fun `snapshot derives unavailable for lost state`() {
        val service = FakePlatformConnectivityService(
            initialState = ConnectivityState.Lost,
            initialType = ConnectivityType.None,
        )

        val snapshot = service.currentSnapshot()

        assertFalse(snapshot.isInternetAvailable)
        assertTrue(snapshot.isUnavailable)
        assertFalse(service.isInternetAvailable())
        assertFalse(service.isVpnActive())
    }

    @Test
    fun `snapshot flow emits latest composite state`() = runBlocking {
        val service = FakePlatformConnectivityService()

        service.pushSnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Wifi,
        )

        val snapshot = service.observeSnapshots().first()
        assertEquals(ConnectivityState.Available, snapshot.state)
        assertEquals(ConnectivityType.Wifi, snapshot.type)
        assertTrue(snapshot.isInternetAvailable)
    }

    @Test
    fun `validated availability accepts every transport category`() {
        val supportedTransports = listOf(
            ConnectivityType.Wifi,
            ConnectivityType.Cellular,
            ConnectivityType.Ethernet,
            ConnectivityType.Vpn,
            ConnectivityType.Other,
            ConnectivityType.Unknown,
        )

        supportedTransports.forEach { type ->
            val snapshot = ConnectivitySnapshot(
                state = if (type == ConnectivityType.Vpn) {
                    ConnectivityState.AvailableThroughVpn
                } else {
                    ConnectivityState.Available
                },
                type = type,
            )

            assertTrue(snapshot.isInternetAvailable, "Expected ${type.wireValue} to be online")
        }
    }

    @Test
    fun `vpn keeps primary and underlying transport independently`() {
        val snapshot = ConnectivitySnapshot(
            state = ConnectivityState.AvailableThroughVpn,
            type = ConnectivityType.Vpn,
            underlyingType = ConnectivityType.Cellular,
            isMetered = true,
        )

        assertEquals(ConnectivityType.Vpn, snapshot.type)
        assertEquals(ConnectivityType.Cellular, snapshot.policyTransportType)
        assertTrue(snapshot.isVpnTransport)
        assertTrue(snapshot.isMetered)
    }

    @Test
    fun `captive portal is not treated as internet availability`() {
        val snapshot = ConnectivitySnapshot(
            state = ConnectivityState.Unavailable,
            type = ConnectivityType.Wifi,
            hasCaptivePortal = true,
            hasInternetCapability = true,
            isValidated = false,
        )

        assertTrue(snapshot.hasCaptivePortal)
        assertFalse(snapshot.isValidated)
        assertFalse(snapshot.isInternetAvailable)
    }

    @Test
    fun `data saver uses metered roaming and platform restriction facts`() {
        val metered = ConnectivitySnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Other,
            isMetered = true,
        )
        val roaming = metered.copy(isMetered = false, isRoaming = true)
        val restricted = roaming.copy(isRoaming = false, isDataSaverRestricted = true)
        val unrestricted = restricted.copy(isDataSaverRestricted = false)

        assertTrue(isDataSaverActive(true, metered))
        assertTrue(isDataSaverActive(true, roaming))
        assertTrue(isDataSaverActive(true, restricted))
        assertFalse(isDataSaverActive(true, unrestricted))
        assertFalse(isDataSaverActive(false, metered))
    }

    @Test
    fun `snapshot flow emits transport-only changes`() = runBlocking {
        val service = FakePlatformConnectivityService(
            initialState = ConnectivityState.Available,
            initialType = ConnectivityType.Wifi,
        )

        service.pushSnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Cellular,
        )

        assertEquals(ConnectivityType.Cellular, service.snapshots.value.type)
        assertEquals(ConnectivityState.Available, service.snapshots.value.state)
    }

    @Test
    fun `snapshot flow emits state-only changes`() = runBlocking {
        val service = FakePlatformConnectivityService(
            initialState = ConnectivityState.Losing,
            initialType = ConnectivityType.Wifi,
        )

        service.pushSnapshot(
            state = ConnectivityState.Available,
            type = ConnectivityType.Wifi,
        )

        assertEquals(ConnectivityState.Available, service.snapshots.value.state)
        assertEquals(ConnectivityType.Wifi, service.snapshots.value.type)
    }
}
