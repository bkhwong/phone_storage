package com.phonesync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAssetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalAssetEntity>)

    @Update
    suspend fun update(item: LocalAssetEntity)

    @Query("SELECT * FROM local_assets WHERE clientAssetId = :id")
    suspend fun getById(id: String): LocalAssetEntity?

    @Query("SELECT * FROM local_assets WHERE clientAssetId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LocalAssetEntity>

    @Query("SELECT clientAssetId FROM local_assets")
    suspend fun allClientAssetIds(): List<String>

    @Query("DELETE FROM local_assets WHERE clientAssetId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM local_assets")
    suspend fun clearAll()

    @Query("SELECT * FROM local_assets WHERE syncState IN (:states) ORDER BY dateAddedEpochMs DESC")
    suspend fun getByStates(states: List<SyncState>): List<LocalAssetEntity>

    @Query("SELECT * FROM local_assets WHERE syncState IN (:states) ORDER BY dateAddedEpochMs DESC")
    fun observeByStates(states: List<SyncState>): Flow<List<LocalAssetEntity>>

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncState IN (:states)")
    suspend fun count(states: List<SyncState>): Int

    @Query("SELECT COUNT(*) FROM local_assets WHERE syncState IN (:states)")
    fun observeCountByStates(states: List<SyncState>): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM local_assets WHERE syncState IN (:states)")
    fun observeBytesByStates(states: List<SyncState>): Flow<Long>

    /**
     * Items the server already has a verified copy of and that are still physically present
     * on this device (including ones already marked archived where the on-device delete
     * itself failed or was cancelled, so the user can retry freeing that space).
     * Biggest files first so freeing space is efficient.
     */
    @Query(
        "SELECT * FROM local_assets WHERE localDeleted = 0 " +
            "AND syncState IN ('BACKED_UP', 'ARCHIVED') ORDER BY sizeBytes DESC",
    )
    fun observeArchivable(): Flow<List<LocalAssetEntity>>

    @Query(
        "SELECT COUNT(*) FROM local_assets WHERE localDeleted = 0 " +
            "AND syncState IN ('BACKED_UP', 'ARCHIVED')",
    )
    fun observeArchivableCount(): Flow<Int>

    /** Everything the server has ever confirmed, regardless of whether it's still on-device. */
    @Query("SELECT COUNT(*) FROM local_assets WHERE syncState IN ('BACKED_UP', 'ARCHIVED')")
    fun observeBackedUpTotalCount(): Flow<Int>

    /**
     * A server discard (e.g. from the Browse screen) invalidates our record of that upload.
     * If the file is still on-device, resetting it to PENDING makes the next sync re-back it up
     * rather than silently leaving a dangling reference to a deleted server asset.
     */
    @Query(
        "UPDATE local_assets SET syncState = 'PENDING', serverAssetId = NULL, " +
            "uploadSessionId = NULL, uploadOffset = 0 WHERE serverAssetId = :serverAssetId",
    )
    suspend fun resetByServerAssetId(serverAssetId: String): Int
}
