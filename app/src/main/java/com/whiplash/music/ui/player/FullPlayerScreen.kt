package com.whiplash.music.ui.player

import kotlinx.coroutines.launch
import com.whiplash.music.ui.theme.glassSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.PlaybackState
import com.whiplash.music.playback.controller.RepeatMode
import com.whiplash.music.playback.controller.SleepTimerMode
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassPrimaryPlayButton
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Full player (section 48): large artwork, title/artist/album, seek,
 * transport controls, shuffle/repeat, favorite, queue. Lyrics/sleep timer
 * are added once those systems exist (section 73: no fake buttons).
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FullPlayerScreen(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onPlayQueueIndex: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onRestoreToQueue: (Int, com.whiplash.music.domain.model.PlayableItem, Boolean) -> Unit = { _, _, _ -> },
    onShuffleUpcoming: () -> Unit = {},
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    autoplayEnabled: Boolean = true,
    onToggleAutoplay: (Boolean) -> Unit = {},
    onSetSleepTimer: (SleepTimerMode?) -> Unit = {},
    lyrics: com.whiplash.music.domain.model.LyricsResult? = null,
    lyricOffsetMs: Long = 0L,
    lyricsSourceName: String? = null,
    onAdjustLyricOffset: (Long) -> Unit = {},
    onResetLyricOffset: () -> Unit = {},
    onLyricsSheetOpened: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onPreviewPlaybackSpeed: (Float) -> Unit = {},
    showStatsForNerds: Boolean = false,
    artworkColorsEnabled: Boolean = true,
    showLyricStrip: Boolean = true,
    /** Changes each time the swipe-up gesture asks for the lyrics. */
    openLyricsRequest: Int = 0,
    onSetLyricStrip: (Boolean) -> Unit = {},
    lyricsBlurUnfocused: Boolean = false,
    heroArtwork: Boolean = false,
    playlists: List<com.whiplash.music.domain.model.Playlist> = emptyList(),
    onAddToPlaylist: (playlistId: Long, playlistName: String) -> Unit = { _, _ -> },
    onCreatePlaylistAndAdd: (name: String) -> Unit = {},
    // Offline download (Library > Downloads, YouTube-Music-style) for
    // whatever is currently playing.
    isCurrentDownloaded: Boolean = false,
    onDownloadCurrent: (() -> Unit)? = null,
    onRemoveDownloadCurrent: (() -> Unit)? = null,
) {
    val item = state.currentItem
    val artworkScale = rememberPausedArtworkScale(state)
    // 2.3: swipe the artwork sideways to skip. State sits outside the
    // track-change AnimatedContent so the spring back isn't cut off.
    val artworkSwipe = rememberArtworkSwipeState()
    val skipHaptic = rememberSkipHaptic()
    val reducedMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()
    // Artwork-derived colours (2.1/2.2). Read from a separate tiny decode
    // of the cover, so the displayed artwork itself is untouched.
    val artworkPalette by rememberArtworkPalette(item?.artworkUri, enabled = artworkColorsEnabled)
    val playerColors = animatedPlayerColors(artworkPalette)
        .copy(fromArtwork = artworkColorsEnabled && item?.artworkUri != null)
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    var isSleepTimerSheetOpen by remember { mutableStateOf(false) }
    var isLyricsSheetOpen by remember { mutableStateOf(false) }
    // Swipe up on the player (gesture lives on the player's root, in MainActivity).
    // Keyed on the counter, so opening the player again doesn't reopen the lyrics.
    var handledLyricsRequest by remember { mutableStateOf(openLyricsRequest) }
    LaunchedEffect(openLyricsRequest) {
        if (openLyricsRequest != handledLyricsRequest) {
            handledLyricsRequest = openLyricsRequest
            if (item != null) {
                isLyricsSheetOpen = true
                onLyricsSheetOpened()
            }
        }
    }
    var isSpeedSheetOpen by remember { mutableStateOf(false) }
    var isAddToPlaylistSheetOpen by remember { mutableStateOf(false) }
    var isCreatePlaylistDialogOpen by remember { mutableStateOf(false) }
    var isOverflowSheetOpen by remember { mutableStateOf(false) }
    var isRemoveDownloadConfirmOpen by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Section 57: subtle haptic feedback for meaningful interactions
    // (play/pause, favorite, queue reorder, slider, toggles) — not on
    // every animation. TickTock is the lightest built-in feedback type
    // and matches these apps' restrained convention better than the
    // stronger LongPress feedback (already reserved for actual long-press
    // actions elsewhere in the app).
    val hapticTogglePlayPause: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onTogglePlayPause()
    }
    val hapticToggleFavorite: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleFavorite()
    }
    val hapticToggleShuffle: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleShuffle()
        // state.shuffleEnabled still holds the pre-toggle value here since
        // onToggleShuffle() only dispatches the change (the real, new value
        // arrives on the next recomposition via the StateFlow) - negate it
        // to announce the value the user is actually toggling TO, matching
        // the shuffle button's own content-description convention above.
        com.whiplash.music.ui.common.ToastController.show(
            if (!state.shuffleEnabled) "Shuffle on" else "Shuffle off"
        )
    }
    val hapticCycleRepeat: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCycleRepeat()
        val next = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        com.whiplash.music.ui.common.ToastController.show(
            when (next) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> "Repeat all"
                RepeatMode.ONE -> "Repeat one"
            }
        )
    }

    // 2.8 hero mode: the in-flow artwork slot takes all spare height, and
    // the hero behind it is sized to reach just past the slot's bottom, so
    // the controls stay at the bottom and the title sits on the faded edge.
    var rootTopPx by remember { mutableStateOf(0f) }
    // 2.9: one shared value drives the whole collapse when the lyrics or
    // queue sheet opens: the big artwork block fades back and a compact
    // header (thumb + title) settles in under the top bar, in the strip
    // the sheet leaves visible. 0 = normal player, 1 = collapsed.
    var topBarBottomPx by remember { mutableStateOf(0f) }
    var heroSlotBottomPx by remember { mutableStateOf(0f) }
    val collapse by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLyricsSheetOpen || isQueueSheetOpen) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else COLLAPSE_ANIM_MS),
        label = "playerCollapse",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Liquid Glass: the player's own backdrop (cover-lit mesh + hero art) is
    // recorded so its buttons can be real glass refracting it.
    val playerBackdrop = com.whiplash.music.ui.theme.rememberGlassBackdrop()
    val playerGlass = WhiplashColors.isGlass
    androidx.compose.runtime.CompositionLocalProvider(
        com.whiplash.music.ui.theme.LocalGlassBackdrop provides if (playerGlass) playerBackdrop else null,
    ) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootTopPx = it.positionInRoot().y },
    ) {
    // Capped at 1.15x the width so a square cover never loses more than a
    // sliver of each side on tall phones; below the cap the fade simply
    // ends a little above the title.
    // The hero reaches down to just past the title (which, with the seek
    // bar and controls, stays grouped at the bottom), so its faded edge
    // always sits right behind the title with no empty band between.
    val heroHeight = if (heroSlotBottomPx > 0f) {
        minOf(
            with(density) { (heroSlotBottomPx - rootTopPx).coerceAtLeast(0f).toDp() } + HERO_TITLE_OVERLAP,
            maxWidth * HERO_MAX_ASPECT,
        )
    } else {
        minOf(maxWidth * HERO_MAX_ASPECT, maxHeight * 0.6f)
    }
    val wideWindow = maxWidth >= TWO_PANE_MIN_WIDTH
    val twoPane = wideWindow && maxWidth > maxHeight
    val useHero = heroArtwork && !twoPane
    Box(
        Modifier
            .fillMaxSize()
            .then(if (playerGlass) Modifier.glassSource(playerBackdrop) else Modifier),
    ) {
    PlayerMeshBackdrop(
        colors = playerColors,
        animate = state.isPlaying && !reducedMotion,
    )
    if (useHero) {
        HeroArtwork(
            artworkUri = item?.artworkUri,
            translationPx = { artworkSwipe.translationPx },
            animate = !reducedMotion,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer { alpha = 1f - collapse * (1f - COLLAPSED_BODY_ALPHA) },
        )
    }
    }

    val TopBar: @Composable () -> Unit = {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { topBarBottomPx = it.positionInRoot().y + it.size.height },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(contentDescription = "Collapse player", onClick = onCollapse) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = WhiplashColors.textPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
            GlassIconButton(
                contentDescription = "Lyrics",
                onClick = {
                    isLyricsSheetOpen = true
                    onLyricsSheetOpened()
                },
            ) {
                Icon(Icons.Filled.Lyrics, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            GlassIconButton(
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = hapticToggleFavorite,
            ) {
                // Section 52's own example: favorite -> small scale
                // transition -> new state. A brief overshoot-then-
                // settle scale pulse on toggle, rather than an instant
                // icon swap, makes the state change feel deliberate.
                val favoriteScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isFavorite) 1.15f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                    ),
                    label = "favoriteScale",
                )
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) WhiplashColors.accent else WhiplashColors.textPrimary,
                    modifier = Modifier.scale(favoriteScale),
                )
            }
            GlassIconButton(contentDescription = "Queue", onClick = { isQueueSheetOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            GlassIconButton(
                contentDescription = "More",
                onClick = { isOverflowSheetOpen = true },
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }
    }

    }
    // Shared by the portrait and two-pane layouts.
    val ArtworkCard: @Composable (PlayableItem?, Modifier) -> Unit = { currentTrack, sizeModifier ->
    Box(
        modifier = sizeModifier
            .artworkSwipeToSkip(
                state = artworkSwipe,
                enabled = currentTrack != null,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipFeedback = skipHaptic,
            )
            // Scale in the draw layer only: layout (title,
            // seek bar, controls) stays exactly where it is,
            // so the shrink never shifts anything else.
            .graphicsLayer {
                scaleX = artworkScale
                scaleY = artworkScale
            }
            .clip(RoundedCornerShape(WhiplashRadius.extraLarge))
            .background(WhiplashColors.surfaceElevated),
    ) {
        if (currentTrack?.artworkUri != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentTrack.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ArtworkSwipeHint(artworkSwipe)
    }
    }
    val TitleBlock: @Composable (PlayableItem?) -> Unit = { currentTrack ->
    // 2.4: long titles scroll instead of being cut off. The
    // artist line starts a beat later than the title so the two
    // never move in lockstep. The whole block already crossfades
    // on track change (the AnimatedContent above). Under reduced
    // motion the lines stay static and ellipsize as before.
    MarqueeText(
        text = currentTrack?.title ?: "Nothing playing",
        style = MaterialTheme.typography.headlineMedium,
        color = WhiplashColors.textPrimary,
        initialDelayMillis = TITLE_MARQUEE_DELAY_MS,
        animate = !reducedMotion,
    )
    MarqueeText(
        text = currentTrack?.artist ?: "",
        style = MaterialTheme.typography.bodyLarge,
        color = WhiplashColors.textSecondary,
        initialDelayMillis = ARTIST_MARQUEE_DELAY_MS,
        animate = !reducedMotion,
    )
    }
    val PlayerControls: @Composable (Boolean) -> Unit = { lyricStripFits ->
    if (showStatsForNerds) {
        StatsForNerdsLine(info = state.audioInfo)
    }

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceLg))

    if (showLyricStrip && lyricStripFits) {
        CurrentLyricStrip(
            result = lyrics,
            positionMs = state.positionMs,
            offsetMs = lyricOffsetMs,
            onClick = {
                isLyricsSheetOpen = true
                onLyricsSheetOpened()
            },
        )
    }

    SeekBar(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        onSeekTo = onSeekTo,
    )

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Toasts raised inside the player sit just above these controls.
            .onGloballyPositioned {
                com.whiplash.music.ui.theme.ToastAnchor.fullPlayerControlsTopPx = it.positionInRoot().y
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            contentDescription = if (state.shuffleEnabled) "Shuffle: ON" else "Shuffle: OFF",
            onClick = hapticToggleShuffle,
        ) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = null,
                tint = if (state.shuffleEnabled) playerColors.accent else WhiplashColors.textSecondary,
            )
        }
        GlassIconButton(contentDescription = "Previous", onClick = onPrevious, size = 56.dp) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = WhiplashColors.textPrimary)
        }
        GlassPrimaryPlayButton(
            isPlaying = state.isPlaying,
            onClick = hapticTogglePlayPause,
            size = 84.dp,
            containerColor = playerColors.accent,
            contentColor = playerColors.onAccent,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                // On clear glass the icon is the page's text colour, not the
                // ink meant for a solid accent fill.
                tint = if (WhiplashColors.isGlass) WhiplashColors.textPrimary else playerColors.onAccent,
                modifier = Modifier.size(38.dp),
            )
        }
        GlassIconButton(contentDescription = "Next", onClick = onNext, size = 56.dp) {
            Icon(Icons.Filled.SkipNext, contentDescription = null, tint = WhiplashColors.textPrimary)
        }
        GlassIconButton(
            contentDescription = "Repeat mode: ${state.repeatMode.name}",
            onClick = hapticCycleRepeat,
        ) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = null,
                tint = if (state.repeatMode != RepeatMode.OFF) playerColors.accent else WhiplashColors.textSecondary,
            )
        }
    }
    }
    if (!twoPane) {
    Column(
        modifier = Modifier
            // Tablets / unfolded: keep the column at phone-like width
            // (widthIn before fillMaxSize, or the fill wins).
            .align(Alignment.TopCenter)
            .widthIn(max = PLAYER_MAX_CONTENT_WIDTH)
            .fillMaxSize()
            // The screen is drawn edge-to-edge (enableEdgeToEdge in
            // MainActivity), so without this the collapse button and
            // artwork draw underneath the system status bar / notification
            // shade swipe area. Only the top inset is needed here — the
            // bottom is left alone since this screen has no bottom nav.
            .windowInsetsPadding(WindowInsets.statusBars)
            // The layout was designed (and every screen tuned) with one extra
            // status-bar height of space above the top bar: the player used
            // to sit inside the Scaffold's inset padding as well as applying
            // its own. Now that the backdrop runs edge to edge, keep that
            // same gap explicitly so every element stays where it was.
            // Full-bleed mode drops that gap: the top buttons sit right under
            // the status bar, over the artwork, giving the cover more room.
            .padding(top = if (useHero) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            // The player now draws under the navigation bar too, so keep
            // the transport controls clear of the gesture handle.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(GlassTokens.spaceLg),
    ) {
        TopBar()
        // Wide portrait windows (tablets) have spare height once the width is
        // capped; centre the body under the top bar instead of leaving it all
        // below the controls. Phones keep the original top-down layout.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = if (wideWindow) Arrangement.Center else Arrangement.Top,
        ) {
        androidx.compose.animation.AnimatedContent(
            targetState = item,
            contentKey = { it?.let { track -> "${track.source}:${track.id}" } },
            transitionSpec = { trackChangeTransition() },
            label = "nowPlayingContent",
            // The artwork gives up height first: on shorter screens, or once
            // the lyric strip appears, it shrinks rather than pushing the
            // transport controls off the bottom.
            modifier = Modifier
                .weight(1f, fill = useHero)
                .graphicsLayer {
                    alpha = 1f - collapse * (1f - COLLAPSED_BODY_ALPHA)
                    val scale = 1f - collapse * COLLAPSED_BODY_SHRINK
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
        ) { currentTrack ->
            Column(modifier = if (useHero) Modifier.fillMaxHeight() else Modifier) {
                if (useHero) {
                    // The artwork itself is drawn full-bleed behind this
                    // column (HeroArtwork). This slot has exactly the same
                    // size as the normal artwork box below, so the title,
                    // seek bar and controls stay in the same place with the
                    // setting on or off. It keeps swipe-to-skip working; the
                    // title sits over the hero's faded bottom edge.
                    // Takes all the spare height, pushing the title down onto
                    // the seek bar; the hero behind is sized to this slot.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onGloballyPositioned { heroSlotBottomPx = it.positionInRoot().y + it.size.height }
                            .artworkSwipeToSkip(
                                state = artworkSwipe,
                                enabled = currentTrack != null,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onSkipFeedback = skipHaptic,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArtworkSwipeHint(artworkSwipe)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = GlassTokens.spaceXl),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArtworkCard(currentTrack, Modifier.aspectRatio(1f))
                    }
                }

                TitleBlock(currentTrack)
                // Hero mode: this block fills the spare height, so the title
                // stays on the artwork's edge and the seek bar + transport
                // (outside this block) sit at the bottom of the screen.
            }
        }

        PlayerControls(true)
        }
    }
    } else {
    // 2.10: landscape / large screens. Artwork fills the left half at the
    // largest square the height allows; the right half holds the top bar,
    // title and controls, centred vertically and capped in width.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceXl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = item,
                contentKey = { it?.let { track -> "${track.source}:${track.id}" } },
                transitionSpec = { trackChangeTransition() },
                label = "nowPlayingArtworkTwoPane",
                contentAlignment = Alignment.Center,
            ) { currentTrack ->
                ArtworkCard(currentTrack, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
            }
        }
        // weight() hands its child a fixed width, so the cap has to live on
        // an inner Column; the Box centres it in the right half.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = PLAYER_MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            TopBar()
            // Scrolls only if the controls can't fit (very short windows or
            // large font), otherwise centred in the remaining height.
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val controlsHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .heightIn(min = controlsHeight),
                    verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = item,
                        contentKey = { it?.let { track -> "${track.source}:${track.id}" } },
                        transitionSpec = { trackChangeTransition() },
                        label = "nowPlayingTitleTwoPane",
                    ) { currentTrack ->
                        Column { TitleBlock(currentTrack) }
                    }
                    // Phone landscape is too short for the strip as well as
                    // the transport; lyrics stay one tap away in the top bar.
                    PlayerControls(controlsHeight >= TWO_PANE_STRIP_MIN_HEIGHT)
                }
            }
        }
        }
    }
    }
    if (!twoPane && collapse > 0f && item != null) {
        CollapsedPlayerHeader(
            item = item,
            progress = { collapse },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (topBarBottomPx - rootTopPx).coerceAtLeast(0f)
                }
                .padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
        )
    }
    }
    }

    if (isQueueSheetOpen) {
        // Opens at half height (the playing song and the next few), and
        // drags or scrolls up to full. Only the queue does this; the other
        // sheets open fully so every option is visible at once.
        val queueSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val queueSheetScope = androidx.compose.runtime.rememberCoroutineScope()
        GlassSheet(onDismissRequest = { isQueueSheetOpen = false }, sheetState = queueSheetState) {
            QueueContent(
                queue = state.queue,
                currentIndex = state.currentIndex,
                isPlaying = state.isPlaying,
                autoplayIds = state.autoplayIds,
                tint = if (playerColors.fromArtwork) playerColors.accent else null,
                autoplayEnabled = autoplayEnabled,
                onToggleAutoplay = onToggleAutoplay,
                onPlayIndex = { index ->
                    onPlayQueueIndex(index)
                    isQueueSheetOpen = false
                },
                onRemove = onRemoveFromQueue,
                onRestore = onRestoreToQueue,
                onMove = onMoveInQueue,
                onShuffleUpcoming = onShuffleUpcoming,
                onReorderStart = {
                    if (queueSheetState.currentValue != androidx.compose.material3.SheetValue.Expanded) {
                        queueSheetScope.launch { runCatching { queueSheetState.expand() } }
                    }
                },
                onClear = onClearQueue,
            )
        }
    }

    if (isSleepTimerSheetOpen) {
        GlassSheet(onDismissRequest = { isSleepTimerSheetOpen = false }) {
            SleepTimerContent(
                current = state.sleepTimer,
                remainingMs = state.sleepTimerRemainingMs,
                onSelect = { mode ->
                    onSetSleepTimer(mode)
                    isSleepTimerSheetOpen = false
                },
            )
        }
    }

    if (isOverflowSheetOpen) {
        GlassSheet(onDismissRequest = { isOverflowSheetOpen = false }) {
            val overflowContext = androidx.compose.ui.platform.LocalContext.current
            PlayerOverflowContent(
                item = item,
                sleepTimerActive = state.sleepTimer != null,
                playbackSpeed = playbackSpeed,
                onOpenSleepTimer = {
                    isOverflowSheetOpen = false
                    isSleepTimerSheetOpen = true
                },
                onOpenPlaybackSpeed = {
                    isOverflowSheetOpen = false
                    isSpeedSheetOpen = true
                },
                onOpenAddToPlaylist = {
                    isOverflowSheetOpen = false
                    if (item != null) isAddToPlaylistSheetOpen = true
                },
                onOpenAudioOutput = {
                    isOverflowSheetOpen = false
                    openOutputSwitcher(overflowContext)
                },
                isDownloaded = isCurrentDownloaded,
                onDownload = if (onDownloadCurrent != null && item is PlayableItem.YoutubeTrack && !isCurrentDownloaded) {
                    {
                        onDownloadCurrent()
                        isOverflowSheetOpen = false
                    }
                } else null,
                onRemoveDownload = if (onRemoveDownloadCurrent != null && isCurrentDownloaded) {
                    {
                        // Same UAT-audit fix as the other song-actions
                        // sheets (Search/Home/Local Library/Artist) — a
                        // confirm dialog rather than deleting instantly,
                        // matching every other download-destructive
                        // action in the app.
                        isRemoveDownloadConfirmOpen = true
                        isOverflowSheetOpen = false
                    }
                } else null,
            )
        }
    }

    if (isRemoveDownloadConfirmOpen && item != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Remove download?",
            message = "\"${item.title}\" will be deleted from this device. You can download it again later.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                onRemoveDownloadCurrent?.invoke()
                isRemoveDownloadConfirmOpen = false
            },
            onDismiss = { isRemoveDownloadConfirmOpen = false },
        )
    }

    if (isLyricsSheetOpen) {
        GlassSheet(onDismissRequest = { isLyricsSheetOpen = false }) {
            LyricsContent(
                result = lyrics,
                positionMs = state.positionMs,
                isPlaying = state.isPlaying,
                onSeekTo = onSeekTo,
                offsetMs = lyricOffsetMs,
                sourceName = lyricsSourceName,
                blurUnfocused = lyricsBlurUnfocused,
                onAdjustOffset = onAdjustLyricOffset,
                onResetOffset = onResetLyricOffset,
                showLyricStrip = showLyricStrip,
                onSetLyricStrip = onSetLyricStrip,
            )
        }
    }

    if (isSpeedSheetOpen) {
        GlassSheet(onDismissRequest = { isSpeedSheetOpen = false }) {
            PlaybackSpeedContent(
                selected = playbackSpeed,
                onSelect = { speed -> onSetPlaybackSpeed(speed) },
                onPreview = onPreviewPlaybackSpeed,
            )
        }
    }

    if (isAddToPlaylistSheetOpen) {
        GlassSheet(onDismissRequest = { isAddToPlaylistSheetOpen = false }) {
            AddToPlaylistContent(
                item = item,
                playlists = playlists,
                onSelectPlaylist = { playlist ->
                    onAddToPlaylist(playlist.id, playlist.name)
                    isAddToPlaylistSheetOpen = false
                },
                onCreateNew = {
                    isAddToPlaylistSheetOpen = false
                    isCreatePlaylistDialogOpen = true
                },
            )
        }
    }

    if (isCreatePlaylistDialogOpen) {
        com.whiplash.music.ui.theme.GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreatePlaylistAndAdd(name)
                isCreatePlaylistDialogOpen = false
            },
            onDismiss = { isCreatePlaylistDialogOpen = false },
        )
    }
}


