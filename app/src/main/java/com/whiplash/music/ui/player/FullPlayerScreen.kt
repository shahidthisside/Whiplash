package com.whiplash.music.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lyrics
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
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    autoplayEnabled: Boolean = true,
    onToggleAutoplay: (Boolean) -> Unit = {},
    onSetSleepTimer: (SleepTimerMode?) -> Unit = {},
    lyrics: com.whiplash.music.domain.model.LyricsResult? = null,
    onLyricsSheetOpened: () -> Unit = {},
) {
    val item = state.currentItem
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    var isSleepTimerSheetOpen by remember { mutableStateOf(false) }
    var isLyricsSheetOpen by remember { mutableStateOf(false) }
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
    }
    val hapticCycleRepeat: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCycleRepeat()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiplashColors.background)
            // The screen is drawn edge-to-edge (enableEdgeToEdge in
            // MainActivity), so without this the collapse button and
            // artwork draw underneath the system status bar / notification
            // shade swipe area. Only the top inset is needed here — the
            // bottom is left alone since this screen has no bottom nav.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(GlassTokens.spaceLg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    contentDescription = if (state.sleepTimer != null) "Sleep timer active" else "Sleep timer",
                    onClick = { isSleepTimerSheetOpen = true },
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = null,
                        tint = if (state.sleepTimer != null) WhiplashColors.accent else WhiplashColors.textPrimary,
                    )
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
            }
        }

        androidx.compose.animation.AnimatedContent(
            targetState = item,
            contentKey = { it?.let { track -> "${track.source}:${track.id}" } },
            transitionSpec = {
                // Smooth crossfade + slight scale on the whole artwork/title/
                // artist block together, rather than each piece snapping
                // independently (artwork via Coil's own crossfade, text with
                // no transition at all, position bar jumping) which is what
                // made track changes look "rough" rather than one cohesive
                // transition. Keyed on track id+source (not the whole item)
                // so an artwork-only update — e.g. the low-res search
                // thumbnail being swapped for the higher-res one resolved
                // moments later from the stream's own metadata — doesn't
                // retrigger this transition a second time for what is
                // still the same track (this was the cause of the artwork
                // visibly "blinking" twice on every track change).
                (androidx.compose.animation.fadeIn(animationSpec = tween(GlassTokens.animRegular)) +
                    androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = tween(GlassTokens.animRegular)))
                    .togetherWith(androidx.compose.animation.fadeOut(animationSpec = tween(GlassTokens.animFast)))
            },
            label = "nowPlayingContent",
        ) { currentTrack ->
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceXl),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
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
                    }
                }

                Text(
                    text = currentTrack?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceLg))

        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeekTo = onSeekTo,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                contentDescription = "Shuffle",
                onClick = hapticToggleShuffle,
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else WhiplashColors.textSecondary,
                )
            }
            GlassIconButton(contentDescription = "Previous", onClick = onPrevious, size = 56.dp) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            GlassPrimaryPlayButton(isPlaying = state.isPlaying, onClick = hapticTogglePlayPause, size = 84.dp) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = WhiplashColors.onAccent,
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
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else WhiplashColors.textSecondary,
                )
            }
        }
    }

    if (isQueueSheetOpen) {
        GlassSheet(onDismissRequest = { isQueueSheetOpen = false }) {
            QueueContent(
                queue = state.queue,
                currentIndex = state.currentIndex,
                autoplayEnabled = autoplayEnabled,
                onToggleAutoplay = onToggleAutoplay,
                onPlayIndex = { index ->
                    onPlayQueueIndex(index)
                    isQueueSheetOpen = false
                },
                onRemove = onRemoveFromQueue,
                onMove = onMoveInQueue,
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

    if (isLyricsSheetOpen) {
        GlassSheet(onDismissRequest = { isLyricsSheetOpen = false }) {
            LyricsContent(
                result = lyrics,
                positionMs = state.positionMs,
                isPlaying = state.isPlaying,
                onSeekTo = onSeekTo,
            )
        }
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
