package com.phonesync.app.data.repository

import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.local.SyncState

/** What to do with a previously-tracked local row that a fresh MediaStore scan no longer sees. */
sealed interface DeleteOutcome {
    data class UpdateState(val state: SyncState) : DeleteOutcome
    data object RemoveLocalRow : DeleteOutcome
}

/**
 * Decides what a *disappearance* from the on-device MediaStore means for a previously-tracked
 * asset, so a scan can tell the difference between:
 *
 * - the app itself deleted the file after a confirmed server archive (expected, no server call needed)
 * - the user deleted it from Gallery/Files directly, bypassing the app's archive flow (the server
 *   copy is now the only copy, so it must be told to discard next sync — see
 *   [com.phonesync.app.data.repository.PhotoSyncRepository.processDiscardIntents])
 * - the file was never backed up at all (nothing for the server to know about; just forget it)
 *
 * Once a [SyncState.PENDING_DISCARD] row is actually processed the row is deleted outright
 * (see [PhotoSyncRepository.processDiscardIntents]), so there is no separate terminal
 * "discarded" state to model here.
 *
 * Pure and Android-framework-free by design so it is fully unit-testable.
 */
object LocalDeleteSemantics {
    fun resolveMissingAsset(previous: LocalAssetEntity): DeleteOutcome = when {
        previous.localDeleted -> DeleteOutcome.UpdateState(SyncState.ARCHIVED)
        previous.syncState == SyncState.ARCHIVED -> DeleteOutcome.UpdateState(SyncState.ARCHIVED)
        previous.syncState == SyncState.PENDING_DISCARD -> DeleteOutcome.UpdateState(SyncState.PENDING_DISCARD)
        previous.serverAssetId != null -> DeleteOutcome.UpdateState(SyncState.PENDING_DISCARD)
        else -> DeleteOutcome.RemoveLocalRow
    }
}
