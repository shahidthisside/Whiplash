package com.whiplash.music.playback.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.playback.cache.TogglableCacheDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())

    /**
     * Live-updating flag read by [TogglableCacheDataSourceFactory] on every
     * new data source request, kept in sync with the real Settings toggle
     * below (see the collector in [onCreate]) — not read once at startup,
     * since the user can flip the setting while a track is already
     * playing and the very next track (or the next chunk of the current
     * one, once ExoPlayer's internal loader opens a fresh DataSource)
     * should immediately reflect the change.
     */
    @Volatile
    private var audioCacheEnabled = true

    override fun onCreate() {
        super.onCreate()
        val app = application as WhiplashApplication

        val cachingFactory = TogglableCacheDataSourceFactory(
            cacheManager = app.audioCacheManager,
            plainFactory = DefaultDataSource.Factory(this),
            isCacheEnabled = { audioCacheEnabled },
            resolveFreshUri = { cacheKey ->
                // Narrow fallback only: fires when PlaybackController's
                // isFullyCached fast-path placeholder URI (see its
                // playIndex) turns out to no longer be backed by a full
                // cache entry by the time the actual read happens (e.g. a
                // concurrent Clear Cache) — not the normal per-track
                // resolve path, which stays entirely in PlaybackController.
                // Runs on a background loading thread; safe to block here,
                // the same way other open-source Media3-backed YouTube
                // clients' own ResolvingDataSource resolvers do (e.g.
                // ViMusic's PlayerService). cacheKey is the stable
                // "SOURCE:id" from PlayableItemMediaItemMapper.mediaIdOf —
                // strip the source prefix back to the bare video id
                // NewPipePlaybackProvider expects.
                val videoId = cacheKey.substringAfter(':', missingDelimiterValue = cacheKey)
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    val quality = app.settingsRepository.audioQuality.first()
                    runCatching { app.newPipePlaybackProvider.getStream(videoId, quality).streamUrl }.getOrNull()
                }
            },
        )

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
            // Real on-disk streaming cache (section: audio caching) — a
            // resolved YouTube stream that was already played is served
            // from disk on a replay instead of re-fetching over the
            // network, the same behavior Spotify/YouTube Music's own
            // caches provide. Toggle lives in Settings and is respected
            // live via the collector below, not just at player-construction
            // time.
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cachingFactory))
            .build()

        app.settingsRepository.audioCacheEnabled
            .onEach { audioCacheEnabled = it }
            .launchIn(serviceScope)

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
        val playbackController = app.playbackController
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
        serviceScope.coroutineContext[Job]?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
