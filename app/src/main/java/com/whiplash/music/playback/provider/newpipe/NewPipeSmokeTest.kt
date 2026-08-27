package com.whiplash.music.playback.provider.newpipe

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Phase 7a verification helper: proves NewPipeExtractor can perform a real
 * search and resolve a real playable stream URL for a known YouTube video,
 * with no PO-token blocker. Not part of the permanent provider API (that is
 * built in Phase 7b as PlaybackProvider) — this is a throwaway smoke test
 * used to validate the dependency actually works before building the
 * abstraction around it.
 */
object NewPipeSmokeTest {

    private const val TAG = "NewPipeSmokeTest"

    /** A long-standing, stable public YouTube video used only for the smoke test. */
    private const val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"

    suspend fun runSearchAndStreamResolution(): Result = withContext(Dispatchers.IO) {
        try {
            val youtube = NewPipe.getService("YouTube")

            val searchExtractor = youtube.getSearchExtractor("lofi hip hop")
            searchExtractor.fetchPage()
            val searchResultCount = searchExtractor.initialPage.items.size
            Log.i(TAG, "Search returned $searchResultCount items")

            val streamInfo = StreamInfo.getInfo(youtube, TEST_VIDEO_URL)
            val audioStreams = streamInfo.audioStreams
            val hasPlayableAudio = audioStreams.isNotEmpty()
            val firstAudioUrl = audioStreams.firstOrNull()?.content

            Log.i(TAG, "Stream title: ${streamInfo.name}")
            Log.i(TAG, "Audio stream count: ${audioStreams.size}")
            Log.i(TAG, "Has playable audio: $hasPlayableAudio")

            Result(
                success = true,
                searchResultCount = searchResultCount,
                streamTitle = streamInfo.name,
                audioStreamCount = audioStreams.size,
                hasPlayableAudioUrl = !firstAudioUrl.isNullOrBlank(),
                error = null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Smoke test failed", e)
            Result(
                success = false,
                searchResultCount = 0,
                streamTitle = null,
                audioStreamCount = 0,
                hasPlayableAudioUrl = false,
                error = "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    data class Result(
        val success: Boolean,
        val searchResultCount: Int,
        val streamTitle: String?,
        val audioStreamCount: Int,
        val hasPlayableAudioUrl: Boolean,
        val error: String?,
    )
}