/**
 * Custom seek bar — deliberately NOT built on Material3's `Slider`.
 *
 * Real, reported bug ("tapping the progress bar sometimes lands ~1 second
 * behind where I tapped, especially with quick repeated taps") was traced
 * all the way through the state layer with real on-device logging: every
 * single seek request, at every layer (this composable's tap math,
 * [com.whiplash.music.playback.controller.PlaybackController.seekTo]'s
 * clamp math, and the resulting position shown on the next recomposition)
 * was verified byte-for-byte correct across ~20 real manual taps — every
 * target computed from a tap was exactly what got rendered next. That
 * means the bug was never in this app's own state/logic, but inside
 * Material3 Slider's own internal drag-tracking state machine, which has
 * a documented, known quirk: calling back into a value change (like a
 * seek) from within its own onValueChangeFinished can leave its internal
 * gesture/animation state inconsistent with the externally-supplied
 * `value` on the next recomposition — exactly the kind of thing that
 * would show up as "snaps back near the previous position first, then
 * corrects." That is a purely visual artifact inside a component this app
 * doesn't control the internals of, not something fixable by changing
 * this app's own state updates further.
 *
 * The proven fix — verified directly against ViMusic's own real, shipping
 * source (`ui/components/SeekBar.kt`, MIT-licensed) — is to not use
 * Slider at all for a media seek bar. This is a minimal, from-scratch
 * tap/drag surface with no hidden internal value-tracking of its own: it
 * reports raw pointer positions directly, so there is no intermediate
 * state that can ever disagree with what was actually tapped.
 *
 * All four [com.whiplash.music.ui.theme.SeekBarStyle] options (section:
 * Appearance — "let the user pick the progress bar style they like")
 * share this exact same tap/drag gesture logic; only the track's drawing
 * differs per style (see [SeekBarTrack]), so switching styles can never
 * reintroduce the seek bug fixed above.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.whiplash.music.WhiplashApplication
    // The position ticker recomposes this composable roughly every 500ms
    // (positionMs updates), and settingsRepository.seekBarStyle is a
    // property getter that builds a brand-new Flow from dataStore.data.map
    // on every call. Without remembering it, collectAsState would restart
    // collection from `initial` on every single one of those recompositions
    // — a real bug that made the selected style appear to never take
    // effect, since it kept resetting back to CLASSIC before the real
    // persisted value could ever be collected and rendered.
    val seekBarStyleFlow = remember(app) { app.settingsRepository.seekBarStyle }
    val style by seekBarStyleFlow.collectAsState(initial = com.whiplash.music.ui.theme.SeekBarStyle.CLASSIC)

    val displayedPositionMs = if (isDragging) dragPositionMs else positionMs
    val fraction = if (durationMs > 0) (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Never let a seek land exactly at/past the track's real end — colliding
    // with the natural end-of-track path is what caused a separate, earlier
    // reported bug (unexpected restarts/skips when seeking very close to
    // the end).
    fun clampedTarget(rawMs: Long): Long {
        val safeDurationMs = (durationMs - END_OF_TRACK_SEEK_MARGIN_MS).coerceAtLeast(0L)
        return rawMs.coerceIn(0L, safeDurationMs)
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures(
                        onTap = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val rawMs = (offset.x / size.width * durationMs).toLong()
                            onSeekTo(clampedTarget(rawMs))
                        },
                    )
                }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragPositionMs = (offset.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragPositionMs = (change.position.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)
                        },
                        onDragEnd = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeekTo(clampedTarget(dragPositionMs))
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            SeekBarTrack(style = style, fraction = fraction, isDragging = isDragging)
        }
        if (style == com.whiplash.music.ui.theme.SeekBarStyle.HAIRLINE) {
            HairlineTimes(positionMs = displayedPositionMs, durationMs = durationMs)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMs(displayedPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = WhiplashColors.textTertiary,
                )
                Text(
                    text = formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = WhiplashColors.textTertiary,
                )
            }
        }
    }
}

/** See [SeekBar]'s doc — keeps a manual seek from ever landing exactly at/past the track's real end. */
private const val END_OF_TRACK_SEEK_MARGIN_MS = 1000L

