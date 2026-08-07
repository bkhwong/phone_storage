@file:Suppress("DEPRECATION")

package com.phonesync.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow

private const val FILE_NAME = "photo_sync_secure_prefs"
private const val KEY_TOKEN = "device_token"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_BASE_URL = "server_base_url"
private const val KEY_INTERVAL_MINUTES = "sync_interval_minutes"
private const val KEY_ALLOW_CELLULAR = "allow_cellular"
private const val KEY_BATTERY_GUIDANCE_SEEN = "battery_guidance_seen"
private const val KEY_LAST_SYNC_EPOCH_MS = "last_sync_epoch_ms"

const val DEFAULT_SYNC_INTERVAL_MINUTES = 60
const val MIN_SYNC_INTERVAL_MINUTES = 15
const val MAX_SYNC_INTERVAL_MINUTES = 240

/**
 * Pairing credentials and user settings, backed by [EncryptedSharedPreferences] so the device
 * token survives process death but never lands on disk in plaintext. Reads/writes are
 * synchronous (SharedPreferences is memory-cached after first load) so this is safe to call
 * from plain lambdas (e.g. a Slider's `onValueChangeFinished`) as well as from workers, in
 * addition to exposing hot [Flow]s for Compose.
 *
 * `androidx.security.crypto` 1.1.0 marks [EncryptedSharedPreferences]/[MasterKey] as deprecated
 * with no stable drop-in replacement shipped yet; suppressed rather than worked around.
 */
@Suppress("DEPRECATION")
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences by lazy { buildPrefs(context.applicationContext) }

    private fun buildPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val deviceToken: Flow<String?> = stringFlow(KEY_TOKEN)
    val serverBaseUrl: Flow<String?> = stringFlow(KEY_BASE_URL)
    val syncIntervalMinutes: Flow<Int> = intFlow(KEY_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)
    val allowCellular: Flow<Boolean> = boolFlow(KEY_ALLOW_CELLULAR, false)
    val batteryGuidanceSeen: Flow<Boolean> = boolFlow(KEY_BATTERY_GUIDANCE_SEEN, false)
    val lastSyncEpochMs: Flow<Long> = longFlow(KEY_LAST_SYNC_EPOCH_MS, 0L)

    fun isPaired(): Boolean = !currentToken().isNullOrBlank()

    fun currentToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun currentBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun currentDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun lastSyncEpochMsBlocking(): Long = prefs.getLong(KEY_LAST_SYNC_EPOCH_MS, 0L)

    fun currentAllowCellular(): Boolean = prefs.getBoolean(KEY_ALLOW_CELLULAR, false)

    fun currentSyncIntervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)

    fun savePairing(baseUrl: String, token: String, deviceId: String) {
        prefs.edit {
            putString(KEY_BASE_URL, baseUrl)
            putString(KEY_TOKEN, token)
            putString(KEY_DEVICE_ID, deviceId)
        }
    }

    /** Forgets the token/device id but keeps the server address and sync settings for convenience. */
    fun clearPairing() {
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_DEVICE_ID)
        }
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_INTERVAL_MINUTES, minutes.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)) }
    }

    fun setAllowCellular(allow: Boolean) {
        prefs.edit { putBoolean(KEY_ALLOW_CELLULAR, allow) }
    }

    fun setBatteryGuidanceSeen(seen: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_GUIDANCE_SEEN, seen) }
    }

    fun setLastSyncEpochMs(epochMs: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_EPOCH_MS, epochMs) }
    }

    private fun stringFlow(key: String): Flow<String?> = preferenceFlow(key) { prefs.getString(key, null) }

    private fun intFlow(key: String, default: Int): Flow<Int> = preferenceFlow(key) { prefs.getInt(key, default) }

    private fun boolFlow(key: String, default: Boolean): Flow<Boolean> =
        preferenceFlow(key) { prefs.getBoolean(key, default) }

    private fun longFlow(key: String, default: Long): Flow<Long> = preferenceFlow(key) { prefs.getLong(key, default) }

    private fun <T> preferenceFlow(key: String, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
