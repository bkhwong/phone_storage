package com.phonesync.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phonesync.app.PhotoSyncApp
import com.phonesync.app.R
import com.phonesync.app.data.local.SyncState
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PhotoSyncApp
        val repo = app.container.repository
        val prefs = app.container.prefs
        if (!prefs.isPaired()) return Result.success()

        return try {
            if (!repo.checkHealth()) return Result.retry()
            repo.scanAndReconcileLocal()
            repo.processDiscardIntents()
            repo.uploadPending(limit = 30)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure(
                workDataOf("error" to (e.message ?: "sync failed")),
            )
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "photo_sync_periodic"
        const val UNIQUE_ONESHOT = "photo_sync_oneshot"

        fun enqueuePeriodic(context: Context, intervalMinutes: Int, allowCellular: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED,
                )
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.toLong().coerceAtLeast(15),
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueNow(context: Context, allowCellular: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED,
                )
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

/**
 * User-initiated long migration for 100GB–1TB initial upload.
 * Continues across sessions via Room uploadOffset / uploadSessionId.
 */
class MigrationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.migration_notification_title))
            .setContentText("Uploading library to PC…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as PhotoSyncApp
        val repo = app.container.repository
        val prefs = app.container.prefs
        if (!prefs.isPaired()) return Result.failure()

        setForeground(getForegroundInfo())

        return try {
            if (!repo.checkHealth()) return Result.retry()
            repo.scanAndReconcileLocal()

            var rounds = 0
            while (rounds < 200) {
                val remaining = repo.observePendingCount().first()
                if (remaining == 0) break
                updateNotification("Uploading… $remaining items left")
                val uploaded = repo.uploadPending(limit = 8) { asset, offset, total ->
                    // Progress is best-effort via notification below each batch.
                }
                if (uploaded == 0) {
                    // Could be all failing; stop to avoid tight loop.
                    val still = app.container.database.localAssetDao()
                        .count(listOf(SyncState.PENDING, SyncState.UPLOADING))
                    if (still > 0) return Result.retry()
                    break
                }
                rounds++
            }
            val left = app.container.database.localAssetDao()
                .count(listOf(SyncState.PENDING, SyncState.UPLOADING))
            if (left > 0) {
                updateNotification("Paused — $left remaining. Tap Continue in app.")
                Result.success(workDataOf("remaining" to left))
            } else {
                updateNotification("Migration complete")
                Result.success(workDataOf("remaining" to 0))
            }
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "migration failed")))
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_migration),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun updateNotification(text: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.migration_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE = "photo_sync_migration"
        private const val CHANNEL_ID = "migration"
        private const val NOTIFICATION_ID = 42

        fun enqueue(context: Context, allowCellular: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED,
                )
                .setRequiresBatteryNotLow(false)
                .build()
            val request = OneTimeWorkRequestBuilder<MigrationWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
