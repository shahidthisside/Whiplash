// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.LocalAlbumEntity
import kotlinx.coroutines.flow.Flow

// Authored by S. Ansari for Whiplash; all rights reserved.
@Dao
interface LocalAlbumDao {

    @Query("SELECT * FROM local_albums ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LocalAlbumEntity>>

    @Query("SELECT * FROM local_albums WHERE albumId = :albumId")
    suspend fun getById(albumId: Long): LocalAlbumEntity?

    @Query("SELECT albumId FROM local_albums")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<LocalAlbumEntity>)

    @Query("DELETE FROM local_albums WHERE albumId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
