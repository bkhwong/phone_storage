package com.phonesync.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonesync.app.media.MediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Exercises the reconstructed Room schema end-to-end (in-memory DB) — the queries in
 * LocalAssetDao are the part of the rebuilt data layer most likely to have a subtle SQL bug
 * (e.g. the archivable/backed-up-total/count-by-state filters) that a pure JVM test can't catch.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalAssetDaoTest {

    private lateinit var db: PhotoSyncDatabase
    private lateinit var dao: LocalAssetDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PhotoSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.localAssetDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        state: SyncState,
        localDeleted: Boolean = false,
        sizeBytes: Long = 1_000,
        serverAssetId: String? = null,
    ) = LocalAssetEntity(
        clientAssetId = id,
        mediaStoreId = id.hashCode().toLong(),
        mediaKind = MediaKind.IMAGE,
        contentUri = "content://media/external/images/media/$id",
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = sizeBytes,
        takenAtEpochMs = null,
        dateAddedEpochMs = 0,
        relativePath = "DCIM/Camera/",
        contentHash = "hash-$id",
        syncState = state,
        serverAssetId = serverAssetId,
        uploadSessionId = null,
        uploadOffset = 0,
        localDeleted = localDeleted,
        lastError = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    @Test
    fun upsertAndGetById_roundTrips() = runBlocking {
        dao.upsert(entity("a", SyncState.PENDING))
        val loaded = dao.getById("a")
        assertEquals("a.jpg", loaded?.displayName)
        assertEquals(SyncState.PENDING, loaded?.syncState)
    }

    @Test
    fun upsert_onConflict_replacesRow() = runBlocking {
        dao.upsert(entity("a", SyncState.PENDING))
        dao.upsert(entity("a", SyncState.BACKED_UP, serverAssetId = "srv-1"))
        val loaded = dao.getById("a")
        assertEquals(SyncState.BACKED_UP, loaded?.syncState)
        assertEquals("srv-1", loaded?.serverAssetId)
    }

    @Test
    fun countByStates_countsOnlyMatchingStates() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("a", SyncState.PENDING),
                entity("b", SyncState.PENDING),
                entity("c", SyncState.UPLOADING),
                entity("d", SyncState.BACKED_UP),
            ),
        )
        assertEquals(2, dao.count(listOf(SyncState.PENDING)))
        assertEquals(3, dao.count(listOf(SyncState.PENDING, SyncState.UPLOADING)))
        assertEquals(1, dao.count(listOf(SyncState.BACKED_UP)))
        assertEquals(0, dao.count(listOf(SyncState.FAILED)))
    }

    @Test
    fun observeArchivable_excludesLocallyDeletedAndPendingItems() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("pending", SyncState.PENDING),
                entity("backedUpOnDevice", SyncState.BACKED_UP, sizeBytes = 500),
                entity("archivedOnDevice", SyncState.ARCHIVED, sizeBytes = 2_000),
                entity("archivedAndDeleted", SyncState.ARCHIVED, localDeleted = true),
            ),
        )
        val archivable = dao.observeArchivable().first()
        val ids = archivable.map { it.clientAssetId }
        assertEquals(listOf("archivedOnDevice", "backedUpOnDevice"), ids) // biggest first
    }

    @Test
    fun observeArchivableCount_matchesObserveArchivable() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("a", SyncState.BACKED_UP),
                entity("b", SyncState.ARCHIVED, localDeleted = true),
                entity("c", SyncState.PENDING),
            ),
        )
        assertEquals(1, dao.observeArchivableCount().first())
    }

    @Test
    fun observeBackedUpTotalCount_includesLocallyDeletedArchivedItems() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("a", SyncState.BACKED_UP),
                entity("b", SyncState.ARCHIVED, localDeleted = true),
                entity("c", SyncState.PENDING),
            ),
        )
        // Unlike observeArchivableCount, this counts everything the server has ever
        // confirmed regardless of whether it's still physically on the device.
        assertEquals(2, dao.observeBackedUpTotalCount().first())
    }

    @Test
    fun resetByServerAssetId_returnsRowToPendingAndClearsServerFields() = runBlocking {
        dao.upsert(entity("a", SyncState.BACKED_UP, serverAssetId = "srv-1"))
        val updated = dao.resetByServerAssetId("srv-1")
        assertEquals(1, updated)
        val reloaded = dao.getById("a")
        assertEquals(SyncState.PENDING, reloaded?.syncState)
        assertNull(reloaded?.serverAssetId)
    }

    @Test
    fun resetByServerAssetId_noMatch_updatesNothing() = runBlocking {
        dao.upsert(entity("a", SyncState.BACKED_UP, serverAssetId = "srv-1"))
        val updated = dao.resetByServerAssetId("does-not-exist")
        assertEquals(0, updated)
    }

    @Test
    fun deleteByIds_removesOnlySpecifiedRows() = runBlocking {
        dao.upsertAll(listOf(entity("a", SyncState.PENDING), entity("b", SyncState.PENDING)))
        dao.deleteByIds(listOf("a"))
        assertNull(dao.getById("a"))
        assertTrue(dao.getById("b") != null)
    }

    @Test
    fun clearAll_removesEverything() = runBlocking {
        dao.upsertAll(listOf(entity("a", SyncState.PENDING), entity("b", SyncState.BACKED_UP)))
        dao.clearAll()
        assertEquals(0, dao.allClientAssetIds().size)
    }

    @Test
    fun observeBytesByStates_sumsOnlyMatchingStates() = runBlocking {
        dao.upsertAll(
            listOf(
                entity("a", SyncState.PENDING, sizeBytes = 1_000),
                entity("b", SyncState.PENDING, sizeBytes = 2_000),
                entity("c", SyncState.BACKED_UP, sizeBytes = 9_000),
            ),
        )
        assertEquals(3_000L, dao.observeBytesByStates(listOf(SyncState.PENDING)).first())
    }
}
