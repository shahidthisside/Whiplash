package com.whiplash.music.playback.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource

/**
 * A [DataSource.Factory] that checks [isCacheEnabled] fresh on every call
 * to [createDataSource] and delegates to either the real disk-caching
 * factory or a plain network-only one accordingly.
 *
 * This is what makes the Settings toggle a genuine on/off switch rather
 * than a cosmetic one: when disabled, [DataSource]s created from this
 * point on never touch [AudioCacheManager]'s [androidx.media3.datasource.cache.SimpleCache]
 * at all — no reads, no writes — instead of just skipping new writes
 * while still silently reading whatever was cached before.
 */
class TogglableCacheDataSourceFactory(
    private val cacheManager: AudioCacheManager,
    private val plainFactory: DataSource.Factory,
    private val isCacheEnabled: () -> Boolean,
) : DataSource.Factory {

    // Deliberately NOT cached (no `by lazy`/stored field): AudioCacheManager.clearCache()
    // releases the underlying SimpleCache and replaces it with a fresh
    // instance on next use. A cached CacheDataSource.Factory here would
    // keep pointing at the old, released Cache — every createDataSource()
    // call after a Clear Cache would then throw (confirmed via a real
    // on-device crash: SimpleCache.getContentMetadata() -> IllegalStateException
    // -> ExoPlaybackException "Source error" the moment playback tried to
    // read through the stale factory). Building this fresh each call is
    // cheap (it's a small wrapper object, not the cache itself) and always
    // reflects whatever Cache instance is currently live.
    override fun createDataSource(): DataSource {
        return if (isCacheEnabled()) {
            cacheManager.cachingDataSourceFactory().createDataSource()
        } else {
            plainFactory.createDataSource()
        }
    }
}
