package com.phonesync.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.phonesync.app.media.MediaKind

/**
 * Local cache of one phone media item and where it stands in the sync pipeline.
 * [clientAssetId] (see [com.phonesync.app.media.ClientAssetIds]) is namespaced by media kind
 * so it is stable across rescans and safe to use as a Room primary key.
 */
@Entity(tableName = "local_assets")
data class LocalAssetEntity(
    @PrimaryKey val clientAssetId: String,
    val mediaStoreId: Long,
    val mediaKind: MediaKind,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val takenAtEpochMs: Long?,
    val dateAddedEpochMs: Long,
    /** MediaStore RELATIVE_PATH, e.g. "DCIM/Camera/" — mirrors phone folders on the PC. */
    val relativePath: String?,
    val contentHash: String?,
    val syncState: SyncState,
    /** Server-assigned asset id once backed up; null until the first successful upload. */
    val serverAssetId: String?,
    /** In-flight resumable upload session id, if any (survives process death). */
    val uploadSessionId: String?,
    /** Bytes already confirmed by the server for [uploadSessionId]. */
    val uploadOffset: Long,
    /** True once the app has actually removed the on-device file after a confirmed archive. */
    val localDeleted: Boolean,
    val lastError: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
