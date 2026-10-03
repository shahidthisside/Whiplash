// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.whiplash.music.playback.controller.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Session callback. Handles playback resumption: a Play from a headset,
 * Bluetooth or the system media controls while the player holds nothing,
 * typically the first one after Android stopped the app.
 */
class WhiplashSessionCallback(
    private val playbackController: PlaybackController,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    /**
     * Hands back the saved song (with a "loading" link, see
     * [PlaybackController.sessionResumeItem]) so the notification shows at
     * once; Media3 then presses play, which the queue-aware player passes to
     * the controller to load the stream and start at the saved position.
     */
    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            val item = runCatching { playbackController.sessionResumeItem() }.getOrNull()
            if (item == null) {
                result.setException(UnsupportedOperationException("No saved queue to resume"))
            } else {
                result.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L))
            }
        }
        return result
    }
}
