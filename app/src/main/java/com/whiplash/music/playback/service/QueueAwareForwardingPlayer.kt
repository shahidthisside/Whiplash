// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
// Media3 caching/data-source/forwarding APIs used here are @UnstableApi;
// opting in file-wide records that this is a deliberate dependency.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.whiplash.music.playback.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.whiplash.music.playback.controller.PlaybackController

// build-origin 0x532e416e73617269
/**
 * Wraps the service's real [ExoPlayer][androidx.media3.exoplayer.ExoPlayer]
 * so the system (notification/lock screen/Bluetooth/OEM "island" surfaces)
 * sees accurate Next/Previous availability and routes those commands to
 * the app's actual queue logic.
 *
 * Root cause this fixes: the underlying ExoPlayer instance is only ever
 * given ONE [androidx.media3.common.MediaItem] at a time (see
 * [PlaybackController.startMediaItem]) — YouTube tracks require an async
 * network resolve for a playable stream URL before a MediaItem can even
 * be constructed, so the whole app manages its own `queue`/`currentIndex`
 * list rather than handing ExoPlayer a real multi-item timeline. Media3
 * derives COMMAND_SEEK_TO_NEXT_MEDIA_ITEM/COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
 * (and therefore the notification's Skip Next/Previous buttons, and the
 * legacy PlaybackState.ACTION_SKIP_TO_NEXT bit lock screens/Bluetooth/OEM
 * surfaces read) purely from the player's own timeline size — with a
 * single-item timeline, ACTION_SKIP_TO_NEXT is never advertised. Confirmed
 * via real on-device `dumpsys media_session`/`dumpsys notification`
 * checks: actions bitmask had ACTION_SKIP_TO_PREVIOUS but NOT
 * ACTION_SKIP_TO_NEXT, and the real notification had only two actions
 * ("Seek to previous item", "Pause") — Next never appeared.
 *
 * Fix: force these two commands to always be reported as available
 * whenever [PlaybackController.hasNext]/[PlaybackController.hasPrevious]
 * say the app's own queue actually has a next/previous item (already
 * correctly accounting for shuffle/repeat/queue-boundary logic), and
 * forward the actual seek-to-next/previous calls into [PlaybackController],
 * which resolves the real next/previous
 * [com.whiplash.music.domain.model.PlayableItem] (including an async
 * YouTube stream resolve) instead of relying on ExoPlayer's own
 * (nonexistent) next item. Media3 re-evaluates these overrides on every
 * natural player event, so the notification/lock screen/OEM surfaces
 * pick up the correct availability without any extra plumbing.
 */
class QueueAwareForwardingPlayer(
    player: Player,
    private val controller: PlaybackController,
) : ForwardingPlayer(player) {

    /** Current augmented commands, recomputed from live app queue state. */
    private fun augmentedCommands(base: Player.Commands): Player.Commands {
        val builder = base.buildUpon()
        if (controller.hasNext()) {
            builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            builder.add(Player.COMMAND_SEEK_TO_NEXT)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            builder.remove(Player.COMMAND_SEEK_TO_NEXT)
        }
        // Media3 draws a distinction the two commands below mirror:
        // SEEK_TO_PREVIOUS is the user-facing Previous button (restart the
        // track once a few seconds in, otherwise go back), while
        // SEEK_TO_PREVIOUS_MEDIA_ITEM always means "the item before this one".
        if (controller.hasPrevious()) builder.add(Player.COMMAND_SEEK_TO_PREVIOUS) else builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
        if (controller.hasPreviousItem()) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return builder.build()
    }

    override fun getAvailableCommands(): Player.Commands = augmentedCommands(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT -> controller.hasNext()
            Player.COMMAND_SEEK_TO_PREVIOUS -> controller.hasPrevious()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.hasPreviousItem()
            else -> super.isCommandAvailable(command)
        }
    }

    /**
     * The notification, lock screen, Control Center, headset and Bluetooth
     * all press Play by calling prepare() (when the player is idle) and then
     * play() on this player. Neither can bring back a song the player no
     * longer holds, so those cases are handed to [PlaybackController], which
     * does what the in-app Play button does: load the song again and carry
     * on where it was.
     */
    override fun prepare() {
        // A song play() is about to load again (or nothing to prepare).
        if (mediaItemCount == 0 || controller.needsReloadToPlay(this)) return
        super.prepare()
    }

    override fun play() {
        if (controller.playFromSession(this)) return
        super.play()
    }

    override fun pause() {
        controller.pauseFromSession()
        super.pause()
    }

    override fun hasNextMediaItem(): Boolean = controller.hasNext()

    override fun hasPreviousMediaItem(): Boolean = controller.hasPreviousItem()

    override fun seekToNext() = controller.seekToNext()

    override fun seekToNextMediaItem() = controller.seekToNext()

    override fun seekToPrevious() = controller.seekToPrevious()

    override fun seekToPreviousMediaItem() = controller.seekToPreviousItem()
}
