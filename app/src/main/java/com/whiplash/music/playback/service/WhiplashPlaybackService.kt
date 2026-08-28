package com.whiplash.music.playback.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.whiplash.music.WhiplashApplication

/**
 * Owns the single [ExoPlayer] instance and [MediaSession] for the app.
 *
 * This is the only place ExoPlayer is constructed (section 12: the
 * Activity/Composable must never own the long-lived player lifecycle).
 * The service survives Activity recreation and keeps playing when the app
 * is backgrounded or the screen is off (section 13), as long as Android
 * allows the foreground service to keep running.
 */
class WhiplashPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Wrap the raw player so the system (notification/lock screen/
        // Bluetooth/OEM "island" surfaces) sees real Next/Previous
        // availability and routes those presses into the app's actual
        // queue-aware seek logic, instead of ExoPlayer's own single-item
        // timeline (which never has a "next" item — see
        // QueueAwareForwardingPlayer's doc for the full root cause).
        // Command overrides are re-evaluated by Media3 on every natural
        // player event (play/pause/track change/position tick), which
        // happens frequently during real playback, so the notification/
        // lock screen picks up Next/Previous availability without a
        // custom listener-wrapping scheme (an earlier attempt at that
        // broke notification posting entirely on-device, so it was
        // reverted in favor of this simpler, verified-working approach).
        val playbackController = (application as WhiplashApplication).playbackController
        val forwardingPlayer = QueueAwareForwardingPlayer(player, playbackController)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(WhiplashSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Per Media3 guidance: if there is no ongoing playback when the user
     * removes the app from recents, stop the service instead of lingering
     * in the background with nothing to do.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
