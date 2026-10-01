package org.connecttag.lib.connectivity

internal inline fun attemptConnectivityCallbackRegistration(register: () -> Unit): Boolean {
    return runCatching(register).isSuccess
}

internal fun ConnectivitySnapshot.withMonitoringRegistrationResult(
    registrationSucceeded: Boolean,
): ConnectivitySnapshot = copy(isMonitoringActive = registrationSucceeded)
