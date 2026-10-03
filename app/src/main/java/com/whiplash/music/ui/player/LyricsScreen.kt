// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.LyricsResult
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import kotlinx.coroutines.launch

/**
 * Full-screen lyrics over the song's own colours (Spotify / Apple Music
 * style): a close button and a "⋯" menu for the rarely used timing tools up
 * top, the lyrics filling the middle with soft faded edges, then the source
 * credit and a mini player to pause or skip back to the player.
 *
 * Swipe down (anywhere, or past the top of the lyrics) or press Back to
 * close. The drag follows the finger and springs back if let go early.
 */
@Composable
fun LyricsScreen(
    item: PlayableItem,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    colors: PlayerColors,
    lyrics: LyricsResult?,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    offsetMs: Long = 0L,
    blurUnfocused: Boolean = false,
    onAdjustOffset: (Long) -> Unit = {},
    onResetOffset: () -> Unit = {},
    showLyricStrip: Boolean = true,
    onSetLyricStrip: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val reduceMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val latestClose by rememberUpdatedState(onClose)

    fun settle(velocity: Float) {
        scope.launch {
            if (drag.value > heightPx * CLOSE_FRACTION || (drag.value > 0f && velocity > CLOSE_VELOCITY)) {
                latestClose()
            } else {
                drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    // Past the top of the lyrics, a further pull down moves the whole
    // screen instead; pushing back up gives that back first.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drag.value > 0f) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationY = drag.value }
            .nestedScroll(connection)
            // Drags that start outside the lyrics list (header, credit, mini
            // player) close too, and never reach the player's own
            // drag-to-dismiss underneath.
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settle(velocity) },
            ),
    ) {
        PlayerMeshBackdrop(colors = colors, animate = isPlaying && !reduceMotion)
        if (colors.fromArtwork) {
            // A light scrim so white lyrics stay readable on bright covers.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LyricsTopBar(
                lyrics = lyrics,
                onClose = onClose,
                offsetMs = offsetMs,
                onAdjustOffset = onAdjustOffset,
                onResetOffset = onResetOffset,
                showLyricStrip = showLyricStrip,
                onSetLyricStrip = onSetLyricStrip,
            )
            LyricsBody(
                result = lyrics,
                positionMs = positionMs,
                isPlaying = isPlaying,
                onSeekTo = onSeekTo,
                offsetMs = offsetMs,
                blurUnfocused = blurUnfocused,
                modifier = Modifier
                    .weight(1f)
                    .fadingEdges(),
            )
            LyricsMiniPlayer(
                item = item,
                isPlaying = isPlaying,
                progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onTogglePlayPause = onTogglePlayPause,
                onOpenPlayer = onClose,
            )
        }
    }
}

/** Soft fade at the top and bottom of the lyrics instead of a hard cut. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.08f to Color.Black,
                0.88f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LyricsTopBar(
    lyrics: LyricsResult?,
    onClose: () -> Unit,
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    showLyricStrip: Boolean,
    onSetLyricStrip: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.spaceSm, vertical = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainIconButton(contentDescription = "Close lyrics", onClick = onClose) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(30.dp))
        }
        Text(
            text = "Lyrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.weight(1f).padding(start = GlassTokens.spaceXs),
        )
        // Timing and the player's lyric line only apply to synced lyrics.
        if (lyrics is LyricsResult.Synced) {
            PlainIconButton(contentDescription = "Lyrics options", onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }
    }
    if (menuOpen && lyrics is LyricsResult.Synced) {
        // Same sheet and row style as the player's own ⋯ menu.
        GlassSheet(onDismissRequest = { menuOpen = false }) {
            LyricsOptionRow(
                icon = Icons.Filled.Timer,
                label = "Lyrics timing",
                subtitle = if (offsetMs == 0L) "In sync with the song" else "Tap the value to reset",
            ) {
                LyricOffsetControl(offsetMs, onAdjustOffset, onResetOffset)
            }
            LyricsOptionRow(
                icon = Icons.Filled.Subtitles,
                label = "Lyric line on player",
                subtitle = "Show the current line above the seek bar",
                modifier = Modifier
                    .toggleable(value = showLyricStrip, role = Role.Switch, onValueChange = onSetLyricStrip),
            ) {
                Switch(
                    checked = showLyricStrip,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WhiplashColors.onAccent,
                        checkedTrackColor = WhiplashColors.accent,
                        uncheckedThumbColor = WhiplashColors.textSecondary,
                        uncheckedTrackColor = WhiplashColors.surfaceGlass,
                        uncheckedBorderColor = WhiplashColors.glassBorderStrong,
                    ),
                )
            }
            Spacer(Modifier.height(GlassTokens.spaceSm))
        }
    }
}

@Composable
private fun LyricsOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = WhiplashColors.textPrimary)
        Column(Modifier.weight(1f).padding(horizontal = GlassTokens.spaceMd)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/**
 * Song, a thin progress line and play/pause, so the lyrics never need
 * closing just to pause. Tapping the song goes back to the player.
 */
@Composable
private fun LyricsMiniPlayer(
    item: PlayableItem,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val shape = RoundedCornerShape(WhiplashRadius.medium)
    Column(
        modifier = Modifier
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (WhiplashColors.isLight) 0.5f else 0.10f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Back to player", onClick = onOpenPlayer)
                .padding(start = GlassTokens.spaceSm, top = GlassTokens.spaceSm, bottom = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassArtworkThumbnail(artworkUri = item.artworkUri, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = GlassTokens.spaceMd)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlainIconButton(contentDescription = if (isPlaying) "Pause" else "Play", onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = WhiplashColors.textPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(GlassTokens.spaceXs))
        }
        // Progress along the bottom edge of the card.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(WhiplashColors.textPrimary.copy(alpha = 0.12f))
                .semantics { contentDescription = "${(progress * 100).toInt()}% played" },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(WhiplashColors.textPrimary),
            )
        }
    }
}

// build-origin 0x532e416e73617269
/** Pulled down past this fraction of the screen, releasing closes the lyrics. */
private const val CLOSE_FRACTION = 0.2f

/** Downward fling speed (px/s) that closes regardless of distance. */
private const val CLOSE_VELOCITY = 1800f
