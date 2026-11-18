package com.cyrus.gallery.data.database

import androidx.room.TypeConverter
import com.cyrus.gallery.domain.model.CloudStorageState

class CloudCacheConverters {
    @TypeConverter fun storageStateToString(value: CloudStorageState): String = value.name
    @TypeConverter fun stringToStorageState(value: String): CloudStorageState = CloudStorageState.valueOf(value)
    @TypeConverter fun downloadStatusToString(value: CloudDownloadStatus): String = value.name
    @TypeConverter fun stringToDownloadStatus(value: String): CloudDownloadStatus = CloudDownloadStatus.valueOf(value)
}
