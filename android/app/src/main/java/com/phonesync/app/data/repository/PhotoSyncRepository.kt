package com.phonesync.app.data.repository

import android.content.Context
import android.net.Uri
import com.phonesync.app.data.local.LocalAssetDao
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.local.SyncState
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.remote.ApiClientFactory
import com.phonesync.app.data.remote.AssetDto
import com.phonesync.app.data.remote.HashLookupRequest
import com.phonesync.app.data.remote.PairRequest
import com.phonesync.app.data.remote.normalizeBaseUrl
import com.phonesync.app.media.ClientAssetIds
import com.phonesync.app.media.HashUtil
import com.phonesync.app.media.MediaStoreScanner
import com.phonesync.app.sync.SyncWorker
import com.phonesync.app.sync.UploadEngine
import com.phonesync.app.sync.MigrationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val ACTIVE_STATES = listOf(SyncState.PENDING, SyncState.UPLOADING, SyncState.FAILED)

/** Files at or below this size upload in one request; larger files use the resumable API. */
const val SIMPLE_UPLOAD_MAX_BYTES = 8L * 1024 * 1024

/**
 * Orchestrates local scanning, hashing, uploading, archiving, browsing, and pairing.
 * Screens/ViewModels and [SyncWorker]/[MigrationWorker] never talk to Room, Retrofit, or
 * MediaStore directly — everything goes through here so there is exactly one place that
 * understands the sync state machine.
 */
