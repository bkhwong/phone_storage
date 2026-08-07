package com.phonesync.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Matches docs/api-contract.md. Base URL is set dynamically per paired server. */
interface PhotoSyncApi {

    @GET("api/health")
    suspend fun health(): HealthDto

    @POST("api/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    @Multipart
    @POST("api/assets/upload")
    suspend fun uploadAsset(
        @Part file: MultipartBody.Part,
        @Part("content_hash") contentHash: RequestBody,
        @Part("taken_at") takenAt: RequestBody?,
        @Part("original_filename") originalFilename: RequestBody,
        @Part("mime_type") mimeType: RequestBody,
        @Part("client_asset_id") clientAssetId: RequestBody,
        @Part("relative_path") relativePath: RequestBody?,
    ): AssetDto

    @POST("api/assets/by-hash/lookup")
    suspend fun lookupHashes(@Body request: HashLookupRequest): HashLookupResponse

    @GET("api/assets")
    suspend fun listAssets(
        @Query("state") state: String?,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): AssetListResponse

    @GET("api/assets/{id}")
    suspend fun getAsset(@Path("id") id: String): AssetDto

    @POST("api/assets/{id}/archive")
    suspend fun archiveAsset(@Path("id") id: String): AssetDto

    @POST("api/assets/{id}/discard")
    suspend fun discardAsset(@Path("id") id: String): DiscardResponse

    @POST("api/uploads/init")
    suspend fun initUpload(@Body request: UploadInitRequest): UploadInitResponse

    @PUT("api/uploads/{id}/chunk")
    suspend fun uploadChunk(
        @Path("id") id: String,
        @Query("offset") offset: Long,
        @Body body: RequestBody,
    ): Response<UploadChunkResponse>

    @POST("api/uploads/{id}/complete")
    suspend fun completeUpload(
        @Path("id") id: String,
        @Body request: UploadCompleteRequest,
    ): AssetDto

    @POST("api/uploads/{id}/abort")
    suspend fun abortUpload(@Path("id") id: String): Map<String, String>
}
