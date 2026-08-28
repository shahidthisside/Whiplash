package com.whiplash.music.playback.provider.newpipe

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.domain.model.YoutubePlaylistDetail
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.ProviderHealthTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Resolves full detail pages (album/playlist track listings, artist/channel
 * info + real popular-songs/albums tabs) for the results returned by
 * [YoutubeSearchProvider]'s album/artist/playlist search (section 39/40).
 *
 * Kept as its own class rather than folded into [YoutubeSearchProvider]
 * since this is detail *extraction* (given a URL, fetch its full page),
 * not search — same separation-of-concerns reasoning already applied
 * between search and [NewPipePlaybackProvider]'s stream resolution.
 */
class YoutubeDetailProvider(
    private val healthTracker: ProviderHealthTracker,
    private val searchProvider: YoutubeSearchProvider,
) {

    /** Real album/playlist detail: title, uploader, artwork, and the actual track listing (section 39). */
    suspend fun getPlaylistDetail(url: String): YoutubePlaylistDetail = withContext(Dispatchers.IO) {
        try {
            val info = PlaylistInfo.getInfo(url)
            val tracks = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toPlayableItemOrNull() }

            healthTracker.recordSuccess(PROVIDER_ID)
            YoutubePlaylistDetail(
                url = url,
                title = info.name.orEmpty(),
                uploaderName = info.uploaderName,
                artworkUrl = info.thumbnails.maxByOrNull { it.height }?.url,
                tracks = tracks,
            )
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /**
     * Real artist/channel detail (section 40). [popularSongs] and [albums]
     * are populated from the channel's actual "tracks" and "albums" tabs
     * when the channel exposes them — real NewPipeExtractor ChannelTabs
     * constants confirmed to exist via direct class inspection before
     * building this — and are left empty (never fabricated) when a
     * channel doesn't have that tab, per section 73.
     */
    suspend fun getArtistDetail(channelUrl: String): YoutubeArtistDetail = withContext(Dispatchers.IO) {
        try {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val info = ChannelInfo.getInfo(channelUrl)

            Log.d(TAG, "Channel '${info.name}' exposes tabs: ${info.tabs.map { it.contentFilters }}")

            val popularSongs = findTab(info, ChannelTabs.TRACKS)?.let { tabHandler ->
                runCatching {
                    val tabExtractor = youtube.getChannelTabExtractor(tabHandler)
                    tabExtractor.fetchPage()
                    ChannelTabInfo.getInfo(tabExtractor).relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .mapNotNull { it.toPlayableItemOrNull() }
                }.onFailure { Log.w(TAG, "Failed to load 'tracks' tab for ${info.name}", it) }
                    .getOrDefault(emptyList())
            } ?: emptyList()

            val albums = findTab(info, ChannelTabs.ALBUMS)?.let { tabHandler ->
                runCatching {
                    val tabExtractor = youtube.getChannelTabExtractor(tabHandler)
                    tabExtractor.fetchPage()
                    ChannelTabInfo.getInfo(tabExtractor).relatedItems
                        .filterIsInstance<PlaylistInfoItem>()
                        .map { item ->
                            YoutubePlaylistResult(
                                url = item.url,
                                title = item.name.orEmpty(),
                                uploaderName = item.uploaderName,
                                artworkUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                                trackCount = item.streamCount.takeIf { it >= 0 },
                                isAlbum = true,
                            )
                        }
                }.onFailure { Log.w(TAG, "Failed to load 'albums' tab for ${info.name}", it) }
                    .getOrDefault(emptyList())
            } ?: emptyList()

            // Fallback (still real data, not fabricated): some channels —
            // especially auto-generated "Topic" channels, which is what
            // YouTube Music search results usually resolve to — don't
            // expose a dedicated "tracks" tab at all, only a general
            // "videos" tab. If that's genuinely all this channel has,
            // reuse it rather than showing an empty page when real songs
            // do exist, just not under the tab name we checked first.
            val tabSongs = popularSongs.ifEmpty {
                findTab(info, ChannelTabs.VIDEOS)?.let { tabHandler ->
                    runCatching {
                        val tabExtractor = youtube.getChannelTabExtractor(tabHandler)
                        tabExtractor.fetchPage()
                        ChannelTabInfo.getInfo(tabExtractor).relatedItems
                            .filterIsInstance<StreamInfoItem>()
                            .mapNotNull { it.toPlayableItemOrNull() }
                    }.onFailure { Log.w(TAG, "Failed to load 'videos' tab fallback for ${info.name}", it) }
                        .getOrDefault(emptyList())
                } ?: emptyList()
            }

            // Final fallback (still real, live-searched data — not
            // fabricated): confirmed via logging that some channels
            // (particularly YouTube-Music-search-resolved "Topic" channels)
            // expose ZERO tabs at all (info.tabs is genuinely empty from
            // NewPipeExtractor's own extraction), so there's no tab-based
            // API to fall back to. Reusing the already-verified
            // MUSIC_SONGS search for this artist's name is the honest
            // substitute every major music app effectively does too when a
            // dedicated per-artist songs API isn't available — it's the
            // same real search endpoint the Search tab's "Songs" results
            // use, just queried with the artist name. Strips a trailing
            // " - Topic" suffix (YouTube's own naming convention for
            // auto-generated artist channels) before both searching and
            // matching, since a real song's uploaderName is the plain
            // artist name without that suffix.
            val effectiveSongs = tabSongs.ifEmpty {
                val plainName = info.name.orEmpty().removeSuffix(" - Topic").trim()
                runCatching { searchProvider.search(plainName) }
                    .onFailure { Log.w(TAG, "Search-based songs fallback failed for $plainName", it) }
                    .getOrDefault(emptyList())
                    .filter { it.artist.equals(plainName, ignoreCase = true) }
            }

            healthTracker.recordSuccess(PROVIDER_ID)
            YoutubeArtistDetail(
                channelUrl = channelUrl,
                name = info.name.orEmpty(),
                artworkUrl = info.avatars.maxByOrNull { it.height }?.url,
                subscriberCount = info.subscriberCount.takeIf { it >= 0 },
                description = info.description?.takeIf { it.isNotBlank() },
                popularSongs = effectiveSongs,
                albums = albums,
            )
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /** Finds the [ChannelInfo.getTabs] entry matching [tabId] (e.g. "tracks"/"albums"), or null if the channel doesn't expose that tab. */
    private fun findTab(info: ChannelInfo, tabId: String) =
        info.tabs.find { it.contentFilters.contains(tabId) }

    private fun StreamInfoItem.toPlayableItemOrNull(): PlayableItem.YoutubeTrack? {
        val videoId = extractVideoId(url) ?: return null
        return PlayableItem.YoutubeTrack(
            id = videoId,
            title = name.orEmpty(),
            artist = uploaderName.orEmpty(),
            album = null,
            artworkUri = thumbnails.maxByOrNull { it.height }?.url,
            durationMs = duration.takeIf { it >= 0 }?.times(1000) ?: 0L,
        )
    }

    private fun extractVideoId(watchUrl: String): String? =
        Regex("[?&]v=([^&]+)").find(watchUrl)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([^?&]+)").find(watchUrl)?.groupValues?.get(1)

    private fun Exception.toProviderFailure(): ProviderFailure = when (this) {
        is ContentNotAvailableException, is PaidContentException ->
            ProviderFailure.ContentUnavailable(message ?: "Content unavailable", this)

        is AgeRestrictedContentException ->
            ProviderFailure.AuthenticationRequired(message ?: "Age-restricted content", this)

        is ReCaptchaException -> ProviderFailure.RateLimited(message ?: "reCAPTCHA required", this)

        is ParsingException -> ProviderFailure.ProviderParserFailure(message ?: "Detail parsing failure", this)

        is UnknownHostException, is InterruptedIOException ->
            ProviderFailure.NetworkFailure(message ?: "Network failure", this)

        is IOException -> ProviderFailure.NetworkFailure(message ?: "Network failure", this)

        is ExtractionException -> ProviderFailure.ProviderParserFailure(message ?: "Detail extraction failure", this)

        else -> ProviderFailure.UnknownPlaybackFailure(message ?: "Unknown detail failure", this)
    }.also {
        Log.w(TAG, "Detail failure mapped ${this.javaClass.simpleName} -> ${it.javaClass.simpleName}: ${this.message}")
    }

    private companion object {
        const val PROVIDER_ID = NewPipePlaybackProvider.PROVIDER_ID
        const val TAG = "YoutubeDetailProvider"
        const val YOUTUBE_SERVICE_NAME = "YouTube"
    }
}
