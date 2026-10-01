package org.connecttag.lib.connectivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivityStateMachineTest {
    @Test
    fun `repeated signal keeps the active transition deadline`() {
        val recovery = ConnectivityDeferredTransition(
            targetState = ConnectivityState.Available,
            delayMillis = 500L,
        )

        assertTrue(
            shouldKeepPendingConnectivityTransition(
                pendingTargetState = ConnectivityState.Available,
                isPendingTransitionActive = true,
                nextTransition = recovery,
            ),
        )
        assertFalse(
            shouldKeepPendingConnectivityTransition(
                pendingTargetState = ConnectivityState.Lost,
                isPendingTransitionActive = true,
                nextTransition = recovery,
            ),
        )
        assertFalse(
            shouldKeepPendingConnectivityTransition(
                pendingTargetState = ConnectivityState.Available,
                isPendingTransitionActive = false,
                nextTransition = recovery,
            ),
        )
    }

    @Test
    fun `offline transition follows unavailable losing lost available sequence`() {
        val machine = ConnectivityStateMachine(
            rules = ConnectivityTransitionRules(
                losingToLostDelayMillis = 1_000L,
                recoveryDebounceMillis = 200L,
                maxRecoveryBackoffMillis = 1_000L,
            )
        )
        machine.initialize(initialState = ConnectivityState.Unavailable, nowMillis = 0L)

        val step1 = machine.onSignal(
            rawState = ConnectivityState.Unavailable,
            nowMillis = 100L,
        )
        assertNotNull(step1.immediateTransition)
        assertEquals(ConnectivityState.Losing, step1.immediateTransition.toState)
        assertNotNull(step1.deferredTransition)
        assertEquals(ConnectivityState.Lost, step1.deferredTransition.targetState)

        val step2 = machine.applyDeferredTransition(
            targetState = ConnectivityState.Lost,
            nowMillis = 1_300L,
        )
        assertNotNull(step2)
        assertEquals(ConnectivityState.Lost, step2.toState)

        val step3 = machine.onSignal(
            rawState = ConnectivityState.Available,
            nowMillis = 1_400L,
        )
        assertNull(step3.immediateTransition)
        assertNotNull(step3.deferredTransition)
        assertEquals(ConnectivityState.Available, step3.deferredTransition.targetState)

        val step4 = machine.applyDeferredTransition(
            targetState = ConnectivityState.Available,
            nowMillis = 1_700L,
        )
        assertNotNull(step4)
        assertEquals(ConnectivityState.Available, step4.toState)
    }

    @Test
    fun `repeated flapping increases recovery debounce`() {
        val machine = ConnectivityStateMachine(
            rules = ConnectivityTransitionRules(
                losingToLostDelayMillis = 100L,
                recoveryDebounceMillis = 100L,
                maxRecoveryBackoffMillis = 800L,
                flapWindowMillis = 5_000L,
            ),
        )
        machine.initialize(initialState = ConnectivityState.Available, nowMillis = 0L)

        machine.onSignal(ConnectivityState.Unavailable, nowMillis = 100L)
        machine.applyDeferredTransition(ConnectivityState.Lost, nowMillis = 200L)
        val firstRecovery = machine.onSignal(ConnectivityState.Available, nowMillis = 300L)
        assertEquals(100L, firstRecovery.deferredTransition?.delayMillis)
        val firstRecovered = machine.applyDeferredTransition(ConnectivityState.Available, nowMillis = 400L)
        assertEquals(1, firstRecovered?.flapCount)

        machine.onSignal(ConnectivityState.Unavailable, nowMillis = 500L)
        machine.applyDeferredTransition(ConnectivityState.Lost, nowMillis = 600L)
        val secondRecovery = machine.onSignal(ConnectivityState.Available, nowMillis = 700L)
        assertEquals(200L, secondRecovery.deferredTransition?.delayMillis)
        val secondRecovered = machine.applyDeferredTransition(ConnectivityState.Available, nowMillis = 900L)
        assertEquals(2, secondRecovered?.flapCount)
    }
}
