package com.whiplash.music.playback.provider

import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSourcePreferenceTest {

    private class Fake(override val id: String) : PlaybackProvider {
        override val displayName = id
        override fun supports(item: PlayableItem) = true
        override suspend fun getStream(songId: String, quality: AudioQuality, preferredItag: Int?) = throw UnsupportedOperationException()
        override suspend fun getPlayerInfo(songId: String) = throw UnsupportedOperationException()
        override suspend fun providerStatus() = ProviderStatus.HEALTHY
    }

    private val newPipe = Fake("newpipe")
    private val direct = Fake("youtube_direct")
    private val all = listOf(newPipe, direct)

    @Test
    fun automaticTriesNewPipeThenDirect() {
        assertEquals(listOf("newpipe", "youtube_direct"), StreamSourcePreference.AUTO.order(listOf(direct, newPipe)).map { it.id })
    }

    @Test
    fun aPickedSourceIsUsedAlone() {
        assertEquals(listOf("newpipe"), StreamSourcePreference.NEWPIPE.order(all).map { it.id })
        assertEquals(listOf("youtube_direct"), StreamSourcePreference.YOUTUBE_DIRECT.order(all).map { it.id })
    }

    @Test
    fun aMissingSourceNeverLeavesNothing() {
        assertEquals(listOf("newpipe"), StreamSourcePreference.YOUTUBE_DIRECT.order(listOf(newPipe)).map { it.id })
    }
}
