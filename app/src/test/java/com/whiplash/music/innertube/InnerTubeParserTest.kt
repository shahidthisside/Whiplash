package com.whiplash.music.innertube

import com.whiplash.music.recommend.UploadKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are trimmed copies of real music.youtube.com responses (Channa Mereya). */
class InnerTubeParserTest {

    private fun fixture(name: String) = JSONObject(javaClass.getResource("/innertube/$name")!!.readText())

    @Test fun radioPageReadsTracksKindsAndCursor() {
        val page = InnerTubeParser.radioPage(fixture("next.json"))
        assertEquals(6, page.items.size)
        val first = page.items.first()
        assertEquals("284Ov7ysmfA", first.track.id)
        assertEquals("Arijit Singh", first.track.artist)
        assertEquals(184_000L, first.track.durationMs)
        assertEquals(UploadKind.OFFICIAL_VIDEO, first.kind)
        // Audio-only tracks (labelled PODCAST_EPISODE when anonymous, no view count).
        val audio = page.items.first { it.track.id == "WWXm39leYew" }
        assertEquals(UploadKind.AUDIO, audio.kind)
        assertEquals("Vishal Mishra", audio.track.artist)
        assertNotNull(page.next)
        assertEquals("RDAMVM284Ov7ysmfA", page.next!!.playlistId)
        assertTrue(page.items.all { it.track.title.isNotBlank() && it.track.artworkUri != null })
    }

    @Test fun continuationPageParses() {
        val page = InnerTubeParser.radioPage(fixture("next_continuation.json"))
        assertEquals(3, page.items.size)
        assertNotNull(page.next)
    }

    @Test fun relatedBrowseIdFound() {
        assertEquals("MPTRt_SKQjgukx2Vr", InnerTubeParser.relatedBrowseId(fixture("next.json")))
    }

    @Test fun relatedGivesSongsAndArtistsButNotOtherPerformances() {
        val related = InnerTubeParser.related(fixture("related.json"))
        val ids = related.songs.map { it.track.id }
        assertTrue(ids.toString(), "Pm0Ga7R-vrM" in ids)
        val song = related.songs.first { it.track.id == "Pm0Ga7R-vrM" }
        assertEquals(UploadKind.AUDIO, song.kind)
        assertEquals("Arijit Singh", song.track.artist)
        assertTrue(song.track.artworkUri!!.contains("w544-h544"))
        assertTrue(related.artists.isNotEmpty())
        // "Other performances" are live/cover takes of the seed itself.
        assertFalse(related.songs.any { it.track.title.contains("Live", ignoreCase = true) && it.track.title.contains("Channa") })
    }

    @Test fun kindsAndLengths() {
        assertEquals(UploadKind.AUDIO, InnerTubeParser.kindOf("MUSIC_VIDEO_TYPE_ATV", hasViews = true))
        assertEquals(UploadKind.USER_VIDEO, InnerTubeParser.kindOf("MUSIC_VIDEO_TYPE_UGC", hasViews = true))
        assertEquals(UploadKind.OFFICIAL_VIDEO, InnerTubeParser.kindOf(null, hasViews = true))
        assertEquals(245_000L, InnerTubeParser.parseLength("4:05"))
        assertEquals(3_723_000L, InnerTubeParser.parseLength("1:02:03"))
        assertEquals(0L, InnerTubeParser.parseLength(null))
        assertEquals(0L, InnerTubeParser.parseLength("live"))
    }

    @Test fun emptyResponseIsEmptyPage() {
        val page = InnerTubeParser.radioPage(JSONObject())
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }
}
