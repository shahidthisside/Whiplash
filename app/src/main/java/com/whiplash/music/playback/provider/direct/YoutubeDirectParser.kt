// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider.direct

import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.playback.provider.AudioStreamRanking
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.ProviderPlayerInfo
import com.whiplash.music.playback.provider.ResolvedStream
import com.whiplash.music.playback.provider.StreamChoiceStore
import org.json.JSONObject

/**
 * Reads YouTube's own player response (the visionOS app's) into a playable
 * stream. Kept free of networking so it can be unit tested on plain JSON.
 *
 * Picks the stream the same way the NewPipe source does (plain original
 * audio first, then the bitrate tier for the quality setting, or the pinned
 * format if it's still offered), so switching source never changes which
 * file a song plays from, and its cached audio stays valid.
 */
object YoutubeDirectParser {

    /** One audio format from the response. */
    data class Format(
        val itag: Int,
        val url: String,
        val mimeType: String,
        val averageBitrateBps: Int,
        val kind: AudioStreamRanking.Kind,
    )

    /** Throws a [ProviderFailure] when YouTube won't play [videoId] for this client. */
    fun checkPlayable(json: JSONObject, videoId: String) {
        val status = json.optJSONObject("playabilityStatus")
        val state = status?.optString("status").orEmpty()
        if (state == "OK") {
            val returned = json.optJSONObject("videoDetails")?.optString("videoId")
            if (!returned.isNullOrEmpty() && returned != videoId) {
                throw ProviderFailure.ProviderParserFailure("Response was for $returned, not $videoId")
            }
            return
        }
        val reason = status?.optString("reason").orEmpty().ifBlank { "Not playable ($state)" }
        // Every refusal can be worth a second opinion from the other source
        // (it asks as a different app), so all of them allow falling back.
        throw when {
            state.isEmpty() -> ProviderFailure.ProviderParserFailure("No playability status for $videoId")
            reason.contains("bot", ignoreCase = true) -> ProviderFailure.RateLimited(reason)
            else -> ProviderFailure.UnknownPlaybackFailure(reason)
        }
    }

    /** The audio formats that have a direct link, without DRC or other special variants. */
    fun audioFormats(json: JSONObject): List<Format> {
        val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return emptyList()
        val out = ArrayList<Format>()
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("audio/")) continue
            if (f.optBoolean("isDrc", false)) continue // loudness-compressed copy, not the original mix
            val url = f.optString("url")
            if (url.isBlank()) continue // ciphered: this source can't use it
            val track = f.optJSONObject("audioTrack")
            val kind = when {
                track == null -> AudioStreamRanking.Kind.UNLABELLED
                track.optBoolean("audioIsDefault", false) -> AudioStreamRanking.Kind.ORIGINAL
                track.optString("displayName").contains("original", ignoreCase = true) -> AudioStreamRanking.Kind.ORIGINAL
                track.optString("displayName").contains("descriptive", ignoreCase = true) -> AudioStreamRanking.Kind.DESCRIPTIVE
                else -> AudioStreamRanking.Kind.DUBBED
            }
            out += Format(
                itag = f.optInt("itag"),
                url = url,
                mimeType = mime.substringBefore(';').trim(),
                averageBitrateBps = f.optInt("averageBitrate").takeIf { it > 0 } ?: f.optInt("bitrate"),
                kind = kind,
            )
        }
        return out
    }

    /** The format to play: the pinned one if offered, else the [quality] tier of the preferred pool. */
    fun choose(formats: List<Format>, quality: AudioQuality, preferredItag: Int?): Format? {
        if (formats.isEmpty()) return null
        val pool = AudioStreamRanking.preferredPool(
            formats.map { AudioStreamRanking.Candidate(it, it.kind, progressive = true, hasUrl = true) },
        ).ifEmpty { formats }
        preferredItag?.let { itag -> pool.firstOrNull { it.itag == itag }?.let { return it } }
        val sorted = pool.sortedBy { it.averageBitrateBps }
        return when (quality) {
            AudioQuality.AUTO, AudioQuality.HIGHEST -> sorted.last()
            AudioQuality.LOW -> sorted.first()
            AudioQuality.MEDIUM -> sorted[(sorted.size - 1) / 2]
            AudioQuality.HIGH -> sorted[((sorted.size - 1) * 3) / 4]
        }
    }

    /** Cover sizes for [videoId], largest first; not every size exists, so callers try them in order. */
    fun artworkCandidates(json: JSONObject, videoId: String): List<String> {
        val listed = json.optJSONObject("videoDetails")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val fromResponse = buildList {
            if (listed != null) for (i in 0 until listed.length()) {
                val t = listed.optJSONObject(i) ?: continue
                val url = t.optString("url").substringBefore('?')
                if (url.isNotBlank()) add(t.optInt("height") to url)
            }
        }.sortedByDescending { it.first }.map { it.second }
        val standard = listOf("maxresdefault", "hq720", "sddefault", "hqdefault").map { "https://i.ytimg.com/vi/$videoId/$it.jpg" }
        return (listOf(standard.first()) + fromResponse + standard.drop(1)).distinct()
    }

    /** The playable stream for [videoId], with the session id ([cpn]) its link needs. */
    fun resolve(json: JSONObject, videoId: String, quality: AudioQuality, preferredItag: Int?, cpn: String, providerId: String): ResolvedStream {
        checkPlayable(json, videoId)
        val format = choose(audioFormats(json), quality, preferredItag)
            ?: throw ProviderFailure.UnknownPlaybackFailure("No direct audio stream for $videoId")
        val url = format.url + "&cpn=" + cpn
        val artwork = artworkCandidates(json, videoId)
        return ResolvedStream(
            streamUrl = url,
            mimeType = format.mimeType,
            bitrateBps = format.averageBitrateBps.takeIf { it > 0 },
            expiresAtEpochMs = StreamChoiceStore.parseExpiryMs(url)
                ?: json.optJSONObject("streamingData")?.optLong("expiresInSeconds")?.takeIf { it > 0 }
                    ?.let { System.currentTimeMillis() + it * 1000 },
            providerId = providerId,
            resolvedArtworkUrl = artwork.firstOrNull(),
            resolvedArtworkCandidates = artwork,
            itag = format.itag.takeIf { it > 0 },
        )
    }

    /** Title, channel, length and covers from the same response. */
    fun playerInfo(json: JSONObject, videoId: String): ProviderPlayerInfo {
        checkPlayable(json, videoId)
        val details = json.optJSONObject("videoDetails")
        val artwork = artworkCandidates(json, videoId)
        return ProviderPlayerInfo(
            songId = videoId,
            title = details?.optString("title").orEmpty(),
            artist = details?.optString("author")?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() },
            album = null,
            artworkUrl = artwork.firstOrNull(),
            durationMs = details?.optString("lengthSeconds")?.toLongOrNull()?.times(1000),
            artworkCandidates = artwork,
            category = null,
        )
    }
}
