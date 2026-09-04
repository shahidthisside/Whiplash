package com.whiplash.music.playback.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Real, on-disk streaming cache for resolved YouTube audio, matching how
 * Spotify/YouTube Music actually behave: cached bytes let a track that was
 * already played start again instantly without a fresh network fetch, the
 * cache size is capped and old entries are evicted automatically (an LRU
 * policy — the same thing Spotify's own support docs describe: "The amount
 * of music stored in the cache shouldn't keep growing... new songs replace
 * stored songs that haven't been listened to for a while"), and it's a
 * cache, not a download — nothing here is presented to the user as saved
 * for offline use.
 *
 * Built on Media3's own [SimpleCache]/[CacheDataSource] (the standard,
 * documented mechanism for this — not a custom byte-store), so gapless
 * playback, crossfade, and stream resolution are all completely unaffected;
 * only the DataSource layer underneath ExoPlayer changes.
 */
class AudioCacheManager(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Lazily constructed so the cache directory/database are only touched
     * once actually needed (section 64: avoid unnecessary work at startup),
     * and so [setMaxSizeBytes] taking effect on the very first use doesn't
     * require restarting the app.
     */
    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var maxSizeBytes: Long = DEFAULT_MAX_CACHE_BYTES

    private val cacheDir: File
        get() = File(appContext.cacheDir, CACHE_DIR_NAME)

    /** Sets the cache's size cap. Takes effect for the current cache instance immediately (evictor re-reads this on every write). */
    fun setMaxSizeBytes(bytes: Long) {
        maxSizeBytes = bytes
    }

    private fun getOrCreateCache(): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(maxSizeBytes),
                StandaloneDatabaseProvider(appContext),
            ).also { cache = it }
        }
    }

    /**
     * A [CacheDataSource.Factory] that reads/writes through the real disk
     * cache. Passed to ExoPlayer's [androidx.media3.exoplayer.source.DefaultMediaSourceFactory]
     * only when caching is enabled in Settings — when disabled, the caller
     * uses a plain [DefaultDataSource.Factory] instead (see
     * [com.whiplash.music.playback.service.WhiplashPlaybackService]), so
     * disabling the setting genuinely stops both new writes and reads from
     * the cache, not just a cosmetic toggle.
     */
    fun cachingDataSourceFactory(): CacheDataSource.Factory {
        // The real network fetch happens as bounded byte-range requests
        // rather than one open-ended GET (see ChunkedDataSource) — this is
        // what CacheDataSource's own read-ahead and the current track's
        // live read both benefit from, since both ultimately go through
        // this same upstream factory.
        val upstreamFactory = ChunkedDataSourceFactory(DefaultDataSource.Factory(appContext))
        return CacheDataSource.Factory()
            .setCache(getOrCreateCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            // If a partially-cached file is corrupt or a write fails, fall
            // back to the network rather than surfacing a playback error —
            // caching is purely a latency optimization and must never be
            // able to break playback itself.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Real current on-disk cache size in bytes, for display in Settings. */
    fun currentCacheSizeBytes(): Long = runCatching { getOrCreateCache().cacheSpace }.getOrDefault(0L)

    /**
     * Whether the full content for [cacheKey] (the same stable key set via
     * [androidx.media3.common.MediaItem.Builder.setCustomCacheKey], see
     * [com.whiplash.music.playback.controller.PlayableItemMediaItemMapper.mediaIdOf])
     * is already present on disk with no gaps, using [Cache.isCached] —
     * the same check other open-source Media3-backed YouTube music clients
     * use before deciding whether a track needs a fresh network resolve
     * (e.g. ViMusic's `PlayerService.createDataSourceFactory()`, which
     * checks `cache.isCached(videoId, dataSpec.position, chunkLength)`
     * ahead of calling its own stream resolver).
     *
     * Used by [com.whiplash.music.playback.controller.PlaybackController]
     * to skip the network stream-resolution round-trip entirely when a
     * track is already fully cached — this was the actual root cause of
     * "a cached song still takes time to reload": the disk cache alone
     * only ever avoided re-downloading the audio bytes once ExoPlayer had
     * a URL to read from; it did nothing for the NewPipeExtractor network
     * call that has to run first to obtain that URL at all, which
     * [com.whiplash.music.playback.controller.PlaybackController.playIndex]
     * previously ran unconditionally on every replay.
     *
     * Relies on [androidx.media3.datasource.cache.ContentMetadata]'s
     * content-length record, which [androidx.media3.datasource.cache.CacheDataSource]
     * writes once a read learns the resource's total length (from the
     * upstream response) — present as soon as any read of the track has
     * started, not only once fully finished, so this correctly returns
     * false for a track that's only partially cached (e.g. skipped away
     * from before finishing) since such an entry can't serve playback
     * from position 0 through the end without also needing the network.
     */
    fun isFullyCached(cacheKey: String): Boolean = runCatching {
        val cache = getOrCreateCache()
        val contentLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
        contentLength > 0 && cache.isCached(cacheKey, 0, contentLength)
    }.getOrDefault(false)

    /**
     * Deletes every cached byte (the real "Clear cache" action Spotify's
     * own storage settings expose — see class doc). Safe to call whether
     * or not a track is currently playing from the cache; ExoPlayer/
     * CacheDataSource re-fetch from the network transparently on the next
     * read if their current position was mid-cached-file.
     */
    fun clearCache() {
        synchronized(this) {
            cache?.release()
            cache = null
        }
        runCatching { SimpleCache.delete(cacheDir, StandaloneDatabaseProvider(appContext)) }
    }

    private companion object {
        const val CACHE_DIR_NAME = "audio_stream_cache"

        /** 300MB default cap — enough for a few hundred typical tracks, small enough not to surprise anyone on a storage-constrained device. */
        const val DEFAULT_MAX_CACHE_BYTES = 300L * 1024 * 1024
    }
}
