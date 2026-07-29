package com.phonesync.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class MediaStoreItem(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val takenAtEpochMs: Long?,
    val dateAddedEpochMs: Long,
)

object HashUtil {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    suspend fun sha256OfUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(1024 * 256)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Cannot open $uri")
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

object ClientAssetIds {
    fun forMediaStore(mediaStoreId: Long): String = "ms_$mediaStoreId"

    fun random(): String = UUID.randomUUID().toString()
}

class MediaStoreScanner(private val context: Context) {

    suspend fun scanAll(): List<MediaStoreItem> = withContext(Dispatchers.IO) {
        val images = queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videos = queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        (images + videos).distinctBy { it.mediaStoreId }
    }

    private fun queryCollection(collection: Uri): List<MediaStoreItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val items = mutableListOf<MediaStoreItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                if (size <= 0L) continue
                val taken = cursor.getLong(takenCol).takeIf { it > 0L }
                val addedSec = cursor.getLong(addedCol)
                items += MediaStoreItem(
                    mediaStoreId = id,
                    contentUri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameCol) ?: "media_$id",
                    mimeType = cursor.getString(mimeCol) ?: "application/octet-stream",
                    sizeBytes = size,
                    takenAtEpochMs = taken,
                    dateAddedEpochMs = addedSec * 1000L,
                )
            }
        }
        return items
    }
}