/**
 * Draws the actual seek bar track for the selected
 * [com.whiplash.music.ui.theme.SeekBarStyle] — the only part that differs
 * between styles. [fraction] and [isDragging] are the single shared
 * source of truth from [SeekBar]; no style keeps its own position state.
 */
@Composable
private fun SeekBarTrack(style: com.whiplash.music.ui.theme.SeekBarStyle, fraction: Float, isDragging: Boolean) {
    when (style) {
        com.whiplash.music.ui.theme.SeekBarStyle.CLASSIC -> ClassicTrack(fraction)
        com.whiplash.music.ui.theme.SeekBarStyle.WAVY -> WavyTrack(fraction, isDragging)
        com.whiplash.music.ui.theme.SeekBarStyle.WAVEFORM -> WaveformTrack(fraction)
        com.whiplash.music.ui.theme.SeekBarStyle.MINIMAL -> MinimalTrack(fraction, isDragging)
        com.whiplash.music.ui.theme.SeekBarStyle.HAIRLINE -> HairlineTrack(fraction, isDragging)
    }
}

/**
 * Hairline style: a 1.5dp line with a small dot that grows while dragging.
 * The fill eases between position ticks so the line glides instead of
 * stepping every 500ms.
 */
@Composable
private fun HairlineTrack(fraction: Float, isDragging: Boolean) {
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (isDragging) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
        label = "hairlineFraction",
    )
    val thumbRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDragging) 7.dp else 4.dp,
        label = "hairlineThumb",
    )
    val activeColor = WhiplashColors.textPrimary
    val inactiveColor = WhiplashColors.glassBorderStrong
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val midY = size.height / 2f
        val stroke = 1.5.dp.toPx()
        val splitX = size.width * animatedFraction
        drawLine(inactiveColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(size.width, midY), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(activeColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(splitX, midY), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(color = activeColor, radius = thumbRadius.toPx(), center = androidx.compose.ui.geometry.Offset(splitX, midY))
    }
}

