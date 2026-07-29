package com.phonesync.app.sync

import com.phonesync.app.data.remote.DEFAULT_CHUNK_SIZE

/**
 * Pure chunk/offset math for resumable uploads (no I/O).
 */
object UploadChunking {
    fun resolveChunkSize(serverChunkSize: Long): Long =
        serverChunkSize.takeIf { it > 0L } ?: DEFAULT_CHUNK_SIZE

    /** Bytes to read for the next chunk given remaining file size. */
    fun nextReadLength(chunkSize: Long, offset: Long, sizeBytes: Long): Int {
        if (offset >= sizeBytes || chunkSize <= 0L) return 0
        val remaining = sizeBytes - offset
        val capped = minOf(chunkSize, remaining, Int.MAX_VALUE.toLong())
        return capped.toInt()
    }

    fun advanceOffset(offset: Long, bytesRead: Int): Long {
        require(bytesRead >= 0) { "bytesRead must be >= 0" }
        return offset + bytesRead.toLong()
    }

    fun isComplete(offset: Long, sizeBytes: Long): Boolean = offset >= sizeBytes

    /** How many full/partial chunks are needed from [startOffset] to [sizeBytes]. */
    fun chunkCount(chunkSize: Long, startOffset: Long, sizeBytes: Long): Int {
        val size = resolveChunkSize(chunkSize)
        if (startOffset >= sizeBytes) return 0
        var offset = startOffset
        var count = 0
        while (offset < sizeBytes) {
            val n = nextReadLength(size, offset, sizeBytes)
            if (n <= 0) break
            offset = advanceOffset(offset, n)
            count++
        }
        return count
    }
}