class PhotoSyncRepository(
    private val context: Context,
    private val dao: LocalAssetDao,
    private val prefs: SecurePrefs,
    private val apiFactory: ApiClientFactory,
    private val scanner: MediaStoreScanner,
    private val uploadEngine: UploadEngine,
) {

    // ---- Pairing -----------------------------------------------------------------------

    suspend fun pair(serverUrl: String, pin: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(serverUrl)
        require(normalized.isNotBlank()) { "Enter your PC's address" }
        val api = apiFactory.createFor(normalized)
        val response = api.pair(PairRequest(pin))
        prefs.savePairing(normalized, response.deviceToken, response.deviceId)
        SyncWorker.enqueuePeriodic(context, prefs.syncIntervalMinutes.first(), prefs.allowCellular.first())
    }

    suspend fun unpair() = withContext(Dispatchers.IO) {
        SyncWorker.cancelAll(context)
        MigrationWorker.cancel(context)
        dao.clearAll()
        prefs.clearPairing()
        apiFactory.reset()
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        runCatching { apiFactory.create().health().ok }.getOrDefault(false)
    }

    /** One call that does everything a manual "sync now" tap should: rescan, reconcile
     * pending discards, and (re)kick a one-shot background upload pass. */
    suspend fun triggerSyncNow() = withContext(Dispatchers.IO) {
        scanAndReconcileLocal()
        processDiscardIntents()
        SyncWorker.enqueueNow(context, prefs.allowCellular.first())
    }

    /**
     * Explicit "Retry" from Home: clears sticky errors on [SyncState.FAILED] rows, flips them
     * back to [SyncState.PENDING] so they sort with fresh work, then kicks [triggerSyncNow].
     * Failed items are already eligible for upload on the next pass — this just makes the
     * user-initiated retry obvious in the UI (error text disappears, state label updates).
     *
     * @return how many failed rows were requeued
     */
    suspend fun retryFailedUploads(): Int = withContext(Dispatchers.IO) {
        val count = dao.requeueFailed(now())
        triggerSyncNow()
        count
    }

    /** Rescans, then starts (or resumes) the long-running foreground migration worker. */
    suspend fun startMigration() = withContext(Dispatchers.IO) {
        scanAndReconcileLocal()
        MigrationWorker.enqueue(context, prefs.allowCellular.first())
    }

    fun cancelMigration() {
        MigrationWorker.cancel(context)
    }

    // ---- Settings -------------------------------------------------------------------------

    fun updateSyncInterval(minutes: Int) {
        prefs.setSyncIntervalMinutes(minutes)
        SyncWorker.enqueuePeriodic(context, minutes, prefs.currentAllowCellular())
    }

    fun updateAllowCellular(allow: Boolean) {
        prefs.setAllowCellular(allow)
        SyncWorker.enqueuePeriodic(context, prefs.currentSyncIntervalMinutes(), allow)
    }

    // ---- Scanning & reconciliation ------------------------------------------------------

    /**
     * Rescans MediaStore, inserts newly discovered items as [SyncState.PENDING], and
     * reconciles rows whose files disappeared since the last scan (see [LocalDeleteSemantics]).
     */
    suspend fun scanAndReconcileLocal(): ScanResult = withContext(Dispatchers.IO) {
        val discovered = scanner.scanAll()
            .associateBy { ClientAssetIds.forMediaStore(it.mediaKind, it.mediaStoreId) }
        val knownIds = dao.allClientAssetIds().toSet()
        val now = System.currentTimeMillis()

        val toInsert = discovered.filterKeys { it !in knownIds }.map { (id, item) ->
            LocalAssetEntity(
                clientAssetId = id,
                mediaStoreId = item.mediaStoreId,
                mediaKind = item.mediaKind,
                contentUri = item.contentUri.toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                takenAtEpochMs = item.takenAtEpochMs,
                dateAddedEpochMs = item.dateAddedEpochMs,
                relativePath = item.relativePath,
                contentHash = null,
                syncState = SyncState.PENDING,
                serverAssetId = null,
                uploadSessionId = null,
                uploadOffset = 0,
                localDeleted = false,
                lastError = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        }
        if (toInsert.isNotEmpty()) dao.upsertAll(toInsert)

        val missingIds = knownIds - discovered.keys
        var missingCount = 0
        if (missingIds.isNotEmpty()) {
            val missingEntities = dao.getByIds(missingIds.toList())
            val toUpdate = mutableListOf<LocalAssetEntity>()
            val toRemove = mutableListOf<String>()
            for (entity in missingEntities) {
                when (val outcome = LocalDeleteSemantics.resolveMissingAsset(entity)) {
                    is DeleteOutcome.UpdateState -> {
                        if (outcome.state != entity.syncState) {
                            toUpdate += entity.copy(syncState = outcome.state, updatedAtEpochMs = now)
                            if (outcome.state == SyncState.PENDING_DISCARD) missingCount++
                        }
                    }
                    DeleteOutcome.RemoveLocalRow -> toRemove += entity.clientAssetId
                }
            }
            if (toUpdate.isNotEmpty()) dao.upsertAll(toUpdate)
            if (toRemove.isNotEmpty()) dao.deleteByIds(toRemove)
        }

        ScanResult(newlyDiscovered = toInsert.size, missingLocally = missingCount)
    }

    /** Tells the server to forget items that were deleted from Gallery without archiving first. */
    suspend fun processDiscardIntents(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getByStates(listOf(SyncState.PENDING_DISCARD))
        var processed = 0
        for (entity in pending) {
            val serverId = entity.serverAssetId
            val ok = if (serverId == null) {
                true
            } else {
                runCatching { apiFactory.create().discardAsset(serverId) }.isSuccess
            }
            if (ok) {
                dao.deleteByIds(listOf(entity.clientAssetId))
                processed++
            }
        }
        processed
    }

    // ---- Uploading ------------------------------------------------------------------------

    /**
     * Uploads up to [limit] pending/failed items. Before uploading anything, does a bulk
     * server-side hash lookup so reinstalls / re-scans don't re-upload files the server
     * already has.
     */
    suspend fun uploadPending(
        limit: Int = 30,
        onProgress: ((LocalAssetEntity, Long, Long) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        val candidates = dao.getByStates(ACTIVE_STATES).take(limit)
        if (candidates.isEmpty()) return@withContext 0

        val hashed = candidates.mapNotNull { asset ->
            runCatching { ensureHash(asset) }.getOrElse { e ->
                dao.update(asset.copy(syncState = SyncState.FAILED, lastError = shortError(e), updatedAtEpochMs = now()))
                null
            }
        }

        val byHash = hashed.filter { !it.contentHash.isNullOrBlank() }.associateBy { it.contentHash!! }
        val matches = if (byHash.isNotEmpty()) {
            runCatching { apiFactory.create().lookupHashes(HashLookupRequest(byHash.keys.toList())) }
                .getOrNull()?.matches.orEmpty()
        } else {
            emptyList()
        }

        var success = 0
        val skipUpload = mutableSetOf<String>()
        for (match in matches) {
            val local = byHash[match.hash] ?: continue
            dao.update(
                local.copy(
                    syncState = SyncState.BACKED_UP,
                    serverAssetId = match.assetId,
                    uploadSessionId = null,
                    uploadOffset = 0,
                    lastError = null,
                    updatedAtEpochMs = now(),
                ),
            )
            skipUpload += local.clientAssetId
            success++
        }

        for (asset in hashed.filterNot { it.clientAssetId in skipUpload }) {
            runCatching { uploadOne(asset, onProgress) }
                .onSuccess { serverId ->
                    dao.update(
                        asset.copy(
                            syncState = SyncState.BACKED_UP,
                            serverAssetId = serverId,
                            uploadSessionId = null,
                            uploadOffset = 0,
                            lastError = null,
                            updatedAtEpochMs = now(),
                        ),
                    )
                    success++
                }
                .onFailure { e ->
                    dao.update(asset.copy(syncState = SyncState.FAILED, lastError = shortError(e), updatedAtEpochMs = now()))
                }
        }

        if (success > 0) prefs.setLastSyncEpochMs(now())
        success
    }

    private suspend fun ensureHash(asset: LocalAssetEntity): LocalAssetEntity {
        if (!asset.contentHash.isNullOrBlank()) return asset
        val hash = HashUtil.sha256OfUri(context, Uri.parse(asset.contentUri))
        val updated = asset.copy(contentHash = hash, updatedAtEpochMs = now())
        dao.update(updated)
        return updated
    }

    private suspend fun uploadOne(
        asset: LocalAssetEntity,
        onProgress: ((LocalAssetEntity, Long, Long) -> Unit)?,
    ): String {
        val result = if (asset.sizeBytes <= SIMPLE_UPLOAD_MAX_BYTES) {
            uploadEngine.uploadSimple(asset)
        } else {
            uploadEngine.uploadResumable(asset, onProgress)
        }
        return result.serverId
    }

    // ---- Status ---------------------------------------------------------------------------

    fun observePending(): Flow<List<LocalAssetEntity>> = dao.observeByStates(ACTIVE_STATES)

    fun observePendingCount(): Flow<Int> = dao.observeCountByStates(ACTIVE_STATES)

    fun observeArchivable(): Flow<List<LocalAssetEntity>> = dao.observeArchivable()

    fun observeStatus(): Flow<StatusSnapshot> = combine(
        dao.observeCountByStates(ACTIVE_STATES),
        dao.observeBytesByStates(ACTIVE_STATES),
        dao.observeCountByStates(listOf(SyncState.FAILED)),
        dao.observeArchivableCount(),
        dao.observeBackedUpTotalCount(),
    ) { pendingCount, pendingBytes, failedCount, onDevice, backedUpTotal ->
        StatusPartial(pendingCount, pendingBytes, failedCount, onDevice, backedUpTotal)
    }.combine(prefs.lastSyncEpochMs) { partial, lastSync ->
        StatusSnapshot(
            pendingCount = partial.pendingCount,
            pendingBytes = partial.pendingBytes,
            failedCount = partial.failedCount,
            backedUpOnDeviceCount = partial.onDeviceCount,
            backedUpTotalCount = partial.backedUpTotalCount,
            lastSyncEpochMs = lastSync,
            pcReachable = null,
        )
    }

    private data class StatusPartial(
        val pendingCount: Int,
        val pendingBytes: Long,
        val failedCount: Int,
        val onDeviceCount: Int,
        val backedUpTotalCount: Int,
    )

    suspend fun enrichStatus(base: StatusSnapshot): StatusSnapshot = base.copy(pcReachable = checkHealth())

    // ---- Archive / free space ---------------------------------------------------------------

    /** Confirms archive with the server for each item; returns the ones the server accepted. */
    suspend fun archiveOnServer(items: List<LocalAssetEntity>): List<LocalAssetEntity> = withContext(Dispatchers.IO) {
        val confirmed = mutableListOf<LocalAssetEntity>()
        for (item in items) {
            val serverId = item.serverAssetId ?: continue
            val ok = runCatching { apiFactory.create().archiveAsset(serverId) }.isSuccess
            if (ok) {
                val updated = item.copy(syncState = SyncState.ARCHIVED, updatedAtEpochMs = now())
                dao.update(updated)
                confirmed += updated
            }
        }
        confirmed
    }

    /** Only call after the on-device file is actually gone (system delete confirmed). */
    suspend fun markLocalDeletedAfterArchive(items: List<LocalAssetEntity>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        dao.upsertAll(items.map { it.copy(syncState = SyncState.ARCHIVED, localDeleted = true, updatedAtEpochMs = now()) })
    }

    // ---- Browse / discard on server -----------------------------------------------------

    suspend fun browseAssets(
        state: String?,
        cursor: String?,
        limit: Int = 60,
    ): Pair<List<AssetDto>, String?> = withContext(Dispatchers.IO) {
        val response = apiFactory.create().listAssets(state = state, limit = limit, cursor = cursor)
        response.items to response.nextCursor
    }

    suspend fun discardServerAsset(assetId: String, local: LocalAssetEntity?) = withContext(Dispatchers.IO) {
        apiFactory.create().discardAsset(assetId)
        if (local != null) {
            dao.deleteByIds(listOf(local.clientAssetId))
        } else {
            dao.resetByServerAssetId(assetId)
        }
    }

    fun authHeader(): Pair<String, String>? = prefs.currentToken()?.let { ApiClientFactory.AUTH_HEADER to it }

    fun thumbnailUrl(assetId: String): String = "${baseUrlOrEmpty()}/api/assets/$assetId/thumbnail"

    fun originalUrl(assetId: String): String = "${baseUrlOrEmpty()}/api/assets/$assetId/original"

    private fun baseUrlOrEmpty(): String = prefs.currentBaseUrl()?.let(::normalizeBaseUrl).orEmpty()

    private fun now(): Long = System.currentTimeMillis()

    private fun shortError(e: Throwable): String = (e.message ?: e::class.simpleName ?: "Unknown error").take(200)
}
