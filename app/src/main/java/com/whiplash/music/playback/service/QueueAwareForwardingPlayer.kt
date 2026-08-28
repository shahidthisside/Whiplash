package com.whiplash.music.playback.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.whiplash.music.playback.controller.PlaybackController

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
        if (controller.hasPrevious()) {
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        } else {
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
        }
        return builder.build()
    }

    override fun getAvailableCommands(): Player.Commands = augmentedCommands(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT -> controller.hasNext()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS -> controller.hasPrevious()
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = controller.hasNext()

    override fun hasPreviousMediaItem(): Boolean = controller.hasPrevious()

    override fun seekToNext() = controller.seekToNext()

    override fun seekToNextMediaItem() = controller.seekToNext()

    override fun seekToPrevious() = controller.seekToPrevious()

    override fun seekToPreviousMediaItem() = controller.seekToPrevious()
}
