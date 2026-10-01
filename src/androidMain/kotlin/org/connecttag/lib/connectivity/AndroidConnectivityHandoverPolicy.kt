package org.connecttag.lib.connectivity

internal object AndroidConnectivityHandoverPolicy {
    fun resolveAfterNetworkLost(
        lostNetworkRemainsActive: Boolean,
        activeSnapshot: ConnectivitySnapshot,
    ): ConnectivitySnapshot {
        if (!lostNetworkRemainsActive) return activeSnapshot

        return ConnectivitySnapshot(
            state = ConnectivityState.Unavailable,
            type = ConnectivityType.None,
        )
    }
}