/** Time row for the Hairline style: elapsed on the left, remaining (as "-m:ss") on the right. */
@Composable
private fun HairlineTimes(positionMs: Long, durationMs: Long) {
    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatMs(positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = WhiplashColors.textTertiary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = if (durationMs > 0) "-" + formatMs(remainingMs) else formatMs(0),
            style = MaterialTheme.typography.labelSmall,
            color = WhiplashColors.textTertiary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .semantics { contentDescription = "${formatMs(remainingMs)} remaining" },
        )
    }
}

/** The original style: thin rounded track + circular thumb. */
@Composable
private fun ClassicTrack(fraction: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WhiplashColors.glassBorderStrong),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WhiplashColors.textPrimary),
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(WhiplashColors.textPrimary)
                .align(androidx.compose.ui.BiasAlignment(horizontalBias = fraction * 2f - 1f, verticalBias = 0f)),
        )
    }
}

/**
 * Android 13+ system media player's own "squiggly" seek bar. Modeled
 * directly on the real, open-source algorithm from mahozad/wavy-slider
 * (a maintained, MIT-licensed Compose Multiplatform library implementing
 * this exact Google-designed style) after an earlier version of this
 * track looked "choppy, like small straight lines joined" — a real,
 * correct visual critique. The root cause was sampling the sine curve only
 * once per half-wavelength (a handful of points across the whole bar),
 * so consecutive straight `lineTo` segments were individually visible as
 * flat facets instead of a continuous curve. The fix — confirmed against
 * that real library's own `createWavyPath` — is to sample at pixel
 * resolution (one `lineTo` per horizontal pixel) so the polyline is dense
 * enough to read as a smooth curve, and to animate the phase shift
 * continuously via a frame clock rather than a fixed-step tween.
 */
