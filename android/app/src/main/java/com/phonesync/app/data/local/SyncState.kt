package com.phonesync.app.data.local

/**
 * Lifecycle of a single on-device media item as it moves through scanning, hashing,
 * uploading, and (optionally) archiving / discarding.
 *
 * ```
 * PENDING -> UPLOADING -> BACKED_UP -> ARCHIVED
 *    ^            |
 *    +---FAILED---+
 *
 * BACKED_UP -> PENDING_DISCARD -> (row removed)   // gallery delete without archive intent
 * ```
 */
enum class SyncState {
    /** Discovered locally, not yet hashed/uploaded. */
    PENDING,

    /** Hash and/or upload in progress (resumable uploads may straddle app restarts). */
    UPLOADING,

    /** Server has a verified copy. May still be on-device. */
    BACKED_UP,

    /** Server has a verified copy AND the user asked us to free device storage for it. */
    ARCHIVED,

    /**
     * The file disappeared from the device's MediaStore (e.g. deleted from Gallery) without
     * going through the app's own archive flow. The server copy is still authoritative and
     * must be told to discard on the next sync — see [com.phonesync.app.data.repository.LocalDeleteSemantics].
     */
    PENDING_DISCARD,

    /** Upload attempted and failed; eligible for retry on the next sync pass. */
    FAILED,
}
