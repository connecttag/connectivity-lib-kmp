package org.connecttag.lib.connectivity

import android.util.Log

class AndroidConnectivityTelemetryRecorder(
    private val tag: String = "ConnectivityTelemetry",
) : ConnectivityTelemetryRecorder {
    override fun record(event: ConnectivityTelemetryEvent) {
        val message = buildString {
            append("from=")
            append(event.fromState)
            append(" to=")
            append(event.toState)
            append(" type=")
            append(event.networkType.wireValue)
            append(" outageMs=")
            append(event.outageDurationMillis ?: 0L)
            append(" transitions=")
            append(event.transitionCount)
            append(" flaps=")
            append(event.flapCount)
            append(" ts=")
            append(event.timestampMillis)
        }
        Log.i(tag, message)
    }
}
