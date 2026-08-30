package com.whiplash.music.playback.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource

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
 *
 * When caching is enabled, requests go through a [ResolvingDataSource]
 * layered on top of the real caching [androidx.media3.datasource.cache.CacheDataSource].
 * For the normal case — [com.whiplash.music.playback.controller.PlaybackController]
 * already resolved a genuinely fresh stream URL before ever building the
 * [androidx.media3.common.MediaItem] — that URL is trusted as-is and
 * [resolveFreshUri] is never called; [CacheDataSource] underneath still
 * transparently reads/writes the disk cache around it exactly as before.
 *
 * The one case this layer actually intercepts: when [PlaybackController]
 * already knows (via [AudioCacheManager.isFullyCached]) that a track's
 * audio is entirely on disk already, it skips resolving a stream URL at
 * all and hands this factory a placeholder URI instead — since resolving
 * one just to immediately read the answer from disk anyway was the exact
 * bug being fixed here ("a cached song still takes time to reload on
 * replay": the disk cache alone only ever avoided re-downloading bytes
 * once ExoPlayer already had a URL to open, never the resolve step needed
 * to obtain that URL in the first place). This layer re-confirms the
 * cache is still fully present right before the actual read (closing a
 * narrow race against a concurrent Clear Cache) and only falls back to
 * [resolveFreshUri] if that's no longer true. This mirrors the pattern
 * used by other open-source Media3-backed YouTube music clients (e.g.
 * ViMusic's `PlayerService`, which checks
 * `cache.isCached(videoId, dataSpec.position, chunkLength)` inside its
 * own `ResolvingDataSource.Factory` before resolving a stream URL) rather
 * than being a custom invention.
 */
class TogglableCacheDataSourceFactory(
    private val cacheManager: AudioCacheManager,
    private val plainFactory: DataSource.Factory,
    private val isCacheEnabled: () -> Boolean,
    /**
     * Called only when [PlaybackController]'s fast-path placeholder URI
     * turns out to no longer be backed by a full cache entry at actual
     * read time (a narrow race against something like a concurrent Clear
     * Cache — not the common path). Runs on a background loading thread,
     * safe to block. Returns a fresh, playable URL for the track
     * identified by [cacheKey] (the same stable key passed to
     * [androidx.media3.common.MediaItem.Builder.setCustomCacheKey]), or
     * null if resolution failed, in which case the placeholder URI is
     * left as-is and surfaces as a normal upstream network error.
     */
    private val resolveFreshUri: (cacheKey: String) -> String?,
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
        if (!isCacheEnabled()) return plainFactory.createDataSource()

        val cachingFactory = cacheManager.cachingDataSourceFactory()
        val resolvingFactory = ResolvingDataSource.Factory(cachingFactory) { dataSpec ->
            val cacheKey = dataSpec.key
            if (cacheKey == null) {
                // No stable key set on this request (shouldn't happen for
                // YouTube tracks — see PlayableItemMediaItemMapper — but
                // for local tracks / anything else, behave exactly as
                // before: pass the request through unchanged).
                return@Factory dataSpec
            }

            val isPlaceholderUri = dataSpec.uri.scheme == PLACEHOLDER_SCHEME
            if (!isPlaceholderUri) {
                // PlaybackController already resolved a genuinely fresh
                // stream URL for this request (the normal case for any
                // track not already fully cached) — trust it as-is rather
                // than re-resolving a second time here, which would just
                // double the network latency for every first play for no
                // benefit. CacheDataSource underneath still transparently
                // writes to/reads from disk around this real URL exactly
                // as before; this ResolvingDataSource layer only exists to
                // intercept the placeholder case below.
                return@Factory dataSpec
            }

            // Reaching here means PlaybackController's isFullyCached fast
            // path (see PlaybackController.playIndex) handed us a
            // placeholder URI on the assumption the content is already
            // fully on disk under cacheKey. Re-check right here, right
            // before the read actually happens, rather than trusting that
            // assumption blindly — closes the narrow race where cache
            // state changed between that check and this read (e.g. a
            // concurrent Clear Cache), and is also what makes the fast
            // path provably safe rather than merely "usually correct".
            val stillCached = cacheManager.isFullyCached(cacheKey)
            if (stillCached) {
                dataSpec
            } else {
                val freshUrl = resolveFreshUri(cacheKey)
                if (freshUrl != null) dataSpec.withUri(android.net.Uri.parse(freshUrl)) else dataSpec
            }
        }
        return resolvingFactory.createDataSource()
    }

    companion object {
        /** Scheme used for PlaybackController's cache-hit fast-path placeholder URI (see its playIndex). */
        const val PLACEHOLDER_SCHEME = "cache"
    }
}
