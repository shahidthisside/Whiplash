package com.whiplash.music.playback.provider

import com.whiplash.music.data.local.dao.ProviderHealthDao
import com.whiplash.music.data.local.entity.ProviderHealthEntity
import com.whiplash.music.data.local.entity.ProviderStatus
import kotlin.math.min

/**
 * Tracks and persists provider health (CLAUDE.md section 9): success/
 * failure counts, recent failure rate, and a status derived from them.
 * Backed by [ProviderHealthDao] (built in Phase 3, unused until now) so
 * health survives process death.
 *
 * State machine:
 * - HEALTHY: no signal of trouble.
 * - DEGRADED: some recent failures, but not enough to pull the provider
 *   out of rotation — [PlaybackManager] still tries it, just not first.
 * - TEMPORARILY_UNAVAILABLE: enough consecutive/recent failures that we
 *   stop trying this provider until [cooldownUntilEpochMs] passes, so we
 *   never hammer a failing upstream (section 9 "Do not hammer a failing
 *   provider").
 *
 * A simple exponential-ish backoff is used for the cooldown window so a
 * provider that is down for longer gets probed less frequently, but it is
 * still periodically allowed to recover per section 9.
 */
class ProviderHealthTracker(private val dao: ProviderHealthDao) {

    private companion object {
        const val DEGRADED_FAILURE_RATE_THRESHOLD = 0.3f
        const val UNAVAILABLE_FAILURE_RATE_THRESHOLD = 0.7f
        const val MIN_SAMPLES_BEFORE_DEGRADING = 3L
        const val BASE_COOLDOWN_MS = 30_000L
        const val MAX_COOLDOWN_MS = 10 * 60_000L
    }

    suspend fun recordSuccess(providerId: String) {
        val current = dao.get(providerId) ?: freshEntity(providerId)
        val successCount = current.successCount + 1
        val failureCount = current.failureCount
        val updated = current.copy(
            successCount = successCount,
            recentFailureRate = decayedFailureRate(current.recentFailureRate, failed = false),
            lastSuccessEpochMs = System.currentTimeMillis(),
            status = deriveStatus(successCount + failureCount, decayedFailureRate(current.recentFailureRate, failed = false)),
            cooldownUntilEpochMs = null,
        )
        dao.upsert(updated)
    }

    suspend fun recordFailure(providerId: String) {
        val current = dao.get(providerId) ?: freshEntity(providerId)
        val failureCount = current.failureCount + 1
        val totalSamples = current.successCount + failureCount
        val newRate = decayedFailureRate(current.recentFailureRate, failed = true)
        val status = deriveStatus(totalSamples, newRate)
        val cooldownUntil = if (status == ProviderStatus.TEMPORARILY_UNAVAILABLE) {
            System.currentTimeMillis() + nextCooldownMs(current.cooldownUntilEpochMs)
        } else {
            null
        }
        dao.upsert(
            current.copy(
                failureCount = failureCount,
                recentFailureRate = newRate,
                lastFailureEpochMs = System.currentTimeMillis(),
                status = status,
                cooldownUntilEpochMs = cooldownUntil,
            ),
        )
    }

    /** Current status, per section 9. Never hits the failing provider to compute this. */
    suspend fun statusOf(providerId: String): ProviderStatus {
        val entity = dao.get(providerId) ?: return ProviderStatus.UNKNOWN
        // Allow a cooled-down provider back into rotation (periodic recovery,
        // section 9) by reporting DEGRADED instead of UNAVAILABLE once the
        // cooldown window has elapsed, even before a new success is recorded.
        val cooldownUntil = entity.cooldownUntilEpochMs
        if (entity.status == ProviderStatus.TEMPORARILY_UNAVAILABLE &&
            cooldownUntil != null &&
            System.currentTimeMillis() >= cooldownUntil
        ) {
            return ProviderStatus.DEGRADED
        }
        return entity.status
    }

    /** Whether [providerId] should currently be skipped (still cooling down). */
    suspend fun isInCooldown(providerId: String): Boolean {
        val entity = dao.get(providerId) ?: return false
        val cooldownUntil = entity.cooldownUntilEpochMs ?: return false
        return entity.status == ProviderStatus.TEMPORARILY_UNAVAILABLE &&
            System.currentTimeMillis() < cooldownUntil
    }

    private fun nextCooldownMs(previousCooldownUntil: Long?): Long {
        // If we were already cooling down recently, back off further, capped.
        val wasRecentlyCoolingDown = previousCooldownUntil != null &&
            System.currentTimeMillis() - previousCooldownUntil < BASE_COOLDOWN_MS * 4
        return if (wasRecentlyCoolingDown) {
            min(BASE_COOLDOWN_MS * 4, MAX_COOLDOWN_MS)
        } else {
            BASE_COOLDOWN_MS
        }
    }

    /** Exponential moving average so old failures/successes fade out over time. */
    private fun decayedFailureRate(previousRate: Float, failed: Boolean): Float {
        val alpha = 0.2f
        val sample = if (failed) 1f else 0f
        return (alpha * sample) + ((1 - alpha) * previousRate)
    }

    private fun deriveStatus(totalSamples: Long, failureRate: Float): ProviderStatus = when {
        totalSamples < MIN_SAMPLES_BEFORE_DEGRADING -> ProviderStatus.HEALTHY
        failureRate >= UNAVAILABLE_FAILURE_RATE_THRESHOLD -> ProviderStatus.TEMPORARILY_UNAVAILABLE
        failureRate >= DEGRADED_FAILURE_RATE_THRESHOLD -> ProviderStatus.DEGRADED
        else -> ProviderStatus.HEALTHY
    }

    private fun freshEntity(providerId: String) = ProviderHealthEntity(
        providerId = providerId,
        successCount = 0,
        failureCount = 0,
        recentFailureRate = 0f,
        lastSuccessEpochMs = null,
        lastFailureEpochMs = null,
        status = ProviderStatus.UNKNOWN,
        cooldownUntilEpochMs = null,
    )
}
