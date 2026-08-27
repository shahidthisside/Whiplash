package com.whiplash.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted health/circuit-breaker state for a playback provider
 * (section 9). [providerId] is a stable identifier such as "rustypipe".
 */
@Entity(tableName = "provider_health")
data class ProviderHealthEntity(
    @PrimaryKey val providerId: String,
    val successCount: Long,
    val failureCount: Long,
    val recentFailureRate: Float,
    val lastSuccessEpochMs: Long?,
    val lastFailureEpochMs: Long?,
    val status: ProviderStatus,
    val cooldownUntilEpochMs: Long?,
)

/** Mirrors the states described in CLAUDE.md section 9. */
enum class ProviderStatus {
    HEALTHY,
    DEGRADED,
    TEMPORARILY_UNAVAILABLE,
    UNKNOWN,
}
