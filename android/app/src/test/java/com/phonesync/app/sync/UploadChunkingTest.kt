package com.phonesync.app.sync

import com.phonesync.app.data.remote.DEFAULT_CHUNK_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadChunkingTest {
    @Test
    fun resolveChunkSize_usesServerWhenPositive() {
        assertEquals(1_000_000L, UploadChunking.resolveChunkSize(1_000_000L))
    }

    @Test
    fun resolveChunkSize_fallsBackWhenZeroOrNegative() {
        assertEquals(DEFAULT_CHUNK_SIZE, UploadChunking.resolveChunkSize(0L))
        assertEquals(DEFAULT_CHUNK_SIZE, UploadChunking.resolveChunkSize(-1L))
    }

    @Test
    fun nextReadLength_atStart() {
        assertEquals(4, UploadChunking.nextReadLength(chunkSize = 4, offset = 0, sizeBytes = 10))
    }

    @Test
    fun nextReadLength_nearEnd_partialChunk() {
        assertEquals(2, UploadChunking.nextReadLength(chunkSize = 4, offset = 8, sizeBytes = 10))
    }

    @Test
    fun nextReadLength_whenComplete_isZero() {
        assertEquals(0, UploadChunking.nextReadLength(chunkSize = 4, offset = 10, sizeBytes = 10))
        assertEquals(0, UploadChunking.nextReadLength(chunkSize = 4, offset = 11, sizeBytes = 10))
    }

    @Test
    fun advanceOffset_progresses() {
        assertEquals(7L, UploadChunking.advanceOffset(3L, 4))
    }

    @Test
    fun isComplete() {
        assertFalse(UploadChunking.isComplete(0, 10))
        assertTrue(UploadChunking.isComplete(10, 10))
        assertTrue(UploadChunking.isComplete(12, 10))
    }

    @Test
    fun chunkCount_exactMultiples() {
        assertEquals(3, UploadChunking.chunkCount(chunkSize = 4, startOffset = 0, sizeBytes = 12))
    }

    @Test
    fun chunkCount_withRemainderAndResume() {
        assertEquals(3, UploadChunking.chunkCount(chunkSize = 4, startOffset = 0, sizeBytes = 10))
        // resume after first chunk
        assertEquals(2, UploadChunking.chunkCount(chunkSize = 4, startOffset = 4, sizeBytes = 10))
    }

    @Test
    fun chunkCount_defaultChunkSize_largeFile() {
        val size = DEFAULT_CHUNK_SIZE * 2 + 100
        assertEquals(3, UploadChunking.chunkCount(DEFAULT_CHUNK_SIZE, 0, size))
    }
}
