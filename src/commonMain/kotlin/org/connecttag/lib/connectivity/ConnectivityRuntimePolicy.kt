package org.connecttag.lib.connectivity

fun isDataSaverActive(
    dataSaverEnabled: Boolean,
    snapshot: ConnectivitySnapshot,
): Boolean {
    return dataSaverEnabled && (
        snapshot.isMetered ||
            snapshot.isRoaming ||
            snapshot.isDataSaverRestricted ||
            snapshot.isLosing ||
            snapshot.isUnavailable
        )
}
