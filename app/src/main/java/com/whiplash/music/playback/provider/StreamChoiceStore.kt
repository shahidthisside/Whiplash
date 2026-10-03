// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider

import android.content.Context
import com.whiplash.music.domain.model.AudioQuality

/**
 * 5.2 Stream-choice stability.
 *
 * Two jobs:
 *
 * 1. **Pin the chosen format per track.** Each resolve used to re-rank the
 *    audio formats YouTube returned and pick one by bitrate tier. The list
 *    isn't always identical between responses, so the same song could come
 *    back as Opus one time and AAC the next. The disk cache is keyed by the
 *    song, not the format, so a partly cached song could then be continued
 *    with bytes of a different file. Now the format picked the first time
 *    ([Pin.itag]) is reused on every re-open while the quality setting is the
 *    same and YouTube still offers it.
 *
 * 2. **Reuse the resolved URL while it's still valid.** googlevideo URLs
 *    carry their real expiry (`expire=`, epoch seconds, usually ~6 h). A
 *    re-opened song within that window plays straight away with no extractor
 *    round-trip. A URL is only reused if it stays valid for the whole song
 *    plus [SAFETY_MARGIN_MS], so it can't expire mid-playback.
 *
 * Pins are persisted (small, capped at [MAX_PINS]); URLs are memory-only.
 */
class StreamChoiceStore(private val persistence: Persistence) {

    /** The format chosen for a song, and the quality setting it was chosen under. */
    data class Pin(val itag: Int, val quality: AudioQuality)

    /** Where pins are kept. [SharedPrefsPersistence] in the app, an in-memory map in tests. */
    interface Persistence {
        fun read(videoId: String): Pin?
        fun write(videoId: String, pin: Pin)
        fun count(): Int
        fun clear()
    }

    private data class CachedUrl(val stream: ResolvedStream, val quality: AudioQuality)

    private val urls = LinkedHashMap<String, CachedUrl>(64, 0.75f, true)

    /** The pinned format for [videoId], if it was chosen under the same [quality]. */
    @Synchronized
    fun pinnedItag(videoId: String, quality: AudioQuality): Int? =
        persistence.read(videoId)?.takeIf { it.quality == quality }?.itag

    /** Any pin for [videoId], whatever quality it was chosen under. */
    @Synchronized
    fun pin(videoId: String): Pin? = persistence.read(videoId)

    /**
     * A URL resolved earlier for [videoId] at [quality] that is still valid
     * for at least [durationMs] + [SAFETY_MARGIN_MS] from [nowMs], else null.
     */
    @Synchronized
    fun freshUrl(videoId: String, quality: AudioQuality, durationMs: Long, nowMs: Long = System.currentTimeMillis()): ResolvedStream? {
        val cached = urls[videoId] ?: return null
        if (cached.quality != quality) return null
        val expires = cached.stream.expiresAtEpochMs ?: return null
        val needed = durationMs.coerceAtLeast(0L) + SAFETY_MARGIN_MS
        if (expires - nowMs < needed) {
            urls.remove(videoId)
            return null
        }
        return cached.stream
    }

    /** Remembers a successful resolve: pins its format and keeps its URL. */
    @Synchronized
    fun remember(videoId: String, quality: AudioQuality, stream: ResolvedStream) {
        stream.itag?.let { persistence.write(videoId, Pin(it, quality)) }
        urls[videoId] = CachedUrl(stream, quality)
        while (urls.size > MAX_URLS) urls.remove(urls.keys.first())
        if (persistence.count() > MAX_PINS) persistence.clear() // rare; pins just get re-learnt
    }

    /** Forgets [videoId]'s URL (e.g. the player got a 403 on it). The pin is kept. */
    @Synchronized
    fun invalidateUrl(videoId: String) {
        urls.remove(videoId)
    }

    @Synchronized
    fun clearUrls() = urls.clear()

    companion object {
        /** Extra validity a reused URL must have beyond the song's length. */
        const val SAFETY_MARGIN_MS = 10 * 60_000L
        private const val MAX_URLS = 200
        private const val MAX_PINS = 2_000

        /** Used when a URL has no readable `expire` parameter. */
        const val FALLBACK_TTL_MS = 5 * 60_000L

        /**
         * Expiry of a googlevideo URL from its `expire` query parameter
         * (epoch seconds), or null if it has none / it can't be read.
         */
        fun parseExpiryMs(url: String): Long? = runCatching {
            Regex("[?&]expire=(\\d{9,11})").find(url)?.groupValues?.get(1)?.toLong()?.times(1000)
        }.getOrNull()

        /** App persistence: one SharedPreferences file of "itag|QUALITY" strings keyed by video id. */
        fun sharedPrefs(context: Context): StreamChoiceStore = StreamChoiceStore(SharedPrefsPersistence(context))
    }
}

private class SharedPrefsPersistence(context: Context) : StreamChoiceStore.Persistence {
    private val prefs = context.applicationContext.getSharedPreferences("stream_choice_pins", Context.MODE_PRIVATE)

    override fun read(videoId: String): StreamChoiceStore.Pin? {
        val raw = prefs.getString(videoId, null) ?: return null
        val itag = raw.substringBefore('|').toIntOrNull() ?: return null
        val quality = runCatching { AudioQuality.valueOf(raw.substringAfter('|')) }.getOrNull() ?: return null
        return StreamChoiceStore.Pin(itag, quality)
    }

    override fun write(videoId: String, pin: StreamChoiceStore.Pin) {
        prefs.edit().putString(videoId, "${pin.itag}|${pin.quality.name}").apply()
    }

    override fun count(): Int = prefs.all.size

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
