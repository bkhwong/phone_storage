package com.phonesync.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import androidx.work.Configuration
import com.phonesync.app.data.local.PhotoSyncDatabase
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.remote.ApiClientFactory
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.media.MediaStoreScanner
import com.phonesync.app.sync.SyncWorker
import com.phonesync.app.sync.UploadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val prefs = SecurePrefs(app)
    val database: PhotoSyncDatabase = Room.databaseBuilder(
        app,
        PhotoSyncDatabase::class.java,
        "photo_sync.db",
    ).fallbackToDestructiveMigration().build()

    val apiFactory = ApiClientFactory(prefs)
    private val scanner = MediaStoreScanner(app)
    private val uploadEngine = UploadEngine(app, database.localAssetDao(), apiFactory)

    val repository = PhotoSyncRepository(
        context = app,
        dao = database.localAssetDao(),
        prefs = prefs,
        apiFactory = apiFactory,
        scanner = scanner,
        uploadEngine = uploadEngine,
    )
}

class PhotoSyncApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ensureNotificationChannels()
        appScope.launch {
            val interval = container.prefs.syncIntervalMinutes.first()
            val cellular = container.prefs.allowCellular.first()
            if (container.prefs.isPaired()) {
                SyncWorker.enqueuePeriodic(this@PhotoSyncApp, interval, cellular)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "sync",
                getString(R.string.notification_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "migration",
                getString(R.string.notification_channel_migration),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
