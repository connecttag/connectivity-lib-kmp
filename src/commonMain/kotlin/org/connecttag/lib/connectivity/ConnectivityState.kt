package org.connecttag.lib.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents the current connectivity state of the device.
 */
sealed interface ConnectivityState {
    /**
     * Internet is available (direct or through VPN).
     */
    data object Available : ConnectivityState

    /**
     * Internet is available through a VPN connection.
     */
    data object AvailableThroughVpn : ConnectivityState

    /**
     * Internet is unavailable.
     */
    data object Unavailable : ConnectivityState

    /**
     * The connection is in the process of being lost.
     */
    data object Losing : ConnectivityState

    /**
     * The connection has been lost.
     */
    data object Lost : ConnectivityState
}

/**
 * Returns true if the connectivity state indicates that internet is available.
 */
fun ConnectivityState.isInternetAvailable(): Boolean {
    return this == ConnectivityState.Available || this == ConnectivityState.AvailableThroughVpn
}

data class ConnectivitySnapshot(
    val state: ConnectivityState,
    val type: ConnectivityType,
    /** Physical transport underneath a VPN, when Android exposes it. */
    val underlyingType: ConnectivityType? = null,
    val isMetered: Boolean = false,
    val isRoaming: Boolean = false,
    val isDataSaverRestricted: Boolean = false,
    val hasCaptivePortal: Boolean = false,
    val hasInternetCapability: Boolean = state.isInternetAvailable(),
    val isValidated: Boolean = state.isInternetAvailable(),
    val isMonitoringActive: Boolean = true,
) {
    val isInternetAvailable: Boolean
        get() = state.isInternetAvailable()

    val isUnavailable: Boolean
        get() = state is ConnectivityState.Unavailable || state is ConnectivityState.Lost

    val isLosing: Boolean
        get() = state is ConnectivityState.Losing

    val isAvailableThroughVpn: Boolean
        get() = state is ConnectivityState.AvailableThroughVpn

    val isVpnTransport: Boolean
        get() = type == ConnectivityType.Vpn

    /** Transport used for allow/block policy while VPN permission stays independent. */
    val policyTransportType: ConnectivityType
        get() = underlyingType ?: type
}

/**
 * Represents the type of network connection.
 */
sealed interface ConnectivityType {
    /**
     * Stable identifier for headers, logging, and analytics.
     */
    val wireValue: String

    /**
     * Translation key for UI labels.
     */
    val labelKey: String

    data object Wifi : ConnectivityType {
        override val wireValue: String = "wifi"
        override val labelKey: String = "connectivity_type_wifi"
    }

    data object Cellular : ConnectivityType {
        override val wireValue: String = "cellular"
        override val labelKey: String = "connectivity_type_cellular"
    }

    data object Ethernet : ConnectivityType {
        override val wireValue: String = "ethernet"
        override val labelKey: String = "connectivity_type_ethernet"
    }

    data object Vpn : ConnectivityType {
        override val wireValue: String = "vpn"
        override val labelKey: String = "connectivity_type_vpn"
    }

    /**
     * A validated internet transport that does not belong to a named category.
     *
     * Availability is derived from [ConnectivityState], so Bluetooth, USB,
     * satellite, Wi-Fi Aware, and future transports are accepted without
     * requiring a new availability rule.
     */
    data object Other : ConnectivityType {
        override val wireValue: String = "other"
        override val labelKey: String = "connectivity_type_other"
    }

    data object Unknown : ConnectivityType {
        override val wireValue: String = "unknown"
        override val labelKey: String = "connectivity_type_unknown"
    }

    data object None : ConnectivityType {
        override val wireValue: String = "none"
        override val labelKey: String = "connectivity_type_none"
    }
}

/**
 * Contract for platform-specific connectivity monitoring.
 */
interface ConnectivityStatusProvider {
    val snapshots: StateFlow<ConnectivitySnapshot>

    fun currentSnapshot(): ConnectivitySnapshot = snapshots.value

    fun isInternetAvailable(): Boolean = currentSnapshot().isInternetAvailable

    fun isVpnActive(): Boolean {
        val snapshot = currentSnapshot()
        return snapshot.isAvailableThroughVpn || snapshot.isVpnTransport
    }
}

interface PlatformConnectivityService : ConnectivityStatusProvider {
    /**
     * The single source of truth for current connectivity and future updates.
     * State and transport are emitted atomically in the same snapshot.
     */
    override val snapshots: StateFlow<ConnectivitySnapshot>
}

fun PlatformConnectivityService.isOnline(): Boolean {
    return isInternetAvailable()
}

fun PlatformConnectivityService.observeSnapshots(): StateFlow<ConnectivitySnapshot> {
    return snapshots
}