@Composable
private fun WavyTrack(fraction: Float, isDragging: Boolean) {
    val waveShiftPxPerSecond = 24f
    val waveShift = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            val elapsedSeconds = (withFrameNanos { it } - startTime) / 1_000_000_000f
            waveShift.value = elapsedSeconds * waveShiftPxPerSecond
        }
    }
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (isDragging) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(200),
        label = "wavyFraction",
    )
    val activeColor = WhiplashColors.textPrimary
    val inactiveColor = WhiplashColors.glassBorderStrong
    val waveAmplitudeDp = 4.dp
    val wavelengthDp = 20.dp

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val midY = size.height / 2f
        val amplitudePx = waveAmplitudeDp.toPx()
        val wavelengthPx = wavelengthDp.toPx()
        val splitX = size.width * animatedFraction

        // Squiggly wave on the played portion, sampled at every pixel so
        // the polyline reads as a smooth continuous curve rather than a
        // handful of visibly-straight facets.
        val wavePath = androidx.compose.ui.graphics.Path()
        val startRadians = waveShift.value / wavelengthPx * (2 * Math.PI).toFloat()
        wavePath.moveTo(0f, midY + amplitudePx * kotlin.math.sin(startRadians))
        var x = 1f
        while (x <= splitX) {
            val radians = (x + waveShift.value) / wavelengthPx * (2 * Math.PI).toFloat()
            val y = midY + amplitudePx * kotlin.math.sin(radians)
            wavePath.lineTo(x, y)
            x += 1f
        }
        drawPath(
            path = wavePath,
            color = activeColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )

        // Flat remaining portion.
        drawLine(
            color = inactiveColor,
            start = androidx.compose.ui.geometry.Offset(splitX, midY),
            end = androidx.compose.ui.geometry.Offset(size.width, midY),
            strokeWidth = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        // Thumb dot at the boundary.
        drawCircle(color = activeColor, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(splitX, midY))
    }
}

