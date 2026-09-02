package com.whiplash.music.data.local

import androidx.room.TypeConverter
import com.whiplash.music.data.local.entity.DownloadStatus
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.ProviderStatus

/** Room type converters for enum columns. */
class Converters {

    @TypeConverter
    fun mediaSourceToString(value: MediaSource): String = value.name

    @TypeConverter
    fun stringToMediaSource(value: String): MediaSource = MediaSource.valueOf(value)

    @TypeConverter
    fun providerStatusToString(value: ProviderStatus): String = value.name

    @TypeConverter
    fun stringToProviderStatus(value: String): ProviderStatus = ProviderStatus.valueOf(value)

    @TypeConverter
    fun downloadStatusToString(value: DownloadStatus): String = value.name

    @TypeConverter
    fun stringToDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
