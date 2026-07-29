package com.phonesync.app.media

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest

/**
 * Local deletes for archive / discard.
 *
 * Prefer [MANAGE_MEDIA] silent deletes when granted; otherwise use
 * [MediaStore.createDeleteRequest] (user confirmation).
 *
 * Never call this before the server has confirmed archive/discard.
 */
object MediaDeleteHelper {

    fun hasManageMedia(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return MediaStore.canManageMedia(context)
    }

    fun manageMediaSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * Returns a [PendingIntent] for user-confirmed delete when MANAGE_MEDIA is not granted.
     * When MANAGE_MEDIA is granted, deletes immediately (silent) and returns null.
     */
    fun deleteUris(
        context: Context,
        uris: List<Uri>,
    ): PendingIntent? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasManageMedia(context)) {
            uris.forEach { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris)
        }
        // Pre-R fallback: direct delete (may fail without write permission).
        uris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        return null
    }

    fun intentSenderRequest(pendingIntent: PendingIntent): IntentSenderRequest =
        IntentSenderRequest.Builder(pendingIntent.intentSender).build()

    fun isDeleteGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
}