/**
 * Vertical bar/equalizer-style segments, like SoundCloud's or dedicated
 * waveform players' seek bars — a fixed number of evenly-spaced bars with
 * slightly randomized (but stable, seeded) heights for visual interest,
 * filled up to the current playback fraction.
 *
 * Two real, correct issues reported against an earlier version, both
 * fixed here:
 * - The bars spanned the full Canvas width edge-to-edge, so the first/last
 *   bars visually touched the position/duration timestamp text directly
 *   below them. A small horizontal inset (matching the thumb radius the
 *   other styles already keep clear of the edges) fixes this.
 * - The active-bar count was computed as a plain `(fraction * barCount).toInt()`,
 *   recalculated fresh on every ~500ms position tick — since that is a
 *   hard integer step, the fill visibly *jumped* forward one whole bar at
 *   a time instead of advancing smoothly, unlike the Classic/Wavy tracks'
 *   continuously-interpolated fill. Animating the underlying fraction
 *   itself (not just snapping the bar index) makes each bar's fill
 *   advance/blend smoothly frame-to-frame instead of jumping.
 */
@Composable
private fun WaveformTrack(fraction: Float) {
    val barCount = 40
    // A stable, seeded pseudo-random height per bar (not truly random on
    // every recomposition) so the waveform shape doesn't jitter as
    // position updates — it should look like a fixed waveform, exactly
    // like a real audio waveform display would.
    val barHeights = remember {
        val random = kotlin.random.Random(seed = 42)
        List(barCount) { 0.35f + random.nextFloat() * 0.65f }
    }
    val activeColor = WhiplashColors.textPrimary
    val inactiveColor = WhiplashColors.glassBorderStrong
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
        label = "waveformFraction",
    )
    val horizontalInsetDp = 7.dp

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val insetPx = horizontalInsetDp.toPx()
        val usableWidth = size.width - insetPx * 2f
        val barWidth = usableWidth / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        // Real, reported issue: the surrounding layout gives this seek
        // bar 24dp of space above it (between the title/artist text and
        // the bar) but only 16dp below it (between the bar and the
        // transport buttons) — see the Spacer calls around SeekBar's call
        // site. The thin Classic/Wavy/Minimal tracks don't visually
        // suffer from this asymmetry since they're a hairline centered in
        // a mostly-empty 28dp box, but the waveform's tall bars fill most
        // of that box, so they visually read as sitting closer to the
        // timestamps (the smaller gap) than to the title (the larger
        // gap) — exactly what was reported, confirmed against the
        // provided screenshot. Capping the bars at 65% of the box height
        // (instead of the full 28dp) gives enough empty margin on both
        // sides for that fixed 8dp layout asymmetry to no longer read as
        // visually off-center.
        val maxBarHeight = size.height * 0.65f
        // A continuous (non-integer) progress through the bars, so the
        // boundary bar itself fades between inactive/active color rather
        // than the fill advancing in a single discrete integer jump.
        val activeProgress = animatedFraction * barCount
        for (i in 0 until barCount) {
            val barHeightPx = maxBarHeight * barHeights[i]
            val xOffset = insetPx + i * (barWidth + gap)
            val barProgress = (activeProgress - i).coerceIn(0f, 1f)
            val color = androidx.compose.ui.graphics.lerp(inactiveColor, activeColor, barProgress)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(xOffset, (size.height - barHeightPx) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/**
 * An intentionally understated, Apple Music-esque style: an ultra-thin
 * 2dp line with no visible thumb at all while idle — the thumb only fades
 * in while actively dragging, so the resting state stays minimal.
 */
@Composable
private fun MinimalTrack(fraction: Float, isDragging: Boolean) {
    val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isDragging) 1f else 0f, label = "minimalThumbAlpha")
    Box(modifier = Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(WhiplashColors.glassBorderStrong),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(WhiplashColors.textPrimary),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(WhiplashColors.textPrimary.copy(alpha = thumbAlpha))
                .align(androidx.compose.ui.BiasAlignment(horizontalBias = fraction * 2f - 1f, verticalBias = 0f)),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** How far the title block rides up over the hero artwork's faded edge. */
private val HERO_TITLE_OVERLAP = 40.dp

/** Tallest the hero may be relative to its width. Tall enough to reach the title on tall phones (no empty band); covers crop a little at the sides. */
private const val HERO_MAX_ASPECT = 1.6f


/** 2.10: windows at least this wide and wider than tall get the two-pane layout. */
private val TWO_PANE_MIN_WIDTH = 600.dp
/** 2.10: max width of the player content column on tablets. */
private val PLAYER_MAX_CONTENT_WIDTH = 560.dp
/** 2.10: two-pane controls area needs at least this height to also show the lyric strip. */
private val TWO_PANE_STRIP_MIN_HEIGHT = 320.dp

/**
 * Crossfade + slight scale used when the track changes (both layouts), so
 * artwork, title and artist move as one cohesive transition. Callers key
 * the AnimatedContent on track id+source, not the whole item, so an
 * artwork-only update (low-res search thumbnail swapped for the resolved
 * higher-res cover) doesn't retrigger it and make the artwork "blink" twice.
 */
private fun androidx.compose.animation.AnimatedContentTransitionScope<PlayableItem?>.trackChangeTransition() =
    (androidx.compose.animation.fadeIn(animationSpec = tween(GlassTokens.animRegular)) +
        androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = tween(GlassTokens.animRegular)))
        .togetherWith(androidx.compose.animation.fadeOut(animationSpec = tween(GlassTokens.animFast)))

/** 2.9: length of the collapse-to-header transition. */
private const val COLLAPSE_ANIM_MS = 320
/** The big artwork block fades out fully so it never shows behind the header. */
private const val COLLAPSED_BODY_ALPHA = 0f
/** How much the big artwork block shrinks (from its top edge) while collapsed. */
private const val COLLAPSED_BODY_SHRINK = 0.08f

/**
 * Compact now-playing header shown under the top bar while the lyrics or
 * queue sheet is open, so the track stays identifiable above the sheet.
 * [progress] is the shared collapse value (0..1); it is read in the draw
 * layer only, so the animation doesn't recompose this row every frame.
 */
@Composable
private fun CollapsedPlayerHeader(
    item: PlayableItem,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slidePx = with(density) { 16.dp.toPx() }
    Row(
        modifier = modifier.graphicsLayer {
            val p = progress()
            alpha = p
            translationY = (1f - p) * slidePx
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(WhiplashRadius.small))
                .background(WhiplashColors.surfaceElevated),
        ) {
            if (item.artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.artworkUri)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Full-bleed "hero" cover (2.8): edge to edge across the top, running under
 * the status bar, fading out at the bottom into the player backdrop. A top
 * scrim keeps the status bar and top buttons readable on bright covers.
 * It follows the swipe-to-skip drag like the normal artwork does.
 */
@Composable
private fun HeroArtwork(
    artworkUri: String?,
    translationPx: () -> Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = translationPx()
                // Offscreen so the DstIn mask below fades the image itself
                // rather than punching through to the window.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black,
                        0.55f to androidx.compose.ui.graphics.Color.Black,
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            },
    ) {
        androidx.compose.animation.Crossfade(
            targetState = artworkUri,
            animationSpec = tween(if (animate) 450 else 0),
            label = "heroArtwork",
            modifier = Modifier.fillMaxSize(),
        ) { uri ->
            if (uri != null) {
                // Never draw the cover shorter than it is wide. YouTube's
                // large thumbnails are often 16:9 frames with the square
                // cover centred between plain side bars; a wide, short hero
                // (short screens, big font) would Crop into those bars and
                // show hard vertical edges. A full-width square (or taller)
                // centred and clipped keeps only real artwork visible.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(uri).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .requiredWidth(maxWidth)
                            .requiredHeight(maxOf(maxWidth, maxHeight)),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.3f)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color.Transparent),
                    ),
                ),
        )
    }
}

