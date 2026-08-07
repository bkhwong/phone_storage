package com.phonesync.app.data.repository

import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.local.SyncState
import com.phonesync.app.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDeleteSemanticsTest {

    private fun asset(
        syncState: SyncState,
        localDeleted: Boolean = false,
        serverAssetId: String? = null,
    ) = LocalAssetEntity(
        clientAssetId = "ms_image_1",
        mediaStoreId = 1,
        mediaKind = MediaKind.IMAGE,
        contentUri = "content://media/external/images/media/1",
        displayName = "IMG_0001.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_000,
        takenAtEpochMs = null,
        dateAddedEpochMs = 0,
        relativePath = "DCIM/Camera/",
        contentHash = "deadbeef",
        syncState = syncState,
        serverAssetId = serverAssetId,
        uploadSessionId = null,
        uploadOffset = 0,
        localDeleted = localDeleted,
        lastError = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    @Test
    fun neverUploaded_missingLocally_isRemovedOutright() {
        val result = LocalDeleteSemantics.resolveMissingAsset(asset(SyncState.PENDING))
        assertEquals(DeleteOutcome.RemoveLocalRow, result)
    }

    @Test
    fun uploading_missingLocally_isRemovedOutright_whenNoServerAssetYet() {
        val result = LocalDeleteSemantics.resolveMissingAsset(asset(SyncState.UPLOADING))
        assertEquals(DeleteOutcome.RemoveLocalRow, result)
    }

    @Test
    fun backedUp_deletedFromGallery_becomesPendingDiscard() {
        // The user deleted it from Gallery/Files directly, bypassing the app's own archive
        // flow -- the server copy is now the only copy and must be told to discard.
        val result = LocalDeleteSemantics.resolveMissingAsset(
            asset(SyncState.BACKED_UP, serverAssetId = "srv-1"),
        )
        assertEquals(DeleteOutcome.UpdateState(SyncState.PENDING_DISCARD), result)
    }

    @Test
    fun backedUp_withoutServerAssetId_isRemovedOutright() {
        // Defensive: shouldn't normally happen (BACKED_UP implies a server id), but if it
        // does there's nothing for the server to discard.
        val result = LocalDeleteSemantics.resolveMissingAsset(asset(SyncState.BACKED_UP))
        assertEquals(DeleteOutcome.RemoveLocalRow, result)
    }

    @Test
    fun archivedByApp_missingLocally_staysArchived() {
        // The app itself deleted the file after a confirmed server archive -- expected, no
        // server call needed, and definitely not a "gallery delete" to report.
        val result = LocalDeleteSemantics.resolveMissingAsset(
            asset(SyncState.ARCHIVED, localDeleted = true, serverAssetId = "srv-1"),
        )
        assertEquals(DeleteOutcome.UpdateState(SyncState.ARCHIVED), result)
    }

    @Test
    fun localDeletedFlagWins_regardlessOfState() {
        val result = LocalDeleteSemantics.resolveMissingAsset(
            asset(SyncState.BACKED_UP, localDeleted = true, serverAssetId = "srv-1"),
        )
        assertEquals(DeleteOutcome.UpdateState(SyncState.ARCHIVED), result)
    }

    @Test
    fun alreadyPendingDiscard_staysPendingDiscard_untilProcessed() {
        // e.g. a previous discard call failed (server offline) -- next scan should keep
        // retrying rather than flip-flopping state.
        val result = LocalDeleteSemantics.resolveMissingAsset(
            asset(SyncState.PENDING_DISCARD, serverAssetId = "srv-1"),
        )
        assertEquals(DeleteOutcome.UpdateState(SyncState.PENDING_DISCARD), result)
    }

    @Test
    fun failedUpload_missingLocally_withServerAsset_becomesPendingDiscard() {
        // Edge case: upload had "succeeded" once giving a serverAssetId, then a later retry
        // pass flipped it to FAILED for an unrelated reason, and the file disappeared before
        // the next sync. The server copy (if any) should still be told to discard.
        val result = LocalDeleteSemantics.resolveMissingAsset(
            asset(SyncState.FAILED, serverAssetId = "srv-1"),
        )
        assertEquals(DeleteOutcome.UpdateState(SyncState.PENDING_DISCARD), result)
    }
}
