package com.phonesync.app.sync

import android.content.Context
import android.net.Uri
import com.phonesync.app.data.local.LocalAssetDao
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.local.SyncState
import com.phonesync.app.data.remote.ApiClientFactory
import com.phonesync.app.data.remote.DEFAULT_CHUNK_SIZE
import com.phonesync.app.data.remote.UploadCompleteRequest
import com.phonesync.app.data.remote.UploadInitRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

data class UploadResult(val serverId: String)

/**
 * Skips exactly [byteCount] bytes from [input], looping on [InputStream.skip] because real
 * `ContentResolver` streams (e.g. over FUSE/cloud-backed providers) can under-skip on a single
 * call. Falls back to reading-and-discarding into a scratch buffer if `skip()` returns 0 while
 * the stream still has bytes to give (some stream implementations only support skip via read).
 *
 * Throws [java.io.EOFException] if the stream ends before [byteCount] bytes were skipped.
 */
fun skipFully(input: InputStream, byteCount: Long) {
    var remaining = byteCount
    val scratch = ByteArray(64 * 1024)
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        val toRead = minOf(remaining, scratch.size.toLong()).toInt()
        val read = input.read(scratch, 0, toRead)
        if (read < 0) {
            throw java.io.EOFException("Stream ended after skipping ${byteCount - remaining} of $byteCount bytes")
        }
        remaining -= read
    }
}

class UploadEngine(
    private val context: Context,
    private val dao: LocalAssetDao,
    private val apiFactory: ApiClientFactory,
) {

    suspend fun uploadSimple(asset: LocalAssetEntity): UploadResult {
        val api = apiFactory.create()
        val hash = asset.contentHash ?: error("hash required")
        val uri = Uri.parse(asset.contentUri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read ${asset.contentUri}")

        val mime = asset.mimeType.toMediaTypeOrNull() ?: ApiClientFactory.OCTET_STREAM
        val filePart = MultipartBody.Part.createFormData(
            "file",
            asset.displayName,
            bytes.toRequestBody(mime),
        )
        val response = api.uploadAsset(
            file = filePart,
            contentHash = hash.toRequestBody(ApiClientFactory.TEXT_PLAIN),
            takenAt = formatTakenAt(asset.takenAtEpochMs)?.toRequestBody(ApiClientFactory.TEXT_PLAIN),
            originalFilename = asset.displayName.toRequestBody(ApiClientFactory.TEXT_PLAIN),
            mimeType = asset.mimeType.toRequestBody(ApiClientFactory.TEXT_PLAIN),
            clientAssetId = asset.clientAssetId.toRequestBody(ApiClientFactory.TEXT_PLAIN),
            relativePath = asset.relativePath?.toRequestBody(ApiClientFactory.TEXT_PLAIN),
        )
        return UploadResult(response.id)
    }

    suspend fun uploadResumable(
        asset: LocalAssetEntity,
        onProgress: ((LocalAssetEntity, Long, Long) -> Unit)? = null,
    ): UploadResult {
        val api = apiFactory.create()
        val hash = asset.contentHash ?: error("hash required")

        dao.update(asset.copy(syncState = SyncState.UPLOADING))

        var sessionId = asset.uploadSessionId
        var offset = asset.uploadOffset
        var chunkSize = DEFAULT_CHUNK_SIZE

        if (sessionId.isNullOrBlank()) {
            val init = api.initUpload(
                UploadInitRequest(
                    contentHash = hash,
                    originalFilename = asset.displayName,
                    mimeType = asset.mimeType,
                    sizeBytes = asset.sizeBytes,
                    takenAt = formatTakenAt(asset.takenAtEpochMs),
                    clientAssetId = asset.clientAssetId,
                    relativePath = asset.relativePath,
                ),
            )
            if (!init.existingAssetId.isNullOrBlank()) {
                return UploadResult(init.existingAssetId)
            }
            sessionId = init.uploadId
            offset = init.offset
            chunkSize = UploadChunking.resolveChunkSize(init.chunkSize)
            dao.update(
                asset.copy(
                    uploadSessionId = sessionId,
                    uploadOffset = offset,
                    syncState = SyncState.UPLOADING,
                ),
            )
        }

        val uri = Uri.parse(asset.contentUri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (offset > 0) {
                skipFully(input, offset)
            }
            val buffer = ByteArray(chunkSize.toInt().coerceAtMost(8 * 1024 * 1024))
            while (offset < asset.sizeBytes) {
                val toRead = UploadChunking.nextReadLength(buffer.size.toLong(), offset, asset.sizeBytes)
                var readTotal = 0
                while (readTotal < toRead) {
                    val r = input.read(buffer, readTotal, toRead - readTotal)
                    if (r < 0) break
                    readTotal += r
                }
                if (readTotal <= 0) break

                val chunkBody = object : RequestBody() {
                    override fun contentType() = ApiClientFactory.OCTET_STREAM
                    override fun contentLength() = readTotal.toLong()
                    override fun writeTo(sink: BufferedSink) {
                        sink.write(buffer, 0, readTotal)
                    }
                }

                val response = api.uploadChunk(sessionId!!, offset, chunkBody)
                if (!response.isSuccessful) {
                    error("Chunk upload failed at offset=$offset code=${response.code()}")
                }
                offset = UploadChunking.advanceOffset(offset, readTotal)
                dao.update(
                    asset.copy(
                        uploadSessionId = sessionId,
                        uploadOffset = offset,
                        syncState = SyncState.UPLOADING,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                onProgress?.invoke(asset, offset, asset.sizeBytes)
            }
        } ?: error("Cannot open ${asset.contentUri}")

        val complete = api.completeUpload(sessionId!!, UploadCompleteRequest(contentHash = hash))
        return UploadResult(complete.id)
    }

    private fun formatTakenAt(epochMs: Long?): String? {
        if (epochMs == null || epochMs <= 0L) return null
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))
    }
}
