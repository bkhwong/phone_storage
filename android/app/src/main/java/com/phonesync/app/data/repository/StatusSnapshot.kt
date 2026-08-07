package com.phonesync.app.data.repository

/** Local + (optionally) network-derived snapshot of sync health for the Home screen. */
data class StatusSnapshot(
    val pendingCount: Int = 0,
    val pendingBytes: Long = 0,
    val failedCount: Int = 0,
    /** Everything the server has ever confirmed, on-device or not — used for the progress ring. */
    val backedUpTotalCount: Int = 0,
    /** Items the server already has, that are still taking up space on this device. */
    val backedUpOnDeviceCount: Int = 0,
    val lastSyncEpochMs: Long = 0,
    /** null = not checked yet this session. */
    val pcReachable: Boolean? = null,
)

/** Result of a [PhotoSyncRepository.scanAndReconcileLocal] pass. */
data class ScanResult(
    val newlyDiscovered: Int = 0,
    val missingLocally: Int = 0,
)