/** Artwork scale while paused (the "sleeve shrinks when paused" cue). */
private const val PAUSED_ARTWORK_SCALE = 0.86f

/**
 * How long a not-playing state must last before it counts as a pause.
 * Every track change passes through isPlaying=false for a moment (see
 * PlaybackController.playIndex); without this the artwork would dip on
 * every skip, which reads as a glitch rather than a pause.
 */
private const val PAUSE_SETTLE_MS = 250L

/**
 * Animated artwork scale for the full player: full size while playing or
 * loading, [PAUSED_ARTWORK_SCALE] when the user has genuinely paused.
 *
 * "Paused" deliberately excludes buffering, stream resolution and errors —
 * those are not something the user chose, and shrinking would suggest
 * they had. Uses a gentle low-bounce spring, which is what gives the
 * spring-back on play its physical feel. With reduced motion on, the
 * scale is left at full size, since it is purely decorative.
 */
@Composable
private fun rememberPausedArtworkScale(state: PlaybackState): Float {
    val reducedMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()
    val pausedNow = state.currentItem != null &&
        !state.isPlaying &&
        !state.isBuffering &&
        !state.isResolvingStream &&
        state.playbackError == null
    var settledPaused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(pausedNow) }
    androidx.compose.runtime.LaunchedEffect(pausedNow) {
        if (pausedNow) kotlinx.coroutines.delay(PAUSE_SETTLE_MS)
        settledPaused = pausedNow
    }
    val target = if (settledPaused && !reducedMotion) PAUSED_ARTWORK_SCALE else 1f
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "pausedArtworkScale",
    )
    return scale
}

