package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeTest {
    private fun t(id: String, title: String, artist: String, dur: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id = id, title = title, artist = artist, album = null, artworkUri = null, durationMs = dur)

    @Test fun tagsFromTitleAndArtist() {
        val sad = VibeTagger.tag("Tanha Dil | Sad Song", "Unknown")
        assertTrue(Mood.SAD in sad.moods)
        assertTrue(sad.energy!! < 0.4f)
        val drill = VibeTagger.tag("8 Asle", "Sukha")
        assertTrue("drill" in drill.genres)
        assertTrue(drill.energy!! > 0.7f)
        assertEquals(1990, VibeTagger.tag("Tu Cheez Badi Hai Mast (1994)", "x").decade)
        assertEquals(1980, VibeTagger.tag("80s Hits", "x").decade)
        val slowed = VibeTagger.tag("Kesariya (Slowed + Reverb)", "Arijit Singh")
        assertEquals(SongVersion.SLOWED, slowed.version)
    }


    @Test fun sessionPrefersItsOwnFeel() {
        val s = SessionVibe()
        s.add(VibeTagger.tag("Channa Mereya", "Arijit Singh"), 3.0)
        val sad = s.similarity(VibeTagger.tag("Tujhe Bhula Diya", "Vishal Mishra"))
        val party = s.similarity(VibeTagger.tag("Party All Night", "Yo Yo Honey Singh"))
        assertTrue("sad=$sad party=$party", sad > party + 0.2)
        assertFalse(s.accepts(SongVersion.SLOWED))
        assertTrue(s.accepts(SongVersion.ORIGINAL))
    }

}
