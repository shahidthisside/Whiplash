package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    /** Completed downloads only, most recent first — what the Downloads tab shows. */
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAtEpochMs DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<DownloadEntity>

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT id FROM downloads WHERE status = 'COMPLETED'")
    fun observeCompletedIds(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    /** Marks any download stuck in DOWNLOADING as FAILED — called once at app startup to clean up rows left behind by a killed process. */
    @Query("UPDATE downloads SET status = 'FAILED' WHERE status = 'DOWNLOADING'")
    suspend fun failAllInProgress()
}
