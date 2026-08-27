package com.whiplash.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whiplash.music.data.local.entity.ProviderHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderHealthDao {

    @Query("SELECT * FROM provider_health WHERE providerId = :providerId")
    suspend fun get(providerId: String): ProviderHealthEntity?

    @Query("SELECT * FROM provider_health")
    fun observeAll(): Flow<List<ProviderHealthEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderHealthEntity)
}