/**
 * One muted line of technical detail under the title (Settings > Stats for
 * Nerds). Space is always reserved while the setting is on, so the seek bar
 * doesn't jump when the details arrive a moment after a track starts; the
 * text itself fades in and out with each track.
 */
@Composable
private fun StatsForNerdsLine(info: com.whiplash.music.playback.controller.AudioStreamInfo?) {
    val text = info?.summary()
    androidx.compose.animation.Crossfade(
        targetState = text,
        animationSpec = tween(GlassTokens.animRegular),
        label = "statsForNerds",
    ) { shown ->
        Text(
            text = shown ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textSecondary.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = GlassTokens.spaceSm)
                .semantics { contentDescription = shown?.let { "Audio format: " + it.replace(" · ", ", ") } ?: "" },
        )
    }
}

/**
 * Single-line text that scrolls when it doesn't fit (Compose's own
 * basicMarquee, which does nothing for text that already fits). Pauses
 * [initialDelayMillis] before the first pass and between passes, so the
 * start of the title is readable before it moves.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    initialDelayMillis: Int,
    animate: Boolean,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = if (animate) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (animate) {
            Modifier.fillMaxWidth().basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = initialDelayMillis,
                repeatDelayMillis = MARQUEE_REPEAT_DELAY_MS,
                velocity = 40.dp,
            )
        } else {
            Modifier.fillMaxWidth()
        },
    )
}

private const val TITLE_MARQUEE_DELAY_MS = 1_500
private const val ARTIST_MARQUEE_DELAY_MS = 2_700
private const val MARQUEE_REPEAT_DELAY_MS = 2_500
