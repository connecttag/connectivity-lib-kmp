package org.connecttag.lib.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android implementation of [PlatformConnectivityService].
 */
class AndroidConnectivityService(
    private val context: Context,
    private val nowMillis: () -> Long,
    backgroundDispatcher: CoroutineDispatcher,
    private val transitionRules: ConnectivityTransitionRules = ConnectivityTransitionRules(),
    private val telemetryRecorder: ConnectivityTelemetryRecorder,
) : PlatformConnectivityService {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val serviceScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val stateMachine = ConnectivityStateMachine(transitionRules)
    private val stateMachineLock = Any()
    private var pendingTransitionJob: Job? = null
    private var pendingTransitionTargetState: ConnectivityState? = null
    private var isMonitoringActive = true
    private val initialSnapshot = readActiveNetworkSnapshot()
    private var latestRawSnapshot: ConnectivitySnapshot = initialSnapshot

    private val _snapshots = MutableStateFlow(initialSnapshot)
    override val snapshots: StateFlow<ConnectivitySnapshot> = _snapshots.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            consumeRawSnapshot(readNetworkSnapshot(network))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            consumeRawSnapshot(readNetworkSnapshot(network, networkCapabilities))
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            val activeSnapshot = readActiveNetworkSnapshot()
            val rawSnapshot = if (
                connectivityManager.activeNetwork == network &&
                activeSnapshot.isInternetAvailable
            ) {
                activeSnapshot.copy(state = ConnectivityState.Losing)
            } else {
                activeSnapshot
            }
            consumeRawSnapshot(rawSnapshot)
        }

        override fun onLost(network: Network) {
            val activeNetwork = connectivityManager.activeNetwork
            consumeRawSnapshot(
                AndroidConnectivityHandoverPolicy.resolveAfterNetworkLost(
                    lostNetworkRemainsActive = activeNetwork == null || activeNetwork == network,
                    activeSnapshot = readActiveNetworkSnapshot(),
                ),
            )
        }

        override fun onUnavailable() {
            consumeActiveNetworkSnapshot()
        }
    }

    init {
        stateMachine.initialize(
            initialState = initialSnapshot.state,
            nowMillis = nowMillis(),
        )
        isMonitoringActive = registerNetworkCallback()
        if (!isMonitoringActive) {
            latestRawSnapshot = initialSnapshot.withMonitoringRegistrationResult(
                registrationSucceeded = false,
            )
            _snapshots.value = latestRawSnapshot
        }
        readActiveNetworkSnapshot()
            .takeIf { refreshedSnapshot -> refreshedSnapshot != _snapshots.value }
            ?.let(::consumeRawSnapshot)
    }

    private fun registerNetworkCallback(): Boolean {
        return attemptConnectivityCallbackRegistration {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        }
    }

    private fun consumeActiveNetworkSnapshot() {
        consumeRawSnapshot(readActiveNetworkSnapshot())
    }

    private fun consumeRawSnapshot(rawSnapshot: ConnectivitySnapshot) {
        synchronized(stateMachineLock) {
            latestRawSnapshot = rawSnapshot.copy(isMonitoringActive = isMonitoringActive)
            val decision = stateMachine.onSignal(
                rawState = rawSnapshot.state,
                nowMillis = nowMillis(),
            )

            applyTransition(decision.immediateTransition)
            if (decision.immediateTransition == null) {
                updateSnapshot(
                    state = stateMachine.currentState(),
                )
            }
            scheduleDeferredTransition(decision.deferredTransition)
        }
    }

    private fun scheduleDeferredTransition(deferredTransition: ConnectivityDeferredTransition?) {
        if (deferredTransition == null) {
            pendingTransitionJob?.cancel()
            pendingTransitionJob = null
            pendingTransitionTargetState = null
            return
        }
        if (shouldKeepPendingConnectivityTransition(
                pendingTargetState = pendingTransitionTargetState,
                isPendingTransitionActive = pendingTransitionJob?.isActive == true,
                nextTransition = deferredTransition,
            )
        ) {
            return
        }
        pendingTransitionJob?.cancel()
        pendingTransitionTargetState = deferredTransition.targetState

        pendingTransitionJob = serviceScope.launch {
            if (deferredTransition.delayMillis > 0) {
                delay(deferredTransition.delayMillis.milliseconds)
            }
            synchronized(stateMachineLock) {
                val transition = stateMachine.applyDeferredTransition(
                    targetState = deferredTransition.targetState,
                    nowMillis = nowMillis(),
                )
                pendingTransitionTargetState = null
                pendingTransitionJob = null
                applyTransition(transition)
            }
        }
    }

    private fun applyTransition(transition: ConnectivityTransition?) {
        transition ?: return
        updateSnapshot(
            state = transition.toState,
        )
        telemetryRecorder.record(
            ConnectivityTelemetryEvent(
                fromState = transition.fromState,
                toState = transition.toState,
                networkType = latestRawSnapshot.type,
                outageDurationMillis = transition.outageDurationMillis,
                transitionCount = transition.transitionCount,
                flapCount = transition.flapCount,
                timestampMillis = transition.timestampMillis,
            )
        )
    }

    private fun updateSnapshot(
        state: ConnectivityState,
    ) {
        _snapshots.update { current ->
            latestRawSnapshot.copy(state = state).takeIf { it != current } ?: current
        }
    }

    private fun readActiveNetworkSnapshot(): ConnectivitySnapshot {
        return try {
            val network = connectivityManager.activeNetwork
                ?: return ConnectivitySnapshot(
                    state = ConnectivityState.Unavailable,
                    type = ConnectivityType.None,
                )
            readNetworkSnapshot(network)
        } catch (_: Exception) {
            ConnectivitySnapshot(
                state = ConnectivityState.Unavailable,
                type = ConnectivityType.Unknown,
            )
        }
    }

    private fun readNetworkSnapshot(
        network: Network,
        knownCapabilities: NetworkCapabilities? = null,
    ): ConnectivitySnapshot {
        return try {
            val capabilities = knownCapabilities ?: connectivityManager.getNetworkCapabilities(network)
                ?: return ConnectivitySnapshot(
                    state = ConnectivityState.Unavailable,
                    type = ConnectivityType.Unknown,
                )
            val type = capabilities.toConnectivityType()
            val hasInternetCapability =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val hasCaptivePortal =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            val isMetered =
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val isRoaming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            val isDataSaverRestricted =
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        connectivityManager.restrictBackgroundStatus ==
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
            val underlyingType = capabilities.underlyingConnectivityType()

            if (!hasInternetCapability || !isValidated) {
                return ConnectivitySnapshot(
                    state = ConnectivityState.Unavailable,
                    type = type,
                    underlyingType = underlyingType,
                    isMetered = isMetered,
                    isRoaming = isRoaming,
                    isDataSaverRestricted = isDataSaverRestricted,
                    hasCaptivePortal = hasCaptivePortal,
                    hasInternetCapability = hasInternetCapability,
                    isValidated = isValidated,
                    isMonitoringActive = isMonitoringActive,
                )
            }

            val state = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                ConnectivityState.AvailableThroughVpn
            } else {
                ConnectivityState.Available
            }
            ConnectivitySnapshot(
                state = state,
                type = type,
                underlyingType = underlyingType,
                isMetered = isMetered,
                isRoaming = isRoaming,
                isDataSaverRestricted = isDataSaverRestricted,
                hasCaptivePortal = hasCaptivePortal,
                hasInternetCapability = hasInternetCapability,
                isValidated = isValidated,
                isMonitoringActive = isMonitoringActive,
            )
        } catch (_: Exception) {
            ConnectivitySnapshot(
                state = ConnectivityState.Unavailable,
                type = ConnectivityType.Unknown,
            )
        }
    }

    private fun NetworkCapabilities.toConnectivityType(): ConnectivityType {
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectivityType.Vpn
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityType.Wifi
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityType.Cellular
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityType.Ethernet
            else -> ConnectivityType.Other
        }
    }

    private fun NetworkCapabilities.underlyingConnectivityType(): ConnectivityType? {
        if (!hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return null
        }

        @Suppress("DEPRECATION")
        val networks = connectivityManager.allNetworks

        return networks.asSequence()
            .filter { network -> network != connectivityManager.activeNetwork }
            .mapNotNull(connectivityManager::getNetworkCapabilities)
            .filter { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            .map { capabilities -> capabilities.toConnectivityType() }
            .firstOrNull()
    }

    /**
     * Unregisters the network callback.
     */
    fun unregister() {
        pendingTransitionJob?.cancel()
        pendingTransitionJob = null
        pendingTransitionTargetState = null
        serviceScope.coroutineContext[Job]?.cancel()
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
