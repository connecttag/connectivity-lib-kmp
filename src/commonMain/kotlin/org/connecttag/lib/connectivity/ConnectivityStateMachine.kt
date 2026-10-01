package org.connecttag.lib.connectivity

data class ConnectivityTransitionRules(
    val losingToLostDelayMillis: Long = 1_500L,
    val recoveryDebounceMillis: Long = 500L,
    val maxRecoveryBackoffMillis: Long = 4_000L,
    val flapWindowMillis: Long = 15_000L,
)

data class ConnectivityDeferredTransition(
    val targetState: ConnectivityState,
    val delayMillis: Long,
)

internal fun shouldKeepPendingConnectivityTransition(
    pendingTargetState: ConnectivityState?,
    isPendingTransitionActive: Boolean,
    nextTransition: ConnectivityDeferredTransition,
): Boolean = isPendingTransitionActive && pendingTargetState == nextTransition.targetState

data class ConnectivityTransition(
    val fromState: ConnectivityState,
    val toState: ConnectivityState,
    val timestampMillis: Long,
    val outageDurationMillis: Long?,
    val transitionCount: Int,
    val flapCount: Int,
)

data class ConnectivityTransitionDecision(
    val immediateTransition: ConnectivityTransition? = null,
    val deferredTransition: ConnectivityDeferredTransition? = null,
)

class ConnectivityStateMachine(
    private val rules: ConnectivityTransitionRules = ConnectivityTransitionRules(),
) {
    private var stableState: ConnectivityState = ConnectivityState.Unavailable
    private var transitionCount: Int = 0
    private var flapCount: Int = 0
    private var outageStartedAtMillis: Long? = null

    fun initialize(
        initialState: ConnectivityState,
        nowMillis: Long,
    ) {
        stableState = initialState
        transitionCount = 0
        flapCount = 0
        outageStartedAtMillis = if (initialState.isInternetAvailable()) {
            null
        } else {
            nowMillis
        }
    }

    fun currentState(): ConnectivityState = stableState

    fun onSignal(
        rawState: ConnectivityState,
        nowMillis: Long,
    ): ConnectivityTransitionDecision {
        val isRawOnline = rawState.isInternetAvailable()
        val isStableOnline = stableState.isInternetAvailable()

        return when {
            isRawOnline && isStableOnline -> {
                ConnectivityTransitionDecision(
                    immediateTransition = transitionTo(rawState, nowMillis),
                )
            }
            isRawOnline && !isStableOnline -> {
                val recoveryDelay = recoveryDelayMillis()
                ConnectivityTransitionDecision(
                    deferredTransition = ConnectivityDeferredTransition(
                        targetState = rawState,
                        delayMillis = recoveryDelay,
                    ),
                )
            }
            !isRawOnline && isStableOnline -> {
                ConnectivityTransitionDecision(
                    immediateTransition = transitionTo(ConnectivityState.Losing, nowMillis),
                    deferredTransition = ConnectivityDeferredTransition(
                        targetState = ConnectivityState.Lost,
                        delayMillis = rules.losingToLostDelayMillis,
                    ),
                )
            }
            stableState is ConnectivityState.Unavailable -> {
                ConnectivityTransitionDecision(
                    immediateTransition = transitionTo(ConnectivityState.Losing, nowMillis),
                    deferredTransition = ConnectivityDeferredTransition(
                        targetState = ConnectivityState.Lost,
                        delayMillis = rules.losingToLostDelayMillis,
                    ),
                )
            }
            stableState is ConnectivityState.Losing -> {
                ConnectivityTransitionDecision(
                    deferredTransition = ConnectivityDeferredTransition(
                        targetState = ConnectivityState.Lost,
                        delayMillis = rules.losingToLostDelayMillis,
                    ),
                )
            }
            else -> ConnectivityTransitionDecision()
        }
    }

    fun applyDeferredTransition(
        targetState: ConnectivityState,
        nowMillis: Long,
    ): ConnectivityTransition? {
        return transitionTo(targetState, nowMillis)
    }

    private fun recoveryDelayMillis(): Long {
        val multiplier = 1L shl flapCount.coerceAtMost(3)
        return (rules.recoveryDebounceMillis * multiplier).coerceAtMost(rules.maxRecoveryBackoffMillis)
    }

    private fun transitionTo(
        nextState: ConnectivityState,
        nowMillis: Long,
    ): ConnectivityTransition? {
        if (stableState == nextState) return null

        val previousState = stableState
        val outageDurationMillis = if (nextState.isInternetAvailable()) {
            outageStartedAtMillis?.let { nowMillis - it }
        } else {
            null
        }

        if (!nextState.isInternetAvailable() && outageStartedAtMillis == null) {
            outageStartedAtMillis = nowMillis
        }

        if (nextState.isInternetAvailable()) {
            flapCount = if ((outageDurationMillis ?: Long.MAX_VALUE) <= rules.flapWindowMillis) {
                flapCount + 1
            } else {
                0
            }
            outageStartedAtMillis = null
        }

        stableState = nextState
        transitionCount += 1

        return ConnectivityTransition(
            fromState = previousState,
            toState = nextState,
            timestampMillis = nowMillis,
            outageDurationMillis = outageDurationMillis,
            transitionCount = transitionCount,
            flapCount = flapCount,
        )
    }
}

data class ConnectivityTelemetryEvent(
    val fromState: ConnectivityState,
    val toState: ConnectivityState,
    val networkType: ConnectivityType,
    val outageDurationMillis: Long?,
    val transitionCount: Int,
    val flapCount: Int,
    val timestampMillis: Long,
)

fun interface ConnectivityTelemetryRecorder {
    fun record(event: ConnectivityTelemetryEvent)
}
