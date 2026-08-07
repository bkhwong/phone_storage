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

    /** Outcome of attempting to delete on-device files, so callers only mark rows as
     * "freed" for URIs that were *actually* removed rather than assuming all-or-nothing. */
    sealed interface DeleteOutcome {
        /** MANAGE_MEDIA (or pre-R) path: deletes happen immediately, per-URI result known now. */
        data class Immediate(val deletedUris: List<Uri>, val failedUris: List<Uri>) : DeleteOutcome

        /** User confirmation required; caller must launch [pendingIntent] and await the result. */
        data class RequiresConfirmation(val pendingIntent: PendingIntent) : DeleteOutcome
    }

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

    fun deleteUris(context: Context, uris: List<Uri>): DeleteOutcome {
        if (uris.isEmpty()) return DeleteOutcome.Immediate(emptyList(), emptyList())

        val canDeleteImmediately = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasManageMedia(context))

        if (canDeleteImmediately) {
            val deleted = mutableListOf<Uri>()
            val failed = mutableListOf<Uri>()
            uris.forEach { uri ->
                val rows = runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
                if (rows > 0) deleted += uri else failed += uri
            }
            return DeleteOutcome.Immediate(deleted, failed)
        }

        return DeleteOutcome.RequiresConfirmation(MediaStore.createDeleteRequest(context.contentResolver, uris))
    }

    fun intentSenderRequest(pendingIntent: PendingIntent): IntentSenderRequest =
        IntentSenderRequest.Builder(pendingIntent.intentSender).build()

    fun isDeleteGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
}
