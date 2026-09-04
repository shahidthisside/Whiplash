package com.whiplash.music.playback.controller
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.provider.FallbackResult
import com.whiplash.music.playback.provider.PlaybackManager
import com.whiplash.music.playback.provider.newpipe.NewPipePlaybackProvider
import com.whiplash.music.playback.service.WhiplashPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The only class that talks to a Media3 [MediaController] directly
 * (section 12: Compose UI -> ViewModel -> PlaybackController ->
 * MediaController -> MediaSessionService -> ExoPlayer).
 *
 * ViewModels depend on this class, never on [MediaController] or
 * [androidx.media3.exoplayer.ExoPlayer] directly, so the playback
 * transport can evolve without UI-layer changes.
 *
 * Owns a domain-level queue (section 21) as the source of truth, separate
 * from Media3's own internal playlist, because [PlayableItem.YoutubeTrack]
 * entries need an async provider resolve (section 8) before Media3 can be
 * given a real URI — something Media3's playlist APIs alone can't express.
 * [LocalTrack][PlayableItem.LocalTrack] entries have a URI immediately and
 * play with no added latency.
 */
class PlaybackController(
    private val context: Context,
    private val playbackManager: PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val newPipePlaybackProvider: NewPipePlaybackProvider,
    private val audioCacheManager: com.whiplash.music.playback.cache.AudioCacheManager,
) {

    private var controller: MediaController? = null
    private var connectionFuture: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTickerJob: Job? = null

    /** Guards against double-handling the same STATE_ENDED event (see [handleTrackEnded]). Reset whenever a new playback attempt actually starts (see [startMediaItem]). */
    private var handledEnded: Boolean = false

    /** Guards against a stale resolve (from a previous playIndex call) landing after a newer one started. */
    private var resolveGeneration: Int = 0

    /** See refreshPositionAndDuration()'s backward-jump-suppression comment: which media ID has already used its one allowed suppression. */
    private var backwardJumpSuppressedForTrack: String? = null

    /** Domain-level queue, source of truth for section 21 queue features. */
    private var queue: MutableList<PlayableItem> = mutableListOf()
    private var currentIndex: Int = -1

    /** Tracks which queue indices have already had a Media3 MediaItem prepared, to avoid re-resolving. */
    private val preparedIndices = mutableSetOf<Int>()

    /** Pre-resolved stream for the upcoming track, filled in shortly before the current track ends (section 18: gapless). */
    private var prefetched: PrefetchedStream? = null
    private var prefetchJob: Job? = null

    private data class PrefetchedStream(val forItemId: String, val streamUrl: String, val artworkUrl: String?)

    private var sleepTimerJob: Job? = null

    /**
     * High-res artwork URLs already resolved for tracks the user hasn't
     * played yet (currently: the immediate next queue item, kept in sync
     * with [prefetched] in [prefetchNeighborStreamsAndArtwork]). Keyed by
     * [PlayableItemMediaItemMapper.mediaIdOf] so it's safe to look up by
     * the same stable id used everywhere else in this class. Consulted by
     * [playIndex] so a track that already has its high-res artwork ready
     * never displays the original — often lower-resolution — artwork at
     * all, eliminating the low-res-then-high-res "blink" rather than just
     * deduplicating the transition animation around it.
     */
    private val artworkPreloadCache = mutableMapOf<String, String>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        // Real, reported bug: turning Autoplay off, then back on again while
        // a track is already playing and sitting at the end of the queue,
        // did nothing — the queue never extended, so the track still just
        // played to the end and stopped. maybeExtendQueueWithRecommendations
        // was previously only ever invoked from inside playIndex() (when a
        // track *starts*) or clearQueueExceptCurrent() — never in reaction to
        // the setting itself changing — so flipping Autoplay back on while
        // already sitting on the last queue item had nothing to trigger it
        // until the user manually skipped away and back, which forced a
        // fresh playIndex() call. Observing the setting directly here and
        // reacting to an off->on transition closes that gap: if the
        // currently playing track happens to already be the last queue item
        // at that moment, the queue is extended immediately, matching what
        // would have happened had Autoplay simply been on the whole time.
        // drop(1) skips the flow's initial replay so this only reacts to an
        // actual user toggle, not the first read of a Flow.
        scope.launch {
            settingsRepository.autoplayEnabled
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    if (!enabled) return@collect
                    val current = _state.value.currentItem as? PlayableItem.YoutubeTrack ?: return@collect
                    if (nextIndex() != null) return@collect // not at the end of the queue; nothing to extend
                    maybeExtendQueueWithRecommendations(current)
                }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            updatePositionTicker(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            // Reaching READY means this track is genuinely playing, so re-arm
            // its single automatic error re-resolve (see [handlePlayerError]).
            // Without this the budget was spent per *track* rather than per
            // *failure*: a stream URL that expired, self-healed, then played
            // fine for another half hour could not self-heal a second time
            // when the replacement URL also expired — and googlevideo URLs are
            // time-limited, so one long listening session genuinely outlives
            // more than one of them. Clearing the flag here also clears any
            // error surfaced for a previous attempt at this same track.
            if (playbackState == Player.STATE_READY) {
                errorRecoveryAttemptedForItemId = null
                if (_state.value.playbackError != null) {
                    _state.update { it.copy(playbackError = null) }
                }
            }
            // Handle natural end-of-track directly and immediately here
            // (section 13 autoplay), rather than relying solely on the
            // 500ms position-poll loop in updatePositionTicker: that loop
            // is cancelled by onIsPlayingChanged(false) — which ExoPlayer
            // also fires the moment STATE_ENDED is reached, since isPlaying
            // = playWhenReady && state == STATE_READY becomes false at
            // STATE_ENDED — creating a real race where the ticker job can
            // be cancelled before it gets to act on STATE_ENDED, silently
            // stopping playback instead of advancing (confirmed as the
            // root cause of a real user-reported bug: "song plays fully
            // then stops" despite Autoplay=on and a non-empty queue).
            if (playbackState == Player.STATE_ENDED) handleTrackEnded()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            refreshPositionAndDuration()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            refreshPositionAndDuration()
        }

        /**
         * Real, silent-failure bug this fixes: this listener previously had
         * NO onPlayerError override at all, anywhere in the app (and
         * [PlaybackState.playbackError] was only ever set non-null from the
         * *stream-resolution* failure branch in [playIndex] — never from an
         * actual player failure). ExoPlayer reports every fatal playback
         * failure exclusively through this callback and then drops to
         * STATE_IDLE, which is NOT STATE_ENDED, so [handleTrackEnded] never
         * ran either. The result was that any player-level failure stopped
         * the music dead with zero feedback: no error, no toast, no retry,
         * no advance — the UI just sat there looking paused forever.
         *
         * The most likely trigger in normal use is an expired stream URL.
         * googlevideo URLs are time-limited, which this app already knew —
         * [com.whiplash.music.playback.provider.ResolvedStream.expiresAtEpochMs]
         * was being computed on every resolve — but that field had zero
         * readers, so nothing ever acted on it. A URL resolved a while ago
         * (prefetched into [prefetched] for gapless, or simply a track left
         * paused for a long time) comes back HTTP 403 and playback died
         * silently.
         *
         * Recovery: a YouTube track gets exactly ONE automatic re-resolve
         * with a freshly fetched URL, resuming at the position it failed at,
         * which makes an expired URL self-heal invisibly. Anything else — a
         * second consecutive failure for the same track, a local file, or a
         * downloaded file whose bytes are gone — surfaces a real, visible
         * error instead of failing silently. Bounded to one attempt on
         * purpose so a genuinely dead track can never spin in a retry loop.
         */
        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    /**
     * Tracks which item we've already burned our single automatic
     * re-resolve on, so a permanently broken track fails visibly on its
     * second error rather than looping. Reset in [startMediaItem] whenever
     * a genuinely new playback attempt begins.
     */
    private var errorRecoveryAttemptedForItemId: String? = null

    private fun handlePlayerError(error: PlaybackException) {
        val current = _state.value.currentItem
        // Clamped against the track's own duration: the raw currentPosition is
        // read at error time, and if the error for an outgoing track lands just
        // after the user already skipped, an unclamped value could seek the new
        // track past its end. A position at or beyond the end is treated as
        // "restart from the beginning" rather than an invalid seek.
        val rawPositionMs = (controller?.currentPosition ?: 0L).coerceAtLeast(0L)
        val durationMs = current?.durationMs ?: 0L
        val failedPositionMs = if (durationMs > 0L && rawPositionMs >= durationMs) 0L else rawPositionMs
        val track = current as? PlayableItem.YoutubeTrack

        // Not recoverable by re-resolving: local files and downloaded files
        // point at real paths on disk, so an error there means the file is
        // missing/corrupt, and a fresh network resolve wouldn't be what the
        // user asked to play anyway. A repeat failure for the same track is
        // also treated as genuinely broken.
        if (track == null || errorRecoveryAttemptedForItemId == track.id) {
            surfacePlayerError(current, error)
            return
        }

        errorRecoveryAttemptedForItemId = track.id
        // A stale prefetched URL for this same item would just fail again.
        if (prefetched?.forItemId == track.id) prefetched = null

        scope.launch {
            val quality = settingsRepository.effectiveAudioQuality()
            val result = try {
                kotlinx.coroutines.withTimeout(RESOLVE_STREAM_TIMEOUT_MS) {
                    playbackManager.resolveStream(track, quality)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                null
            }
            // The user may have skipped/stopped while we were re-resolving;
            // never yank a different track out from under them.
            if (_state.value.currentItem?.id != track.id) return@launch
            val fresh = (result as? FallbackResult.Success)?.value
            if (fresh == null) {
                surfacePlayerError(track, error)
                return@launch
            }
            startMediaItem(track, resolvedStreamUrl = fresh.streamUrl, resumeAtMs = failedPositionMs, isErrorRecovery = true)
        }
    }

    private fun surfacePlayerError(item: PlayableItem?, error: PlaybackException) {
        val isNetwork = error.errorCode in NETWORK_ERROR_CODES
        _state.update {
            it.copy(
                isResolvingStream = false,
                isBuffering = false,
                playbackError = PlaybackError(
                    itemTitle = item?.title ?: "This track",
                    message = if (isNetwork) "Couldn't stream this track. Check your connection." else "This track couldn't be played.",
                    isNetworkFailure = isNetwork,
                ),
            )
        }
    }

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null || connectionFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, WhiplashPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        connectionFuture = future
        future.addListener(
            {
                controller = future.get().also { it.addListener(playerListener) }
                refreshPositionAndDuration()
                onReady()
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        positionTickerJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        connectionFuture = null
    }

    /** Replaces the queue with [items] and starts playback at [startIndex] (section 21). */
    fun playQueue(items: List<PlayableItem>, startIndex: Int) {
        if (items.isEmpty()) return
        queue = items.toMutableList()
        preparedIndices.clear()
        currentIndex = startIndex.coerceIn(0, queue.lastIndex)
        _state.update { it.copy(queue = queue.toList(), currentIndex = currentIndex) }
        playIndex(currentIndex)
    }

    /** Convenience for the common single-track case: replaces the queue with just [item]. */
    fun playNow(item: PlayableItem) = playQueue(listOf(item), 0)

    /** Appends [item] to the end of the queue without interrupting current playback (section 21: "add to queue"). */
    fun addToQueue(item: PlayableItem) {
        queue.add(item)
        _state.update { it.copy(queue = queue.toList()) }
        ToastController.show("Added to queue")
    }

    /** Inserts [item] immediately after the currently playing track (section 21: "play next"). */
    fun playNext(item: PlayableItem) {
        val insertAt = (currentIndex + 1).coerceIn(0, queue.size)
        queue.add(insertAt, item)
        shiftPreparedIndicesAfterInsert(insertAt)
        _state.update { it.copy(queue = queue.toList()) }
        ToastController.show("Playing next")
    }

    /** Removes the item at [index]. If it's the currently playing item, advances to the next one. */
    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val wasCurrentIndex = index == currentIndex
        queue.removeAt(index)
        preparedIndices.remove(index)
        val shifted = preparedIndices.filter { it > index }.toSet()
        preparedIndices.removeAll { it > index }
        preparedIndices.addAll(shifted.map { it - 1 })

        when {
            queue.isEmpty() -> {
                currentIndex = -1
                controller?.stop()
                controller?.clearMediaItems()
                _state.update { it.copy(queue = emptyList(), currentIndex = -1, currentItem = null, isPlaying = false) }
            }
            index < currentIndex -> {
                currentIndex -= 1
                controller?.removeMediaItem(index)
                _state.update { it.copy(queue = queue.toList(), currentIndex = currentIndex) }
            }
            index == currentIndex -> {
                // Removed the now-playing track: play whatever now occupies this index (or stop if it was last).
                _state.update { it.copy(queue = queue.toList()) }
                if (currentIndex > queue.lastIndex) currentIndex = queue.lastIndex
                if (currentIndex >= 0) playIndex(currentIndex) else {
                    controller?.stop()
                    controller?.clearMediaItems()
                    _state.update { it.copy(currentItem = null, isPlaying = false) }
                }
            }
            else -> _state.update { it.copy(queue = queue.toList()) }
        }

        // Removing the currently playing track has an immediately visible
        // effect (the next track starts, or playback stops) — a toast on
        // top of that is redundant. Removing any other row is much less
        // obviously confirmed (especially if that part of the queue sheet
        // isn't even in view), so it gets the same brief confirmation as
        // every other queue action.
        if (!wasCurrentIndex) ToastController.show("Removed from queue")
    }

    /** Moves a queue item from [from] to [to] (section 21: "reorder"). */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        currentIndex = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        preparedIndices.clear() // conservative: re-resolve on demand rather than track a shifted mapping through an arbitrary move
        _state.update { it.copy(queue = queue.toList(), currentIndex = currentIndex) }
    }

    /** Removes every item except the currently playing one (section 21: "clear"). */
    fun clearQueueExceptCurrent() {
        if (currentIndex < 0 || queue.isEmpty()) return
        val current = queue[currentIndex]
        queue = mutableListOf(current)
        currentIndex = 0
        preparedIndices.clear()
        _state.update { it.copy(queue = queue.toList(), currentIndex = 0) }
        ToastController.show("Queue cleared")

        // maybeExtendQueueWithRecommendations() is normally only triggered
        // from playIndex() when a track *starts* playing and happens to be
        // the last queue item. Clearing the queue also makes the current
        // track the last item, but without a fresh playIndex() call — so
        // without this, autoplay would never get a chance to extend the
        // queue again, and the track would just stop dead when it finished
        // naturally, even with Autoplay enabled (confirmed via real device
        // testing: cleared the queue mid-playback, let the track finish,
        // and playback stopped instead of continuing).
        (current as? PlayableItem.YoutubeTrack)?.let { maybeExtendQueueWithRecommendations(it) }
    }

    private fun shiftPreparedIndicesAfterInsert(insertAt: Int) {
        val shifted = preparedIndices.filter { it >= insertAt }.map { it + 1 }
        preparedIndices.removeAll { it >= insertAt }
        preparedIndices.addAll(shifted)
    }

    /**
     * Starts playback of the queue item at [index]. [PlayableItem.LocalTrack]
     * plays immediately (URI already known). [PlayableItem.YoutubeTrack]
     * first resolves a stream via [playbackManager] (automatic fallback,
     * section 8) — [PlaybackState.isResolvingStream]=true immediately so
     * there is no perceived dead air while that network round-trip happens.
     */
    private fun playIndex(index: Int) {
        if (index !in queue.indices) return
        val item = queue[index]
        currentIndex = index
        val generation = ++resolveGeneration
        prefetchJob?.cancel()

        // Stop the previous track's audio immediately (not just update the
        // UI state) so switching tracks is instant to the ear — otherwise
        // the old MediaItem keeps audibly playing for the entire async
        // stream-resolve window below, which is what made it sound like
        // "the previous song is still playing" when skipping to a new one.
        // Local tracks resolve synchronously right after this anyway, so
        // this only causes a brief, expected silence for YouTube tracks
        // (filled by isResolvingStream's buffering indicator in the UI).
        controller?.pause()
        controller?.volume = 1f // undo any in-progress fade from the track this interrupted

        // If we already have this track's high-res artwork cached (from a
        // gapless prefetch, or because it's already sitting in the
        // artworkPreloadCache below), show that immediately instead of the
        // item's original — often lower-resolution — search-time thumbnail.
        // This is what actually eliminates the low-res-then-high-res
        // "blink" for prefetched transitions, rather than just deduplicating
        // the transition animation (the contentKey fix from before): here
        // there is no low-res frame shown at all, because we never assign
        // the item's original artworkUri in the first place.
        val mediaId = PlayableItemMediaItemMapper.mediaIdOf(item)
        val cachedArtwork = (prefetched?.takeIf { it.forItemId == item.id }?.artworkUrl)
            ?: artworkPreloadCache[mediaId]
        artworkPreloadCache.remove(mediaId) // consumed; will be freshly populated for the new neighbors by prefetchNeighborStreamsAndArtwork
        val displayItem = if (cachedArtwork != null && item is PlayableItem.YoutubeTrack) {
            item.copy(artworkUri = cachedArtwork)
        } else item
        if (displayItem !== item && currentIndex in queue.indices) queue[currentIndex] = displayItem

        // Pre-warm Coil's cache for whatever artwork the immediate previous
        // and next queue items already have (their existing artworkUri —
        // whether that's a low-res search thumbnail or an already-upgraded
        // high-res one). This doesn't fetch new high-res URLs by itself
        // (that only happens for the upcoming item via prefetchNeighborStreamsAndArtwork,
        // since it piggybacks on a resolve that's needed anyway) — it just
        // ensures neither neighbor needs a cold network fetch + decode the
        // moment the user actually navigates to it.
        preloadNeighborArtwork(index)

        _state.update {
            it.copy(
                currentItem = displayItem,
                currentIndex = index,
                playbackError = null,
                isPlaying = false,
                // Reset position/duration in the SAME state update that swaps
                // currentItem, rather than waiting for Media3's onEvents/
                // onMediaMetadataChanged callback (which only fires once
                // prepare() completes below). Without this, the artwork/
                // title/artist visibly changed to the new track immediately
                // while the seek bar kept showing the previous track's
                // position/duration until the new stream finished loading —
                // a jarring, half-updated transition rather than one clean
                // instant switch.
                positionMs = 0L,
                durationMs = 0L,
                isResolvingStream = item is PlayableItem.YoutubeTrack && prefetched?.forItemId != item.id,
            )
        }

        // History is deliberately NOT recorded here any more — see
        // [startMediaItem], which records it only once playback actually
        // starts, and only after the track's metadata has been cached.
        // Recording at this point caused two separate real bugs; both are
        // described in detail at that call site.

        // Resolve high-res artwork for BOTH neighbors as soon as this track
        // starts — not just in the last few seconds before it ends (that
        // window only helps if the user waits for a natural transition; a
        // manual tap of Next/Previous at any other point in the track had
        // zero prefetch benefit before this, which is exactly why the
        // "next song artwork still blinks" report kept recurring). This
        // makes the high-res artwork resolve start immediately in the
        // background regardless of when the user actually navigates.
        prefetchNeighborStreamsAndArtwork(index)

        when (item) {
            is PlayableItem.LocalTrack -> startMediaItem(item, resolvedStreamUrl = null)
            is PlayableItem.DownloadedTrack -> startMediaItem(item, resolvedStreamUrl = null)
            is PlayableItem.YoutubeTrack -> {
                val cached = prefetched?.takeIf { it.forItemId == item.id }
                val mediaId = PlayableItemMediaItemMapper.mediaIdOf(item)
                if (cached != null) {
                    // Gapless (section 18): this track's stream was already resolved
                    // while the previous one was still playing, so there is no
                    // network round-trip — and therefore no perceptible gap — here.
                    // Artwork was already applied above (displayItem), so no
                    // separate upgrade step or second state update is needed here.
                    prefetched = null
                    // Real, reported bug found during full regression testing:
                    // this fast path (and the isFullyCached one below) started
                    // playback and extended the queue but never called
                    // cacheSong — only the slow, full network-resolve branch
                    // below did. That meant any track reached via gapless
                    // prefetch or a fully-cached-audio replay never got a row
                    // written to the songs table, so it silently vanished from
                    // Speed dial/Favorites/Playlists (their queries join
                    // against songs for title/artist/artwork) even though
                    // history correctly recorded it — confirmed directly via
                    // sqlite (a real trackId present in history with no
                    // matching row in songs at all). cacheSong is cheap/
                    // idempotent (an upsert), so calling it here has no
                    // downside beyond the one it already has in the slow path.
                    scope.launch { libraryRepository.cacheSong(item) }
                    startMediaItem(displayItem, resolvedStreamUrl = cached.streamUrl)
                    maybeExtendQueueWithRecommendations(item)
                } else if (audioCacheManager.isFullyCached(mediaId)) {
                    // The track's audio is already fully present in the on-disk
                    // cache from a previous play (same stable cache key as
                    // PlayableItemMediaItemMapper.mediaIdOf) — skip the network
                    // stream-resolution round-trip entirely rather than running
                    // it unconditionally just to obtain *a* URL to hand to
                    // ExoPlayer. This was the real root cause of "a cached song
                    // still takes time to reload on replay": the disk cache
                    // alone only ever saved re-downloading bytes once ExoPlayer
                    // already had a URL to open, never the resolve step itself.
                    // The placeholder URI below is safe because
                    // TogglableCacheDataSourceFactory's ResolvingDataSource layer
                    // checks the exact same isFullyCached condition before ever
                    // dereferencing it — for a real cache hit, this URI is
                    // guaranteed never touched over the network. If cache state
                    // somehow changed between this check and the actual read
                    // (e.g. a concurrent Clear Cache), that same layer transparently
                    // falls back to a fresh resolve keyed off resolveFreshUri,
                    // so this is not a fragile assumption — it's a fast-path
                    // hint, not the sole safety mechanism.
                    //
                    // Same real bug as the gapless branch above — see its
                    // comment — applies here too: this fast path also never
                    // called cacheSong, so a replayed fully-cached track
                    // could disappear from Speed dial/Favorites/Playlists.
                    scope.launch { libraryRepository.cacheSong(item) }
                    _state.update { it.copy(isResolvingStream = false) }
                    startMediaItem(displayItem, resolvedStreamUrl = "cache://$mediaId")
                    maybeExtendQueueWithRecommendations(item)
                } else {
                    // Real, on-device-confirmed problem: maybeExtendQueueWithRecommendations
                    // used to only be called AFTER playbackManager.resolveStream()
                    // succeeded — but these two operations are fully
                    // independent (one fetches the audio stream URL, the
                    // other fetches related-track recommendations), so
                    // waiting for one before starting the other needlessly
                    // serialized them. Measured on-device: stream resolve
                    // ~2.6s, then getRelatedTracks ~2.1s, then category
                    // checks ~4-5s — all stacked sequentially added up to
                    // ~9-10 seconds before the queue actually grew, on the
                    // very first track played from a fresh queue (Quick
                    // Picks/Search/History/Speed dial). Starting both at
                    // once lets the recommendation fetch's network time
                    // overlap with the stream resolve's, instead of adding
                    // on top of it.
                    maybeExtendQueueWithRecommendations(item)
                    scope.launch {
                        val quality = settingsRepository.effectiveAudioQuality()
                        libraryRepository.cacheSong(item)
                        // Wrapped in withTimeout: playbackManager.resolveStream()
                        // (and the NewPipeExtractor calls under it) has no
                        // internal timeout of its own beyond individual HTTP
                        // calls' connect/read timeouts (see OkHttpClient in
                        // WhiplashApplication) — a StreamInfo.getInfo() resolve
                        // makes several sequential requests, so a slow/stuck
                        // upstream can compound well past any single request's
                        // timeout without ever throwing. Confirmed as a real
                        // bug via on-device testing: a track sat in
                        // isResolvingStream/buffering for over 10 minutes with
                        // zero error surfaced, until the system's own
                        // notification-manager timeout (unrelated to this
                        // app's own error handling) eventually intervened.
                        // This bounds the whole resolve attempt so the user
                        // always gets a real, actionable error within a
                        // reasonable window instead of an indefinitely stuck
                        // loading indicator.
                        val result = try {
                            kotlinx.coroutines.withTimeout(RESOLVE_STREAM_TIMEOUT_MS) {
                                playbackManager.resolveStream(item, quality)
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            FallbackResult.Failure(
                                com.whiplash.music.playback.provider.ProviderFailure.NetworkFailure(
                                    "Timed out resolving ${item.title}",
                                ),
                                attempts = emptyList(),
                            )
                        }
                        if (generation != resolveGeneration) return@launch // superseded by a newer playIndex call

                        when (result) {
                            is FallbackResult.Success -> {
                                val upgraded = upgradeArtworkIfCurrent(item, result.value.resolvedArtworkUrl)
                                _state.update { it.copy(isResolvingStream = false, currentItem = upgraded ?: it.currentItem) }
                                startMediaItem(upgraded ?: item, resolvedStreamUrl = result.value.streamUrl)
                                // maybeExtendQueueWithRecommendations(item) now
                                // called upfront, in parallel with this resolve
                                // (see the comment where this scope.launch
                                // starts) — removed the duplicate call that used
                                // to be here.
                                // Persist the upgraded (higher-res) artwork
                                // over the earlier cacheSong() call's
                                // search-time thumbnail, so History/
                                // Favorites/Playlists reconstructing this
                                // track later via LibraryRepository also
                                // get the better artwork instead of always
                                // falling back to the lower-res one that
                                // was cached before this resolve completed.
                                if (upgraded is PlayableItem.YoutubeTrack) {
                                    scope.launch { libraryRepository.cacheSong(upgraded) }
                                }
                            }
                            is FallbackResult.Failure -> {
                                _state.update {
                                    it.copy(
                                        isResolvingStream = false,
                                        playbackError = PlaybackError(
                                            itemTitle = item.title,
                                            message = result.failure.message ?: "Playback failed",
                                            isNetworkFailure = result.failure is com.whiplash.music.playback.provider.ProviderFailure.NetworkFailure,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Pre-resolves the stream + high-res artwork for the upcoming queue
     * item, AND just the high-res artwork for the previous one, as soon as
     * the current track starts playing (section 18: gapless; also the fix
     * for artwork "blinking" on manual skip — see call site in [playIndex]
     * for why this must run immediately rather than only in the last few
     * seconds of the current track). The next item gets its full stream
     * resolved (not just artwork) since that's also what makes forward
     * gapless transitions possible; the previous item only needs artwork
     * since going backward doesn't reuse a cached stream the same way.
     */
    private fun prefetchNeighborStreamsAndArtwork(fromIndex: Int) {
        prefetchJob?.cancel()
        val nextItem = nextIndex()?.let { queue.getOrNull(it) } as? PlayableItem.YoutubeTrack
        val prevItem = previousIndex()?.let { queue.getOrNull(it) } as? PlayableItem.YoutubeTrack

        if (nextItem != null && prefetched?.forItemId != nextItem.id) {
            prefetchJob = scope.launch {
                try {
                    // Real, reported bug (UAT audit finding): the
                    // "Gapless Playback" setting was stored/displayed but
                    // never actually read anywhere — this stream prefetch
                    // (the actual mechanism that makes gapless transitions
                    // possible: resolving the next track's stream ahead
                    // of time so playIndex() doesn't hit its usual brief
                    // isResolvingStream window when it starts) ran
                    // unconditionally regardless of the setting. Checking
                    // it here means turning the toggle off genuinely
                    // disables the gapless behavior it claims to control
                    // — the next track then resolves normally (with its
                    // usual brief loading window) exactly like this
                    // function's own catch block already describes for a
                    // failed prefetch. Deliberately does NOT gate the
                    // artwork-prefetch blocks below (this same function,
                    // and the previous-item block further down) — that's
                    // a separate "avoid an artwork blink on skip" concern
                    // unrelated to gapless audio, and turning gapless off
                    // shouldn't bring back the artwork-blink bug as a
                    // side effect.
                    if (!settingsRepository.gaplessEnabled.first()) return@launch
                    val quality = settingsRepository.effectiveAudioQuality()
                    // Same withTimeout guard as the main resolve path in
                    // playIndex() — without it, a hung upstream here would
                    // leak this prefetch coroutine forever rather than
                    // falling through to the existing "prefetch is only a
                    // latency optimization" catch block below.
                    val result = kotlinx.coroutines.withTimeout(RESOLVE_STREAM_TIMEOUT_MS) {
                        playbackManager.resolveStream(nextItem, quality)
                    }
                    if (result is FallbackResult.Success) {
                        prefetched = PrefetchedStream(nextItem.id, result.value.streamUrl, result.value.resolvedArtworkUrl)
                        result.value.resolvedArtworkUrl?.let { url ->
                            artworkPreloadCache[PlayableItemMediaItemMapper.mediaIdOf(nextItem)] = url
                            preloadArtworkBitmap(url)
                        }
                    }
                } catch (_: Exception) {
                    // Prefetch is a latency optimization only; a failure here just
                    // means the next track resolves normally (with its usual
                    // brief isResolvingStream window) when it actually starts.
                }
            }
        }

        if (prevItem != null && artworkPreloadCache[PlayableItemMediaItemMapper.mediaIdOf(prevItem)] == null) {
            scope.launch {
                try {
                    // getPlayerInfo (not getStream) is used here deliberately:
                    // we only want the metadata (for its high-res artwork),
                    // not a playable stream URL, since navigating backward
                    // re-resolves a fresh stream at that time anyway (a
                    // stream resolved now could expire before the user
                    // actually goes back to it).
                    val artworkUrl = newPipePlaybackProvider.getPlayerInfo(prevItem.id).artworkUrl
                    if (artworkUrl != null) {
                        artworkPreloadCache[PlayableItemMediaItemMapper.mediaIdOf(prevItem)] = artworkUrl
                        preloadArtworkBitmap(artworkUrl)
                    }
                } catch (_: Exception) {
                    // Same as above: artwork prefetch is a nice-to-have only.
                }
            }
        }
    }

    /**
     * Warms Coil's memory cache for [artworkUrl] so that when this track
     * actually starts playing, [coil.compose.AsyncImage] can render it
     * immediately from cache rather than needing a fresh network fetch +
     * decode — this is what removes any residual flash/delay beyond just
     * picking the right URL up front.
     */
    private fun preloadArtworkBitmap(artworkUrl: String) {
        val loader = coil.Coil.imageLoader(context)
        val request = coil.request.ImageRequest.Builder(context)
            .data(artworkUrl)
            .build()
        loader.enqueue(request)
    }

    /** Warms Coil's cache for the previous and next queue items' current artwork (see [playIndex]). */
    private fun preloadNeighborArtwork(index: Int) {
        listOf(index - 1, index + 1).forEach { neighborIndex ->
            queue.getOrNull(neighborIndex)?.artworkUri?.let { preloadArtworkBitmap(it) }
        }
    }

    /**
     * YouTube-style autoplay/radio (sections 13, 22): when the currently
     * playing track is the last one in the queue and autoplay is enabled,
     * fetch related tracks and append them so playback continues instead
     * of just stopping. Only triggers for the LAST queue item, not every
     * track, so it doesn't fight with a user who deliberately queued a
     * short, finite list and wants it to end. Deduplicates against
     * everything already in the queue so repeats don't pile up across
     * multiple autoplay extensions in the same session.
     */
    private fun maybeExtendQueueWithRecommendations(justStarted: PlayableItem.YoutubeTrack) {
        scope.launch {
            if (!settingsRepository.autoplayEnabled.first()) return@launch
            val indexOfItem = queue.indexOfFirst { it.id == justStarted.id && it.source == justStarted.source }
            if (indexOfItem != queue.lastIndex) return@launch // not the last item; nothing to extend yet

            try {
                val related = newPipePlaybackProvider.getRelatedTracks(justStarted.id)
                val existingIds = queue.map { it.id }.toSet()
                val existingTracks = queue.filterIsInstance<PlayableItem.YoutubeTrack>()
                // Dedupe both against the queue AND within this same batch of
                // candidates — related-tracks results routinely include more
                // than one near-duplicate upload of the same song in a single
                // response, not just across separate autoplay extensions.
                // isSameSong (title containment + duration proximity, not
                // exact title match) is required here — see its doc comment
                // for the real cases (both false negatives and false
                // positives) that led to this specific combination.
                val accepted = mutableListOf<Pair<String, Long>>().apply {
                    addAll(existingTracks.map { it.title to it.durationMs })
                }
                val candidates = related
                    .filter { it.id !in existingIds }
                    .filter { candidate ->
                        val isDuplicate = accepted.any { (title, durationMs) ->
                            isSameSong(title, durationMs, candidate.title, candidate.durationMs)
                        }
                        if (!isDuplicate) accepted.add(candidate.title to candidate.durationMs)
                        !isDuplicate
                    }

                // Filter out non-music content before adding to the queue.
                // YouTube's own generic "related videos" (what NewPipeExtractor's
                // StreamInfo.relatedItems returns for a watch?v= URL) is not
                // the same as a music-scoped recommendation feed — it can
                // freely mix in anything from the same channel/algorithmic
                // bucket regardless of type (confirmed via real testing:
                // playing "Perfect" by Ed Sheeran returned a real 948-second
                // "Entertainment"-category clip and a 791-second "Education"-
                // category clip alongside genuine songs). Each candidate's
                // real YouTube category (Music/Comedy/Entertainment/etc,
                // from the same full watch-page response used elsewhere in
                // this provider) is checked in parallel and only Music-
                // categorized items are kept — a real signal YouTube itself
                // assigns per video, not a guess based on title/duration
                // heuristics that would be fragile and easy to get wrong.
                //
                // Real, reported problem beyond just Music-category filtering:
                // "Music" category alone doesn't distinguish a normal single
                // song from a mashup/medley or a full-album/"audio jukebox"
                // upload — all three are legitimately "Music" category, but a
                // listener playing a normal song does not want a full album
                // recommended next, and vice versa. classifySongLength (based
                // purely on title keywords, deliberately NOT duration — see
                // its doc comment for why) buckets both the seed track and
                // every candidate into SINGLE/MASHUP/LONG_FORM, and only
                // candidates matching the seed's own bucket are kept — so a
                // normal song only ever gets normal songs recommended, a
                // mashup only gets other mashups, and a full album/jukebox
                // only gets other long-form uploads.
                val seedLengthClass = classifySongLength(justStarted.title)
                // Bounds how many getPlayerInfo() category-checks are in
                // flight at once (see CATEGORY_CHECK_CONCURRENCY's doc
                // comment for the real speed-vs-contention history here) —
                // still checks every candidate, just not all simultaneously.
                val categoryCheckLimiter = Semaphore(CATEGORY_CHECK_CONCURRENCY)
                val filtered = candidates.take(MAX_AUTOPLAY_ADDITIONS * 2).map { candidate ->
                    async {
                        val info = categoryCheckLimiter.withPermit {
                            runCatching { newPipePlaybackProvider.getPlayerInfo(candidate.id) }.getOrNull()
                        }
                        candidate to info?.category
                    }
                }.awaitAll()
                    .filter { (_, category) -> category == null || category.equals("Music", ignoreCase = true) }
                    .map { (candidate, _) -> candidate }
                    .filter { classifySongLength(it.title) == seedLengthClass }

                val toAdd = filtered.take(MAX_AUTOPLAY_ADDITIONS)
                if (toAdd.isEmpty()) return@launch

                queue.addAll(toAdd)
                trimConsumedQueueHistory()
                _state.update { it.copy(queue = queue.toList()) }
            } catch (_: Exception) {
                // Autoplay extension is a nice-to-have; a failure here must
                // never disrupt the track that's already playing (matches
                // the same safety principle used for stream-resolution
                // failures elsewhere in this class).
            }
        }
    }

    /**
     * Returns an updated [PlayableItem.YoutubeTrack] with [betterArtworkUrl]
     * applied, but only if [resolvedFor] is still the item currently
     * playing (avoids clobbering a newer track's artwork with a stale
     * resolve's result) and only if a non-blank URL was actually resolved.
     */
    private fun upgradeArtworkIfCurrent(
        resolvedFor: PlayableItem.YoutubeTrack,
        betterArtworkUrl: String?,
    ): PlayableItem? {
        val current = _state.value.currentItem
        if (betterArtworkUrl.isNullOrBlank() || current?.id != resolvedFor.id || current !is PlayableItem.YoutubeTrack) {
            return current
        }
        val updated = current.copy(artworkUri = betterArtworkUrl)
        if (currentIndex in queue.indices) queue[currentIndex] = updated
        return updated
    }

    /**
     * Keeps the queue from growing without limit during a long unattended
     * autoplay session.
     *
     * Real growth this bounds: autoplay re-fires every time the current track
     * becomes the last queue item and appends up to
     * [MAX_AUTOPLAY_ADDITIONS] more each time, with nothing ever removing
     * anything — so hours of continuous listening pushed the queue into the
     * hundreds or thousands, and every extension also paid an O(n)
     * `queue.toList()` copy to publish it. Only already-played entries well
     * behind the current track are dropped, so the user keeps a generous
     * backward history for Previous and the upcoming queue is never touched.
     * [currentIndex] is shifted by exactly the number removed so it keeps
     * pointing at the same track.
     */
    private fun trimConsumedQueueHistory() {
        if (queue.size <= MAX_QUEUE_ENTRIES) return
        val keepBehind = MAX_PLAYED_HISTORY_IN_QUEUE
        val removable = (currentIndex - keepBehind).coerceAtLeast(0)
        if (removable <= 0) return
        repeat(removable) { queue.removeAt(0) }
        currentIndex -= removable
    }

    private fun startMediaItem(
        item: PlayableItem,
        resolvedStreamUrl: String?,
        resumeAtMs: Long = 0L,
        isErrorRecovery: Boolean = false,
    ) {
        handledEnded = false
        // Any genuinely new playback attempt re-arms the single automatic
        // error re-resolve (see [handlePlayerError]); a recovery restart
        // deliberately does not, so one bad track can't retry forever.
        if (!isErrorRecovery) errorRecoveryAttemptedForItemId = null
        val mediaItem = PlayableItemMediaItemMapper.toMediaItem(item, resolvedStreamUrl)
        controller?.apply {
            if (resumeAtMs > 0L) setMediaItem(mediaItem, resumeAtMs) else setMediaItem(mediaItem)
            prepare()
            play()
        }

        // Two real, separately-reported bugs are fixed by recording the play
        // HERE, sequentially, rather than at the top of [playIndex]:
        //
        // 1. "Couldn't play this song, but it still shows up in my history and
        //    twice in Speed dial." recordPlayed used to fire the instant a
        //    track was tapped, before its stream had been resolved — so a
        //    track YouTube reports as UNPLAYABLE (a deleted or region-blocked
        //    video still listed in an imported playlist) was written into
        //    history despite never playing a single second. Because Speed dial
        //    is built from history and keyed by video id, a song that exists
        //    on YouTube as two different uploads then showed up as two tiles:
        //    one playable, one permanently dead. This function is only ever
        //    reached once a track genuinely starts, and is never reached from
        //    the resolve-failure branch, so an unplayable track can no longer
        //    pollute history at all.
        //
        // 2. "On a fresh install the very first song I play doesn't appear in
        //    History or Speed dial until I play a second song or restart the
        //    app." This was an ordering race. recordPlayed (which writes the
        //    `history` row) and cacheSong (which writes the `songs` metadata
        //    row) used to run as two INDEPENDENT coroutines with no ordering
        //    guarantee. LibraryRepository.flatMapResolve resolves a history
        //    reference against the `songs` table with a one-shot read and
        //    silently drops anything it can't resolve (mapNotNull), and
        //    observeRecentlyPlayed only re-emits when the `history` table
        //    changes — never when `songs` is written. So if the history row
        //    landed first, the UI resolved it to nothing and had no reason to
        //    ever recompute. On a warm install the metadata was usually
        //    already cached from a previous session, which is exactly why this
        //    only reproduced on a fresh install or after clearing data.
        //    Caching the metadata and THEN recording the play, in that order
        //    inside one coroutine, guarantees the first emission can resolve.
        //
        // Skipped for an error-recovery restart so a self-healed expired URL
        // doesn't count as a second play of the same song.
        if (!isErrorRecovery) {
            scope.launch {
                if (item is PlayableItem.YoutubeTrack) libraryRepository.cacheSong(item)
                libraryRepository.recordPlayed(item)
            }
        }

        scope.launch {
            val speed = settingsRepository.playbackSpeed.first()
            if (speed != 1.0f) controller?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
        }
        scope.launch { maybeFadeIn() }
    }

    /** Sets playback speed (section 18), applied immediately to the live player. */
    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
        scope.launch { settingsRepository.setPlaybackSpeed(speed) }
    }

    private suspend fun maybeFadeIn() {
        if (settingsRepository.crossfadeDurationMs.first() <= 0) {
            controller?.volume = 1f
            return
        }
        val c = controller ?: return
        val steps = 12
        val stepDelay = FADE_STEP_MS
        for (i in 0..steps) {
            c.volume = i / steps.toFloat()
            delay(stepDelay)
        }
        c.volume = 1f
    }

    private suspend fun fadeOutBeforeTransition() {
        val fadeMs = settingsRepository.crossfadeDurationMs.first()
        if (fadeMs <= 0) return
        val c = controller ?: return
        val steps = 12
        val stepDelay = (fadeMs / steps).coerceAtLeast(10).toLong()
        for (i in steps downTo 0) {
            c.volume = i / steps.toFloat()
            delay(stepDelay)
        }
    }

    fun play() = controller?.play()

    fun pause() = controller?.pause()

    fun togglePlayPause() {
        val c = controller ?: return
        // If the current item previously failed to resolve (e.g. no
        // internet when the user first tapped it), there is no prepared
        // MediaItem for ExoPlayer to play/pause at all — c.play() would be
        // a silent no-op, which is exactly why tapping the mini-player/
        // full-player Play button did nothing and never even offered the
        // same "couldn't play"/"no internet" feedback the initial tap-to-
        // play gave. Retry the resolve instead, so this button always
        // either plays or gives the same real feedback, never nothing.
        if (_state.value.playbackError != null && currentIndex in queue.indices) {
            playIndex(currentIndex)
            return
        }
        if (c.isPlaying) c.pause() else c.play()
    }

    /**
     * Seeks within the currently playing item. Clamps the target against
     * the player's own LIVE duration (not whatever the UI's seek bar had
     * cached when the user released it) so a seek request computed just
     * before a track transition can never accidentally land at/past the
     * (possibly already-different) current item's real end and trigger an
     * immediate, ambiguous STATE_ENDED right on top of the natural
     * end-of-track path — the deeper root cause behind a real, reported
     * bug ("seeking near the end of a track sometimes restarts/skips
     * unexpectedly"). [ui.player.FullPlayerScreen]'s SeekBar already keeps
     * a safety margin from its own (UI-side) duration snapshot; this is
     * the second, authoritative layer against the live player state.
     */
    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        val liveDuration = c.duration
        val safeTarget = if (liveDuration > 0) {
            positionMs.coerceIn(0L, (liveDuration - END_OF_TRACK_SEEK_MARGIN_MS).coerceAtLeast(0L))
        } else {
            positionMs
        }
        c.seekTo(safeTarget)
        // Optimistically reflect the seek target in state immediately,
        // rather than waiting for the next refreshPositionAndDuration()
        // (from onEvents, or the next 500ms ticker iteration). MediaController.
        // seekTo() is asynchronous — it does not synchronously update
        // c.currentPosition — so without this, there is a real window
        // (confirmed via real, reported repeated-tapping on the seek bar)
        // where _state.positionMs still holds whatever was last polled up
        // to ~500ms ago. FullPlayerScreen's SeekBar stops treating a tap
        // as "dragging" (and so falls back to state.positionMs for what it
        // renders) the instant this function is called, so any gap here
        // was directly visible as the bar briefly snapping backward to a
        // stale position before catching up to where the user actually
        // tapped — worse with rapid successive taps, since each one
        // reopened the same window. This does not create a genuine mismatch:
        // the very next real onEvents callback (which always fires once
        // the seek actually completes) overwrites this with the true
        // value anyway.
        _state.update { it.copy(positionMs = safeTarget) }
    }

    /** Advances to the next queue item, honoring shuffle/repeat (section 21). */
    fun seekToNext() {
        val next = nextIndex() ?: return
        playIndex(next)
    }

    /** Returns to the previous queue item (section 21). */
    fun seekToPrevious() {
        val prev = previousIndex() ?: return
        playIndex(prev)
    }

    private fun nextIndex(): Int? {
        if (queue.isEmpty()) return null
        val state = _state.value
        return when {
            state.repeatMode == RepeatMode.ONE -> currentIndex
            state.shuffleEnabled -> queue.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
            currentIndex + 1 <= queue.lastIndex -> currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    private fun previousIndex(): Int? {
        if (queue.isEmpty()) return null
        val state = _state.value
        return when {
            state.shuffleEnabled -> queue.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
            currentIndex - 1 >= 0 -> currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> queue.lastIndex
            else -> null
        }
    }

    /**
     * Whether [seekToNext] would actually do anything right now — used by
     * [com.whiplash.music.playback.service.QueueAwareForwardingPlayer] to
     * advertise real Next availability to the system (notification/lock
     * screen/Bluetooth/OEM surfaces), since the underlying ExoPlayer's own
     * single-item timeline can never express this (see that class's doc).
     */
    fun hasNext(): Boolean = nextIndex() != null

    /**
     * The live ExoPlayer's audio session id (adapted from BitChord's system
     * equalizer integration) — the same id [android.media.audiofx.Equalizer]
     * and every other system/third-party equalizer app expects when told
     * which app's audio to affect via
     * [android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL].
     * [androidx.media3.common.C.AUDIO_SESSION_ID_UNSET] before the controller
     * has connected or while nothing has ever been prepared — callers should
     * treat that as "no equalizer session yet" rather than a real id.
     */
    fun audioSessionId(): Int = controller?.audioSessionId ?: androidx.media3.common.C.AUDIO_SESSION_ID_UNSET

    /** Whether [seekToPrevious] would actually do anything right now (see [hasNext]). */
    fun hasPrevious(): Boolean = previousIndex() != null

    fun setShuffleEnabled(enabled: Boolean) {
        _state.update { it.copy(shuffleEnabled = enabled) }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    /**
     * Sets or clears the sleep timer (section 60). [SleepTimerMode.Duration]
     * counts down and pauses playback (gracefully — not an abrupt cut, see
     * [fadeOutAndPauseForSleepTimer]) when it reaches zero. The two
     * "end of" modes don't run a countdown at all; they're checked directly
     * from the natural end-of-track path in [updatePositionTicker] instead,
     * since they mean "the next natural stopping point" rather than a fixed
     * time.
     */
    fun setSleepTimer(mode: SleepTimerMode?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (mode == null) {
            _state.update { it.copy(sleepTimer = null, sleepTimerRemainingMs = null) }
            return
        }

        _state.update {
            it.copy(
                sleepTimer = mode,
                sleepTimerRemainingMs = (mode as? SleepTimerMode.Duration)?.totalMs,
            )
        }

        if (mode is SleepTimerMode.Duration) {
            sleepTimerJob = scope.launch {
                var remaining = mode.totalMs
                while (remaining > 0) {
                    delay(1000)
                    remaining -= 1000
                    _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0)) }
                }
                fadeOutAndPauseForSleepTimer()
                _state.update { it.copy(sleepTimer = null, sleepTimerRemainingMs = null) }
            }
        }
        // EndOfSong / EndOfQueue: no job to run here — handled reactively
        // in updatePositionTicker when a track actually ends.
    }

    /** A graceful fade-to-silence rather than an abrupt stop, so the sleep timer doesn't jolt the listener awake. */
    private suspend fun fadeOutAndPauseForSleepTimer() {
        val c = controller ?: return
        val steps = 20
        for (i in steps downTo 0) {
            c.volume = i / steps.toFloat()
            delay(80)
        }
        c.pause()
        c.volume = 1f
    }

    private fun refreshPositionAndDuration() {
        val c = controller ?: return
        _state.update { current ->
            // Guard against a listener callback (onEvents/onMediaMetadataChanged
            // fire on essentially every player change, including the pause()
            // call at the top of playIndex()) reporting the OLD MediaItem's
            // still-valid position/duration AFTER we've already reset them to
            // 0 for the track that's about to play. Without this check, the
            // reset in playIndex() could be immediately overwritten with
            // stale values from the outgoing track before prepare() for the
            // new one ever runs, which is exactly what made the seek bar
            // appear to "wait" before resetting instead of resetting
            // instantly when the track changed.
            val controllerMediaId = c.currentMediaItem?.mediaId
            val expectedMediaId = current.currentItem?.let { PlayableItemMediaItemMapper.mediaIdOf(it) }
            if (controllerMediaId != null && expectedMediaId != null && controllerMediaId != expectedMediaId) {
                return@update current
            }
            val newPos = c.currentPosition.coerceAtLeast(0)
            // Real, on-device-confirmed ExoPlayer/emulator quirk (isolated
            // via direct testing with autoplay fully disabled, on multiple
            // different fresh/uncached tracks — NOT specific to this app's
            // autoplay/queue-extension code, it happens regardless): during
            // early playback of a track, c.currentPosition occasionally
            // reports one lower reading before immediately continuing to
            // advance normally on the very next poll. A single stale sample
            // is invisible if skipped for one cycle; suppressing MORE than
            // one cycle in a row is what caused the real, reported seek-bar
            // freeze from an earlier (reverted) attempt at this same fix —
            // so this deliberately allows at most ONE suppressed sample per
            // track (tracked via backwardJumpSuppressedForTrack, reset below
            // on every real track change) and always trusts the player again
            // after that, regardless of direction.
            val safePos = if (
                c.isPlaying &&
                newPos < current.positionMs &&
                controllerMediaId != null &&
                backwardJumpSuppressedForTrack != controllerMediaId
            ) {
                backwardJumpSuppressedForTrack = controllerMediaId
                current.positionMs
            } else {
                newPos
            }
            current.copy(
                positionMs = safePos,
                durationMs = c.duration.coerceAtLeast(0),
                isPlaying = c.isPlaying,
            )
        }
    }

    /**
     * Position updates from Player.Listener alone only fire on discrete
     * events (play/pause/seek/track change), which is not enough for a
     * smoothly advancing progress bar. Poll every 500ms only while actually
     * playing (section 64: avoid unnecessary work) and stop immediately on
     * pause/stop. Also detects natural end-of-track to auto-advance the
     * queue (section 13: autoplay) once ExoPlayer reports STATE_ENDED for a
     * single-MediaItem player (we manage the queue ourselves rather than
     * handing Media3 the whole playlist, since YouTube items need an async
     * resolve Media3's playlist APIs can't express).
     */
    /**
     * Called once, directly and immediately, when ExoPlayer reaches
     * STATE_ENDED (see the [Player.Listener.onPlaybackStateChanged]
     * override for why this must not depend on the polling ticker). Guards
     * against double-handling via [handledEndedForItem], since both this
     * direct callback and a stale/racing ticker iteration could otherwise
     * observe the same STATE_ENDED and both try to advance the queue.
     */
    private fun handleTrackEnded() {
        if (handledEnded) return
        handledEnded = true

        val c = controller ?: return
        val timer = _state.value.sleepTimer
        val atLastQueueItem = nextIndex() == null
        when {
            timer is SleepTimerMode.EndOfSong -> {
                setSleepTimer(null)
                c.pause()
            }
            timer is SleepTimerMode.EndOfQueue && atLastQueueItem -> {
                setSleepTimer(null)
                c.pause()
            }
            else -> seekToNext()
        }
    }

    private fun updatePositionTicker(isPlaying: Boolean) {
        positionTickerJob?.cancel()
        if (!isPlaying) return
        positionTickerJob = scope.launch {
            var prefetchTriggered = false
            var fadeOutTriggered = false
            while (true) {
                refreshPositionAndDuration()
                val c = controller
                if (c != null && c.playbackState == Player.STATE_ENDED) {
                    // Safety net only: the direct onPlaybackStateChanged
                    // callback (see handleTrackEnded) is the primary path
                    // and fires immediately/reliably without this loop's
                    // 500ms polling delay. handleTrackEnded is idempotent
                    // (guarded by handledEndedForItem), so calling it here
                    // too is harmless if it already ran.
                    handleTrackEnded()
                    return@launch
                }
                val state = _state.value
                val remainingMs = state.durationMs - state.positionMs
                if (!prefetchTriggered && state.durationMs > 0 && remainingMs in 0..PREFETCH_LEAD_MS) {
                    prefetchTriggered = true
                    // Safety-net re-trigger near the end of the track, in case
                    // the queue changed after playback started (reorder/add)
                    // and the neighbor prefetch done at track-start in
                    // playIndex() is now stale. The normal case — where
                    // nothing changed — is a harmless no-op here since
                    // prefetched/artworkPreloadCache already have the right
                    // entries from playIndex().
                    prefetchNeighborStreamsAndArtwork(state.currentIndex)
                }
                val fadeMs = settingsRepository.crossfadeDurationMs.first()
                if (!fadeOutTriggered && fadeMs > 0 && state.durationMs > 0 && remainingMs in 0..fadeMs.toLong()) {
                    fadeOutTriggered = true
                    launch { fadeOutBeforeTransition() }
                }
                delay(500)
            }
        }
    }

    private companion object {
        /**
         * [PlaybackException] error codes that mean "the network/stream was
         * the problem" rather than "this media is broken", used by
         * [surfacePlayerError] to pick a message the user can actually act
         * on (and to set [PlaybackError.isNetworkFailure], which the UI uses
         * to offer a retry).
         */
        val NETWORK_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )


        /** Caps how many related tracks autoplay appends at once, to avoid an unbounded queue (section 22: "do not unexpectedly add enormous queues"). */
        const val MAX_AUTOPLAY_ADDITIONS = 10

        /**
         * Queue length past which [trimConsumedQueueHistory] starts dropping
         * already-played entries. Autoplay appends indefinitely, so without a
         * ceiling a long session grows the queue (and the O(n) state copy
         * published on every extension) without limit.
         */
        const val MAX_QUEUE_ENTRIES = 300

        /** How many already-played tracks stay reachable behind the current one when trimming. */
        const val MAX_PLAYED_HISTORY_IN_QUEUE = 50

        /**
         * Caps how many getPlayerInfo() category-checks run at once during
         * autoplay's queue extension. Every candidate is still checked —
         * this only bounds how many resolves are in flight simultaneously.
         *
         * Real, reported problem this value addresses: the first autoplay
         * extension of a session (Quick Picks/Search/History/Speed dial —
         * i.e. whenever playback starts from a single track with no
         * pre-existing queue) took roughly 10 seconds before the queue
         * actually grew, because up to MAX_AUTOPLAY_ADDITIONS * 2 (20)
         * candidates were being checked in small batches of 4 at a time —
         * 5 sequential batches, each a real network round-trip.
         *
         * This was originally set low (4) on the assumption it was also
         * fixing a separate, real on-device-confirmed seek-bar position
         * glitch — but later, more careful isolation testing in the same
         * session (autoplay fully disabled, fresh uncached tracks) proved
         * that glitch happens independently of this concurrency value
         * entirely; it's now handled directly by the one-suppression guard
         * in refreshPositionAndDuration instead. With that separately
         * covered, there's no remaining reason to keep this artificially
         * low — raised to let candidates resolve in far fewer batches
         * without changing what's checked or how results are filtered.
         */
        const val CATEGORY_CHECK_CONCURRENCY = 10

        /** How far before track-end to start resolving the next stream (section 18: gapless). */
        const val PREFETCH_LEAD_MS = 8_000L

        /** See [seekTo]'s doc — never let a manual seek land within this margin of the live player's real duration. */
        const val END_OF_TRACK_SEEK_MARGIN_MS = 1000L

        /** Volume-ramp step interval for fade in/out (section 18). */
        const val FADE_STEP_MS = 40L

        /**
         * Hard ceiling on a single playbackManager.resolveStream() attempt
         * (which itself may try multiple providers in sequence, see
         * PlaybackManager's fallback loop). Generous enough to comfortably
         * cover a real multi-request StreamInfo.getInfo() resolve under
         * normal conditions, but bounded so a hung/degraded upstream always
         * surfaces a real error instead of leaving the UI buffering
         * indefinitely (see the real bug this fixes, documented at the
         * withTimeout call site in playIndex()).
         */
        const val RESOLVE_STREAM_TIMEOUT_MS = 30_000L
    }
}

/**
 * Real, reported problem: autoplay's existing dedup only checked exact
 * YouTube video ID, which never catches the same song uploaded multiple
 * times under different video IDs — e.g. the same track re-uploaded by a
 * different channel, or an "Official Video" and a separate "Lyric Video"/
 * "Audio" upload of the identical song. Related-tracks results routinely
 * include several of these near-duplicates for the same underlying song,
 * so the queue ended up with 2-3 entries that were all, in practice, the
 * same song to a listener even though each had a technically-unique video
 * ID.
 *
 * Title matching alone is NOT enough — confirmed via real on-device
 * testing across multiple iterations:
 *  - Exact match after stripping upload-type noise ("(Official Video)",
 *    "(Lyrics)", etc.) missed real duplicates where the seed track's title
 *    is a clean song name ("Shape of You", from a search result) but a
 *    related-tracks candidate has the artist baked into the title itself
 *    ("Ed Sheeran - Shape of You (Lyrics)") — these don't normalize to the
 *    same string.
 *  - Loosening to substring containment (does either normalized title
 *    contain the other) fixed that, but then produced real false
 *    positives: "Shape of You" incorrectly matched "Ed Sheeran & Diljit
 *    Dosanjh - Shape of You x Naina (Live in Birmingham 2024)" — a genuine
 *    different mashup/live-medley track, not a duplicate — and "Starboy"
 *    incorrectly matched "Starboy x I Feel It Coming" for the same reason.
 *    Title text alone can't reliably tell "same song, different upload"
 *    apart from "different song that happens to share a title word."
 *
 * [isSameSong] combines both signals: title containment AND duration
 * proximity (within [DURATION_MATCH_TOLERANCE_MS]). Two uploads of the
 * genuinely same song have near-identical runtimes; a mashup/live/extended
 * version that happens to share title words does not — a real, reliable,
 * independent signal already available on every [PlayableItem] without
 * any extra network cost. (dedup logic worked through by -SA,
 * github.com/shahidthisside)
 */
internal fun normalizeSongTitle(title: String): String {
    // Bracketed/parenthesized "upload type" tags - e.g. "(Official Video)",
    // "(Lyrics)", "[CHOREOGRAPHY]". Broadened from only-matching-if-a-
    // specific-keyword-is-inside (which missed "[CHOREOGRAPHY]" - a real,
    // on-device-confirmed case with no keyword match inside its own
    // brackets) to strip ANY short bracketed/parenthesized tag, since a
    // real song title practically never uses brackets for its own name.
    val bracketTagPattern = Regex("""[\[(][^\])]{1,40}[\])]""")
    // Trailing "upload type" phrases that describe the VIDEO, not the song,
    // and are often NOT bracketed at all - e.g. "... Special Performance
    // Video", "... Official MV", "... Dance Practice", "... Live
    // Performance". Real, on-device-confirmed gap: two uploads of BTS'
    // "Butter" - one titled "...Special Performance Video" (choreography),
    // the other "...Official MV" - didn't normalize to the same string
    // without this, since neither phrase was inside brackets.
    val trailingVideoTypePattern = Regex(
        """\b(special performance video|dance practice|live performance|performance video|choreography|dance video|behind the scenes|teaser|trailer)\b""",
        RegexOption.IGNORE_CASE,
    )
    val noisePattern = Regex(
        """[\[(].*?(official|lyric|lyrics|audio|video|visualiser|visualizer|mv|hd|hq|4k|remaster(?:ed)?|explicit|clean|radio edit)[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )
    return title
        .lowercase()
        .replace(noisePattern, " ")
        .replace(bracketTagPattern, " ")
        .replace(trailingVideoTypePattern, " ")
        .replace(Regex("""feat\.?|ft\.?"""), " ")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
}

/** See [normalizeSongTitle]'s doc comment for why title containment is gated on duration proximity AND a mashup/medley exclusion. */
internal fun isSameSong(
    titleA: String,
    durationMsA: Long,
    titleB: String,
    durationMsB: Long,
): Boolean {
    val a = normalizeSongTitle(titleA)
    val b = normalizeSongTitle(titleB)
    if (a.isBlank() || b.isBlank()) return false
    val titleMatches = a == b || a.contains(b) || b.contains(a)
    if (!titleMatches) return false
    // Explicit mashup/medley/combo exclusion — confirmed via real on-device
    // testing this is needed: title containment alone matched "Shape of
    // You" against "...Shape of You x Naina (Live in Birmingham 2024)", a
    // genuinely different mashup track, not a duplicate upload. These
    // combo tracks reliably signal themselves with a literal "x"/"vs"
    // joining two song names, or an explicit "mashup"/"medley"/"mix" word
    // — check the ORIGINAL (non-normalized) titles since normalization
    // strips punctuation that " x " and " vs " rely on to read as a
    // separator rather than a word.
    val comboPattern = Regex("""\s+(x|vs\.?)\s+|\b(mashup|medley)\b""", RegexOption.IGNORE_CASE)
    if (comboPattern.containsMatchIn(titleA) != comboPattern.containsMatchIn(titleB)) return false
    // If either duration is unknown (0/missing), fall back to title alone
    // rather than blocking a real duplicate just because one side's
    // duration wasn't populated (confirmed some providers can leave this
    // unset).
    if (durationMsA <= 0 || durationMsB <= 0) return true
    // Tolerance is deliberately generous (not a few seconds) — confirmed
    // via real on-device testing that a legitimate same-song "Official
    // Music Video" upload can run 30+ seconds longer than a "Lyrics"
    // upload of the identical song (extended intro/outro), so a tight
    // tolerance produced a real false negative (missed duplicate). The
    // combo-track exclusion above is what actually rules out mashups/
    // medleys now, so this tolerance only needs to catch the more extreme
    // case of a genuinely different, much longer/shorter track (e.g. a
    // full-album stream, a 10-minute extended remix) slipping through on
    // title containment alone.
    return kotlin.math.abs(durationMsA - durationMsB) <= DURATION_MATCH_TOLERANCE_MS
}

private const val DURATION_MATCH_TOLERANCE_MS = 60_000L

/**
 * Real, reported problem: YouTube's "Music" category alone doesn't
 * distinguish a normal single song from a mashup/medley or a full-album/
 * "audio jukebox" upload — all three are legitimately category "Music",
 * but a listener playing a normal song does not want a full album or a
 * mashup recommended next (and vice versa: someone who searched for and
 * is playing a full album/mashup wants more of that, not random single
 * songs).
 *
 * Deliberately NOT based on duration — a real, reported case (Nusrat
 * Fateh Ali Khan qawwali tracks, and classical/devotional vocal music
 * generally) has genuine SINGLE songs that routinely run 20-30+ minutes,
 * which a duration threshold would misclassify as a full album/long-form
 * upload. Content type is determined purely from explicit title
 * keywords — the actual, reliable signal for "this upload contains
 * multiple songs" or "this is a combination of songs" is that its title
 * says so (creators reliably label these), not how long the audio runs.
 * A track with none of these keywords is treated as SINGLE regardless of
 * its duration, exactly matching a single long qawwali/classical piece.
 */
internal enum class SongLengthClass { SINGLE, MASHUP, LONG_FORM }

private val LONG_FORM_KEYWORDS = Regex(
    """\b(full album|audio jukebox|jukebox|greatest hits|best of|all\s+(\w+\s+)?songs|full movie|non\s*stop|nonstop|top\s*\d+|\d+\s*songs|playlist|compilation|motivational music|background music|study music|workout music|gym music|mix\s*20\d\d|movie\s+songs|hit\s+songs|hits\s*20\d\d|lofi\s+mix|lo-?fi\s+mix|lofi\s+songs|lofi\s+playlist|sin\s+anuncios)\b|\bmix\s*$""",
    RegexOption.IGNORE_CASE,
)

// Real, on-device-confirmed bug this fixes: a genuine ~16-minute,
// multi-artist compilation ("Die With A Smile - Lady Gaga, Bruno Mars
// (Lyrics) ZAYN, Ed Sheeran,... MIX", confirmed on-device at 16:19 total
// duration, several different artists explicitly credited together in
// the title) was classified as SINGLE and recommended alongside normal
// single songs after playing "Blinding Lights." Added `\bmix\s*$` — bare
// "mix" as the trailing word of the title, with nothing after it — which
// is a real, common compilation/multi-artist-mix upload convention. This
// is deliberately narrower than a plain `\bmix\b` anywhere-in-title
// match: a legitimate single track's own title very commonly says
// "(Extended Mix)"/"(Radio Mix)"/"(Club Mix)"/"(Original Mix)" — always
// with a qualifying word directly before "Mix" and wrapped in
// parentheses that close AFTER "Mix" — so those titles end with ")", not
// with the bare word "mix" itself, and are correctly NOT matched by this
// anchored pattern (confirmed: "Faded (Original Mix)" does not match,

// Two more real, on-device-confirmed bugs, found on a second independent
// "Blinding Lights" autoplay run: "Spotify Pop Hits 2025 [emoji] Lady
// Gaga, Bruno Mars, Ed Sheeran, Billie Eilish, Miley Cyrus, Tate McRae
// #1" (uploaded by a channel literally named "Sunset Playlist and Sound
// View", confirmed on-device at 123:45 — over two hours) and "Musica Pop
// en Inglés 2026 [emoji] Melhores Musicas Internacionais 2026 [emoji]
// Canciones Pop Sin Anuncios" (confirmed on-device at 74:57). Neither
// contained any pre-existing keyword. Added `hits\s*20\d\d` (mirroring
// the already-existing `mix\s*20\d\d` pattern's own precedent exactly —
// "Hits" alone, unlike "greatest hits"/"hit songs", wasn't covered) and
// `sin\s+anuncios` (a real, distinctive Spanish "ad-free" compilation-
// playlist branding phrase). Deliberately did NOT generalize to "3+
// different artist names listed together" as a standalone signal — that
// exact heuristic was already tried and reverted for the pipe-separator
// case documented below (real single songs legitimately credit multiple
// artists in their own title), so it carries the same false-positive
// risk here and isn't safe to reintroduce just because these two
// examples happen to list several names.
// only a title that is actually itself the literal last word "Mix" with
// no closing parenthesis does).

// Real, reported/on-device-confirmed gap found via manual autoplay
// testing: a genuine ~15-minute lofi compilation ("MIDNIGHT VIBES ||
// बैरण song #tredingsong #viralsongs", confirmed on-device at 15:35
// total duration) was classified as SINGLE and recommended right
// alongside normal single songs after playing "Barsaat." Its title
// contains no explicit compilation keyword at all — no "jukebox",
// "nonstop", "playlist", or even "lofi mix" (added above for the more
// common explicit case, but this specific title doesn't say it) — just
// a generic "VIBES" branding + hashtag-farming markers (#tredingsong
// #viralsongs), which is NOT a safe keyword to add: "Midnight Vibes" is
// also a real, legitimate single-song title in its own right (e.g. an
// actual song by that exact name), so matching on "vibes" alone would
// create new false positives on genuine single songs. Deliberately did
// NOT add a duration threshold either — explicitly rejected per this
// function's own design (see the qawwali/Nusrat Fateh Ali Khan doc
// above): many genuine SINGLE songs legitimately run 15-30+ minutes, so
// a duration cutoff would misclassify those as long-form too, exactly
// the false positive this classifier exists to avoid. This specific
// title pattern (generic mood branding + viral hashtags, no explicit
// compilation keyword) is a known, currently-unaddressed gap — content-
// type classification here is deliberately keyword-only, and no reliably
// safe keyword exists for this exact case without over-matching real
// single-song titles.
//
// Same category of gap, confirmed again on a third independent
// "Blinding Lights" autoplay run: "Japanese City Pop 80s – Tokyo Friday
// Midnight [emoji] | Bayside Highway & Neon Memories" (uploader
// "Nightdrive Tokyo", confirmed on-device at 70:48). Purely aesthetic/
// mood branding ("80s", "Tokyo Friday Midnight", "Bayside Highway &
// Neon Memories") with no explicit compilation keyword — same accepted
// tradeoff as MIDNIGHT VIBES above: a themed decade/city/mood name is
// not a safe keyword to add on its own, since a genuine single song
// could legitimately use similar aesthetic branding in its own title.

/**
 * Real, on-device-confirmed false positive this AVOIDS: a naive "3+ pipe-
 * separated segments" heuristic was tried here to catch generic
 * compilation-style titles ("Gym Motivational Music | Motivational Video |
 * Strong | Bollywood | English | Boost Up") that don't contain any of
 * LONG_FORM_KEYWORDS' explicit phrases. That heuristic was REMOVED after
 * testing showed it broke a real, common Bollywood title convention —
 * crediting cast/singer/lyricist with pipe separators in a genuine SINGLE
 * song's own title ("JAWAN: Chaleya (Hindi) | Shah Rukh Khan | Nayanthara
 * | Atlee | Anirudh | Arijit S, Shilpa R | Kumaar" is one real song, not a
 * compilation). Perfect coverage of every possible compilation-title
 * phrasing isn't achievable via keywords alone; avoiding a false positive
 * on a real, common single-song title convention matters more than
 * catching every generic-background-music-style compilation upload.
 */

// Real, reported/on-device-confirmed bug this fixes: a title using the
// actual Unicode multiplication sign "×" (U+00D7) as its mashup
// separator ("Jo Tum Mere Ho × Jhol × Samjho Na × Sahiba × Pal Pal ×
// Bairan × Majboor × Finding Her |Lofi by Nishu") was NOT caught — the
// regex previously only matched the plain ASCII letter "x", and
// RegexOption.IGNORE_CASE only affects letter casing, not Unicode
// look-alike characters, so this genuine 8-song mashup was classified as
// SINGLE and recommended right alongside normal individual songs after
// playing "Barsaat." "×" is a common, real-world separator creators use
// for exactly this kind of mashup/medley title (visually similar to "x"
// but a distinct code point), so it needs its own explicit alternative
// in the pattern rather than relying on case-insensitivity to cover it.
private val MASHUP_KEYWORDS = Regex(
    """\b(mashup|medley|megamix|mega\s*mix)\b|\s+(x|vs\.?|×)\s+""",
    RegexOption.IGNORE_CASE,
)

internal fun classifySongLength(title: String): SongLengthClass {
    if (LONG_FORM_KEYWORDS.containsMatchIn(title)) return SongLengthClass.LONG_FORM
    if (MASHUP_KEYWORDS.containsMatchIn(title)) return SongLengthClass.MASHUP
    return SongLengthClass.SINGLE
}
