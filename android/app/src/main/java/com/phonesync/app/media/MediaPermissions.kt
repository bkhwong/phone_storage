package com.phonesync.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Media-library permission helpers shared by [com.phonesync.app.MainActivity] (gate) and the
 * Home screen (partial-access banner). Android 14+ ("Upside Down Cake") introduced a third
 * grant option — "Selected photos and videos" — which only sets
 * [Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED]. That is enough to use the app, but
 * MediaStore will only expose the user-picked subset, so Home surfaces a banner offering to
 * request full library access.
 */
enum class MediaAccessLevel {
    /** No usable media permission — gate the app. */
    None,

    /** Android 14+ "Select photos" subset only. */
    Partial,

    /** Full images + videos (or legacy READ_EXTERNAL_STORAGE). */
    Full,
}

object MediaPermissions {

    fun required(): Array<String> = when {
        // Requesting all three together is the documented pattern for surfacing the system's
        // "Allow all / Select photos and videos / Don't allow" three-way choice.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun accessLevel(context: Context): MediaAccessLevel {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fullAccess = granted(Manifest.permission.READ_MEDIA_IMAGES) &&
                granted(Manifest.permission.READ_MEDIA_VIDEO)
            if (fullAccess) return MediaAccessLevel.Full
            if (granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                return MediaAccessLevel.Partial
            }
            return MediaAccessLevel.None
        }
        return if (required().all(::granted)) MediaAccessLevel.Full else MediaAccessLevel.None
    }

    fun hasMediaAccess(context: Context): Boolean =
        accessLevel(context) != MediaAccessLevel.None

    fun hasPartialMediaAccess(context: Context): Boolean =
        accessLevel(context) == MediaAccessLevel.Partial

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
}
