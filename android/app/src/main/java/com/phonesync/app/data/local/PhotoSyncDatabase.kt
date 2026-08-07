package com.phonesync.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LocalAssetEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PhotoSyncDatabase : RoomDatabase() {
    abstract fun localAssetDao(): LocalAssetDao
}
