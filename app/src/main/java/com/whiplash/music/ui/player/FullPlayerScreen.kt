package com.whiplash.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayedFraction = if (isDragging) dragFraction else fraction

    Column {
        Slider(
            value = displayedFraction,
            onValueChange = {
                isDragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                // Section 57: subtle haptic on slider interaction, fired
                // once on release (not continuously while dragging, which
                // would be excessive per the "do not vibrate for every
                // animation" guidance).
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSeekTo((dragFraction * durationMs).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = WhiplashColors.textPrimary,
                activeTrackColor = WhiplashColors.textPrimary,
                inactiveTrackColor = WhiplashColors.glassBorderStrong,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(if (isDragging) (dragFraction * durationMs).toLong() else positionMs),
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

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
