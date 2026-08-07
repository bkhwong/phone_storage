package com.phonesync.app.data.local

import androidx.room.TypeConverter
import com.phonesync.app.media.MediaKind

class Converters {
    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromMediaKind(value: MediaKind): String = value.name

    @TypeConverter
    fun toMediaKind(value: String): MediaKind = MediaKind.valueOf(value)
}
