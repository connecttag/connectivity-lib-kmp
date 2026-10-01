package org.connecttag.lib.connectivity.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.connecttag.lib.connectivity.ConnectivityState
import org.connecttag.lib.connectivity.ConnectivitySnapshot
import org.connecttag.lib.connectivity.ConnectivityType
import org.connecttag.lib.connectivity.PlatformConnectivityService
import org.connecttag.lib.connectivity.generated.resources.Res
import org.connecttag.lib.connectivity.generated.resources.connectivity_status_connection_lost
import org.connecttag.lib.connectivity.generated.resources.connectivity_status_offline
import org.connecttag.lib.connectivity.generated.resources.connectivity_status_online_vpn
import org.connecttag.lib.connectivity.generated.resources.connectivity_status_online_with_type
import org.connecttag.lib.connectivity.generated.resources.connectivity_status_unstable
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_cellular
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_ethernet
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_none
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_other
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_unknown
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_vpn
import org.connecttag.lib.connectivity.generated.resources.connectivity_type_wifi
import org.connecttag.lib.connectivity.observeSnapshots
import org.jetbrains.compose.resources.stringResource

/**
 * Remembers the atomic connectivity snapshot and updates it when either state
 * or transport changes.
 */
@Composable
fun rememberConnectivitySnapshot(
    connectivityService: PlatformConnectivityService,
): ConnectivitySnapshot {
    val snapshot = remember(connectivityService) {
        mutableStateOf(connectivityService.currentSnapshot())
    }

    LaunchedEffect(connectivityService) {
        connectivityService.observeSnapshots().collect { current ->
            snapshot.value = current
        }
    }

    return snapshot.value
}

/**
 * State-only compatibility projection of [rememberConnectivitySnapshot].
 */
@Composable
fun rememberConnectivityState(
    connectivityService: PlatformConnectivityService,
): ConnectivityState {
    return rememberConnectivitySnapshot(connectivityService).state
}

/**
 * A generic network status indicator bar.
 */
@Composable
fun NetworkStatusIndicator(
    connectivityService: PlatformConnectivityService,
    modifier: Modifier = Modifier,
    availableLabel: ((ConnectivityType) -> String)? = null,
    availableVpnLabel: String? = null,
    unavailableLabel: String? = null,
    losingLabel: String? = null,
    lostLabel: String? = null,
    availableColor: Color = Color(0xFF4CAF50),
    availableVpnColor: Color = Color(0xFF2196F3),
    unavailableColor: Color = Color(0xFFF44336),
    losingColor: Color = Color(0xFFFFEB3B),
    lostColor: Color = Color(0xFFF44336),
) {
    val connectivity = rememberConnectivitySnapshot(connectivityService)
    val connectionState = connectivity.state
    val connectionType = connectivity.type

    val connectionTypeLabel = connectivityTypeLabel(connectionType)
    val resolvedAvailableLabel = availableLabel?.invoke(connectionType)
        ?: stringResource(Res.string.connectivity_status_online_with_type, connectionTypeLabel)
    val resolvedAvailableVpnLabel = availableVpnLabel
        ?: stringResource(Res.string.connectivity_status_online_vpn)
    val resolvedUnavailableLabel = unavailableLabel
        ?: stringResource(Res.string.connectivity_status_offline)
    val resolvedLosingLabel = losingLabel
        ?: stringResource(Res.string.connectivity_status_unstable)
    val resolvedLostLabel = lostLabel
        ?: stringResource(Res.string.connectivity_status_connection_lost)

    val (backgroundColor, text) = when (connectionState) {
        ConnectivityState.Available -> availableColor to resolvedAvailableLabel
        ConnectivityState.AvailableThroughVpn -> availableVpnColor to resolvedAvailableVpnLabel
        ConnectivityState.Unavailable -> unavailableColor to resolvedUnavailableLabel
        ConnectivityState.Losing -> losingColor to resolvedLosingLabel
        ConnectivityState.Lost -> lostColor to resolvedLostLabel
    }

    if (connectionState != ConnectivityState.Available) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor.copy(alpha = 0.8f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = Color.White)
        }
    }
}

@Composable
private fun connectivityTypeLabel(type: ConnectivityType): String {
    return when (type) {
        ConnectivityType.Wifi -> stringResource(Res.string.connectivity_type_wifi)
        ConnectivityType.Cellular -> stringResource(Res.string.connectivity_type_cellular)
        ConnectivityType.Ethernet -> stringResource(Res.string.connectivity_type_ethernet)
        ConnectivityType.Vpn -> stringResource(Res.string.connectivity_type_vpn)
        ConnectivityType.Other -> stringResource(Res.string.connectivity_type_other)
        ConnectivityType.Unknown -> stringResource(Res.string.connectivity_type_unknown)
        ConnectivityType.None -> stringResource(Res.string.connectivity_type_none)
    }
}

/**
 * A helper class to hold connectivity state in a way that can be observed in Compose.
 */
class ConnectivityStateHolder(
    private val connectivityService: PlatformConnectivityService
) {
    private val _snapshot = mutableStateOf(connectivityService.currentSnapshot())
    val snapshot: State<ConnectivitySnapshot> = _snapshot
    val connectionState: State<ConnectivityState> = derivedStateOf { _snapshot.value.state }
    val connectionType: State<ConnectivityType> = derivedStateOf { _snapshot.value.type }

    suspend fun startObserving() {
        connectivityService.observeSnapshots().collect { snapshot ->
            _snapshot.value = snapshot
        }
    }
}
