// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

import com.whiplash.music.domain.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongKeyTest {
    private fun t(title: String, artist: String, dur: Long = 200_000L) =
        PlayableItem.YoutubeTrack(id = title + artist, title = title, artist = artist, album = null, artworkUri = null, durationMs = dur)

    private fun dup(a: PlayableItem.YoutubeTrack, b: PlayableItem.YoutubeTrack): Boolean {
        val f = NearDuplicateFilter()
        f.accept(a)
        return !f.accept(b)
    }

    @Test fun coreStripsUploadNoise() {
        assertEquals("g o a t", SongKey.of("Diljit Dosanjh - G.O.A.T. (Official Music Video)", "Diljit Dosanjh").core)
        assertEquals("dawood", SongKey.of("Dawood Lyrical Video | PBX 1 | Sidhu Moose Wala | Byg Byrd |  Latest Punjabi Songs 2018", "T-Series").core)
        assertEquals("cheques", SongKey.of("Shubh - Cheques (Official Music Video)", "SHUBH").core)
        assertEquals("wavy", SongKey.of("WAVY (OFFICIAL VIDEO) KARAN AUJLA | LATEST PUNJABI SONGS 2024", "Karan Aujla").core)
        assertEquals("desi kalakaar", SongKey.of("LYRICAL: Desi Kalakaar Full Song with LYRICS | Yo Yo Honey Singh | Sonakshi Sinha", "T-Series").core)
        assertEquals("तुम ही हो", SongKey.of("तुम ही हो (Lyrics)", "Arijit Singh").core)
    }

    @Test fun reuploadsAndVariantsAreDuplicates() {
        assertTrue(dup(t("Dawood", "Sidhu Moose Wala"), t("Dawood Lyrical Video | PBX 1 | Sidhu Moose Wala | Byg Byrd", "T-Series")))
        assertTrue(dup(t("Wavy", "Karan Aujla"), t("WAVY (OFFICIAL VIDEO) KARAN AUJLA | LATEST PUNJABI SONGS 2024", "Karan Aujla")))
        assertTrue(dup(t("Kesariya", "Arijit Singh"), t("Kesariya (Slowed + Reverb)", "Lofi Vibes", 260_000)))
        assertTrue(dup(t("Kesariya", "Arijit Singh"), t("Kesariya - 8D Audio", "8D Tunes")))
        assertTrue(dup(t("Case", "Diljit Dosanjh", 136_000), t("Diljit Dosanjh: CASE (Official Video) GHOST", "Diljit Dosanjh", 140_000)))
        assertTrue(dup(t("Blinding Lights", "The Weeknd"), t("The Weeknd - Blinding Lights (Official Audio)", "The Weeknd")))
    }

    @Test fun differentSongsAreKept() {
        assertFalse(dup(t("Lover", "Diljit Dosanjh"), t("Lover", "Taylor Swift")))
        assertFalse(dup(t("Shape of You", "Ed Sheeran"), t("Shape of You x Naina (Mashup)", "DJ")))
        assertFalse(dup(t("Love", "A"), t("Love Me Like You Do", "A")))
        assertFalse(dup(t("295", "Sidhu Moose Wala"), t("So High", "Sidhu Moose Wala")))
    }

    @Test fun dashTitlesByLabels() {
        // "Artist - Title" by a label matches the artist's own upload…
        assertTrue(dup(t("Tum Hi Ho", "Arijit Singh"), t("Arijit Singh - Tum Hi Ho", "T-Series")))
        // …and "Title - Artist" too.
        assertTrue(dup(t("Tum Hi Ho", "Arijit Singh"), t("Tum Hi Ho - Arijit Singh", "T-Series")))
        // Two different songs "Arijit Singh - …" by the same label never merge.
        assertFalse(dup(t("Arijit Singh - Tum Hi Ho", "T-Series"), t("Arijit Singh - Channa Mereya", "T-Series")))
    }

    @Test fun dashTitlesWithUnknownArtists() {
        assertTrue(dup(t("Mera Gaana", "Nayi Awaaz"), t("Nayi Awaaz - Mera Gaana", "T-Series")))
        assertTrue(dup(t("Mera Gaana", "Nayi Awaaz"), t("Mera Gaana - Nayi Awaaz", "T-Series")))
        assertFalse(dup(t("Nayi Awaaz - Mera Gaana", "T-Series"), t("Nayi Awaaz - Doosra Gaana", "T-Series")))
        // Same title by two different unknown singers on one label: both stay.
        assertFalse(dup(t("Nayi Awaaz - Mera Gaana", "T-Series"), t("Purani Awaaz - Mera Gaana", "T-Series")))
    }

    @Test fun labelUploadWithActorCredits() {
        // Seen live: artist upload vs label upload crediting the film and actors.
        assertTrue(dup(
            t("Dil Diyan Gallan (From \"Tiger Zinda Hai\")", "Vishal-Shekhar - Topic", 260_000),
            t("Dil Diyan Gallan Full Song | Tiger Zinda Hai | Salman Khan | Katrina Kaif | Atif Aslam", "YRF", 284_000),
        ))
        // Same title from a label but a clearly different length: different song.
        assertFalse(dup(t("Tere Bina", "A. R. Rahman", 300_000), t("Tere Bina | Some Film | Actor Name", "T-Series", 190_000)))
    }

    @Test fun labelUploadsWithCreditsLists() {
        // Seen live: Zaalima by Arijit Singh vs a Zee Music lyrical upload.
        assertTrue(dup(
            t("Zaalima", "Arijit Singh", 300_000),
            t("Zaalima - Lyrical | Raees | Shah Rukh Khan, Mahira Khan | Arijit Singh & Harshdeep Kaur | JAM8", "Zee Music Company", 302_000),
        ))
        assertTrue(dup(
            t("KAUN TUJHE", "Palak Muchhal", 240_000),
            t("KAUN TUJHE  Lyrical | M.S. DHONI -THE UNTOLD STORY | Amaal Mallik Palak | Sushant, Disha", "T-Series", 245_000),
        ))
    }

    @Test fun onlyUploadIsKept() {
        // A song that only exists as a lyric video stays.
        val list = listOf(t("Dil Diyan Gallan (Lyrical Video)", "T-Series"), t("Kesariya", "Arijit Singh"))
        assertEquals(2, list.withoutNearDuplicates().size)
    }

    @Test fun bestUploadRepresentsTheSong() {
        val list = listOf(
            t("Kesariya (Slowed + Reverb)", "Lofi Beats"),
            t("Heeriye", "Arijit Singh"),
            t("Kesariya (Lyrical)", "Arijit Singh"),
            t("Kesariya", "Arijit Singh - Topic"),
        )
        val out = list.withoutNearDuplicates()
        assertEquals(listOf("Kesariya", "Heeriye"), out.map { it.title })
    }

    @Test fun sameTitleDifferentSongsWithoutArtistInfo() {
        // Both uploaded by labels, same name, very different length: different songs.
        assertFalse(dup(t("Tere Bina", "T-Series", 180_000), t("Tere Bina", "Zee Music Company", 330_000)))
        // Same length: same song.
        assertTrue(dup(t("Tere Bina", "T-Series", 240_000), t("Tere Bina (Lyrics)", "Zee Music Company", 250_000)))
    }

    @Test fun strictKeepsVersionsForSearch() {
        val f = NearDuplicateFilter(strictVersions = true)
        assertTrue(f.accept(t("Kesariya", "Arijit Singh")))
        assertTrue(f.accept(t("Kesariya (Slowed + Reverb)", "Arijit Singh")))
        assertFalse(f.accept(t("Kesariya (Official Video)", "Arijit Singh")))
    }

    @Test fun versions() {
        assertEquals(SongVersion.SLOWED, SongKey.versionOf("Kesariya (Slowed + Reverb)"))
        assertEquals(SongVersion.LOFI, SongKey.versionOf("Tum Hi Ho Lofi Flip"))
        assertEquals(SongVersion.LIVE, SongKey.versionOf("Hass Hass (Live at Coachella)"))
        assertEquals(SongVersion.ORIGINAL, SongKey.versionOf("Live Your Life"))
        assertEquals(SongVersion.REMIX, SongKey.versionOf("Tauba Tauba (Remix)"))
    }

    @Test fun officialAudioBeatsItsMusicVideo() {
        val video = PlayableItem.YoutubeTrack("vid1", "Channa Mereya", "Arijit Singh", null, null, 290_000L)
        val audio = PlayableItem.YoutubeTrack("aud1", "Channa Mereya", "Arijit Singh", null, null, 289_000L)
        UploadKinds.record("vid1", UploadKind.OFFICIAL_VIDEO)
        UploadKinds.record("aud1", UploadKind.AUDIO)
        assertEquals(listOf("aud1"), listOf(video, audio).withoutNearDuplicates().map { it.id })
    }


    @Test fun coCreditedUploadMatchesSoloCredit() {
        // YouTube Music credits "Pritam & Arijit Singh"; another upload credits only Arijit.
        val a = PlayableItem.YoutubeTrack("1", "Janam Janam", "Pritam & Arijit Singh", null, null, 238_000L)
        val b = PlayableItem.YoutubeTrack("2", "Janam Janam", "Arijit Singh", null, null, 290_000L)
        assertEquals(1, listOf(a, b).withoutNearDuplicates().size)
        val d = PlayableItem.YoutubeTrack("4", "Janam Janam - Dilwale | Shah Rukh Khan | Kajol", "Arijit Singh", null, null, 300_000L)
        assertEquals(1, listOf(a, d).withoutNearDuplicates().size)
        // Different artists sharing a title stay apart.
        val c = PlayableItem.YoutubeTrack("3", "Janam Janam", "Atif Aslam & Someone", null, null, 200_000L)
        assertEquals(2, listOf(a, c).withoutNearDuplicates().size)
    }

}
