// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.provider.direct

import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.playback.provider.ProviderFailure
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YoutubeDirectParserTest {

    private fun format(itag: Int, bitrate: Int, mime: String = "audio/webm; codecs=\"opus\"", url: String? = "https://rr1.googlevideo.com/videoplayback?expire=1791000000&itag=$itag", extra: (JSONObject) -> Unit = {}) =
        JSONObject().put("itag", itag).put("mimeType", mime).put("averageBitrate", bitrate).put("bitrate", bitrate + 1000)
            .apply { if (url != null) put("url", url) }.also(extra)

    private fun response(vararg formats: JSONObject, status: String = "OK", reason: String? = null, id: String = "abcdefghijk") = JSONObject()
        .put("playabilityStatus", JSONObject().put("status", status).apply { if (reason != null) put("reason", reason) })
        .put("videoDetails", JSONObject().put("videoId", id).put("title", "Song").put("author", "Artist - Topic").put("lengthSeconds", "200"))
        .put("streamingData", JSONObject().put("expiresInSeconds", "21540").put("adaptiveFormats", JSONArray(formats.toList())))

    private val full = response(
        format(139, 48_000, "audio/mp4; codecs=\"mp4a.40.5\""),
        format(140, 129_000, "audio/mp4; codecs=\"mp4a.40.2\""),
        format(249, 55_000),
        format(250, 72_000),
        format(251, 141_000),
        format(137, 4_000_000, "video/mp4; codecs=\"avc1\""),
    )

    @Test
    fun picksTheSameTierAsTheNewPipeSource() {
        assertEquals(251, YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.AUTO, null, "CPN", "d").itag)
        assertEquals(139, YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.LOW, null, "CPN", "d").itag)
        assertEquals(250, YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.MEDIUM, null, "CPN", "d").itag)
    }

    @Test
    fun keepsThePinnedFormatWhenOffered() {
        assertEquals(140, YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.AUTO, 140, "CPN", "d").itag)
        // Not offered any more: falls back to the tier.
        assertEquals(251, YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.AUTO, 999, "CPN", "d").itag)
    }

    @Test
    fun addsTheSessionIdAndReadsExpiry() {
        val s = YoutubeDirectParser.resolve(full, "abcdefghijk", AudioQuality.AUTO, null, "Ab-_12", "youtube_direct")
        assertTrue(s.streamUrl.endsWith("&cpn=Ab-_12"))
        assertEquals(1_791_000_000_000L, s.expiresAtEpochMs)
        assertEquals("audio/webm", s.mimeType)
        assertEquals(141_000, s.bitrateBps)
        assertEquals("youtube_direct", s.providerId)
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg", s.resolvedArtworkCandidates.first())
    }

    @Test
    fun skipsCipheredDrcAndVideoFormats() {
        val json = response(
            format(251, 200_000, url = null),
            format(251, 150_000) { it.put("isDrc", true) },
            format(250, 72_000),
        )
        val formats = YoutubeDirectParser.audioFormats(json)
        assertEquals(listOf(250), formats.map { it.itag })
    }

    @Test
    fun prefersTheOriginalTrackOverADub() {
        val json = response(
            format(251, 160_000) { it.put("audioTrack", JSONObject().put("displayName", "Hindi").put("audioIsDefault", false)) },
            format(250, 72_000) { it.put("audioTrack", JSONObject().put("displayName", "English original").put("audioIsDefault", true)) },
        )
        assertEquals(250, YoutubeDirectParser.resolve(json, "abcdefghijk", AudioQuality.AUTO, null, "c", "d").itag)
    }

    @Test
    fun refusalsAllowFallingBackToTheOtherSource() {
        for ((status, reason) in listOf("ERROR" to "This video is unavailable", "LOGIN_REQUIRED" to "Sign in to confirm you're not a bot", "UNPLAYABLE" to "x")) {
            try {
                YoutubeDirectParser.resolve(response(format(251, 1), status = status, reason = reason), "abcdefghijk", AudioQuality.AUTO, null, "c", "d")
                fail("expected a failure for $status")
            } catch (e: ProviderFailure) {
                assertTrue("$status should fall back", e.isFailoverEligible)
            }
        }
    }

    @Test
    fun noDirectAudioIsAFailureNotACrash() {
        try {
            YoutubeDirectParser.resolve(response(format(251, 1, url = null)), "abcdefghijk", AudioQuality.AUTO, null, "c", "d")
            fail("expected a failure")
        } catch (e: ProviderFailure) {
            assertTrue(e.isFailoverEligible)
        }
    }

    @Test
    fun aResponseForAnotherVideoIsRejected() {
        try {
            YoutubeDirectParser.resolve(response(format(251, 1), id = "zzzzzzzzzzz"), "abcdefghijk", AudioQuality.AUTO, null, "c", "d")
            fail("expected a failure")
        } catch (e: ProviderFailure) {
            assertTrue(e.isFailoverEligible)
        }
    }

    @Test
    fun playerInfoDropsTheTopicSuffix() {
        val info = YoutubeDirectParser.playerInfo(full, "abcdefghijk")
        assertEquals("Artist", info.artist)
        assertEquals(200_000L, info.durationMs)
        assertNull(info.category)
    }
}
