package org.connecttag.lib.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakePlatformConnectivityService(
    initialState: ConnectivityState = ConnectivityState.Unavailable,
    initialType: ConnectivityType = ConnectivityType.None,
    initialSnapshot: ConnectivitySnapshot = ConnectivitySnapshot(
        state = initialState,
        type = initialType,
    ),
) : PlatformConnectivityService {
    private val snapshotFlow = MutableStateFlow(initialSnapshot)

    override val snapshots: StateFlow<ConnectivitySnapshot> = snapshotFlow.asStateFlow()

    fun pushSnapshot(
        state: ConnectivityState,
        type: ConnectivityType,
        transform: ConnectivitySnapshot.() -> ConnectivitySnapshot = { this },
    ) {
        snapshotFlow.update {
            ConnectivitySnapshot(
                state = state,
                type = type,
            ).transform()
        }
    }
}
