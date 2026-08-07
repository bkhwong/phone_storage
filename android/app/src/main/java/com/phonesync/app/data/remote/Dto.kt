package com.phonesync.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Must match `schemas.DEFAULT_CHUNK_SIZE` on the server (4 MiB). */
const val DEFAULT_CHUNK_SIZE = 4L * 1024 * 1024

@JsonClass(generateAdapter = true)
data class HealthDto(
    val ok: Boolean = true,
    val status: String = "ok",
    val version: String? = null,
)

@JsonClass(generateAdapter = true)
data class PairRequest(val pin: String)

@JsonClass(generateAdapter = true)
data class PairResponse(
    @param:Json(name = "device_token") val deviceToken: String,
    @param:Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class AssetDto(
    val id: String,
    @param:Json(name = "content_hash") val contentHash: String,
    val state: String,
    @param:Json(name = "size_bytes") val sizeBytes: Long,
    @param:Json(name = "original_filename") val originalFilename: String? = null,
    @param:Json(name = "mime_type") val mimeType: String? = null,
    @param:Json(name = "taken_at") val takenAt: String? = null,
    @param:Json(name = "client_asset_id") val clientAssetId: String? = null,
    @param:Json(name = "created_at") val createdAt: String? = null,
    @param:Json(name = "updated_at") val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AssetListResponse(
    val items: List<AssetDto> = emptyList(),
    @param:Json(name = "next_cursor") val nextCursor: String? = null,
)

@JsonClass(generateAdapter = true)
data class HashLookupRequest(val hashes: List<String>)

@JsonClass(generateAdapter = true)
data class HashLookupMatch(
    val hash: String,
    @param:Json(name = "asset_id") val assetId: String,
    val state: String? = null,
)

@JsonClass(generateAdapter = true)
data class HashLookupResponse(val matches: List<HashLookupMatch> = emptyList())

@JsonClass(generateAdapter = true)
data class UploadInitRequest(
    @param:Json(name = "content_hash") val contentHash: String,
    @param:Json(name = "size_bytes") val sizeBytes: Long,
    @param:Json(name = "original_filename") val originalFilename: String?,
    @param:Json(name = "mime_type") val mimeType: String?,
    @param:Json(name = "taken_at") val takenAt: String? = null,
    @param:Json(name = "client_asset_id") val clientAssetId: String? = null,
    @param:Json(name = "relative_path") val relativePath: String? = null,
)

@JsonClass(generateAdapter = true)
data class UploadInitResponse(
    @param:Json(name = "upload_id") val uploadId: String,
    @param:Json(name = "chunk_size") val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val offset: Long = 0,
    @param:Json(name = "existing_asset_id") val existingAssetId: String? = null,
)

@JsonClass(generateAdapter = true)
data class UploadChunkResponse(
    @param:Json(name = "upload_id") val uploadId: String? = null,
    val offset: Long = 0,
    val complete: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UploadCompleteRequest(@param:Json(name = "content_hash") val contentHash: String? = null)

@JsonClass(generateAdapter = true)
data class DiscardResponse(val id: String, val discarded: Boolean = true)
