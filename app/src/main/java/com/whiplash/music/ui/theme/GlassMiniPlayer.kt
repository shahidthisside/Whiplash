package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Persistent mini-player (section 47): artwork, title, artist,
 * previous/play-pause/next transport controls, thin progress indicator,
 * and a tap target to expand into the full player.
 * Fully opaque (not translucent) — it always sits directly above list
 * content, and a translucent chrome there reads as a rendering bug (list
 * items visible through it) rather than a premium glass effect.
 */
@Composable
fun GlassMiniPlayer(
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFraction: Float,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WhiplashRadius.large)
    val haptic = LocalHapticFeedback.current
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = GlassTokens.elevationElevated, shape = shape, clip = false)
            .clip(shape)
            .background(WhiplashColors.surfaceSheet)
            .clickable(onClick = onExpand)
            // Section 58: swipe the mini-player left/right for next/
            // previous — an additive gesture layered on top of the
            // already-working, accessible Previous/Next buttons (section
            // 58's own explicit requirement: "important actions must
            // still have accessible buttons" — this is never the only
            // way to skip a track).
            .pointerInput(onNext, onPrevious) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragAccumulatedPx = 0f },
                    onDragCancel = { dragAccumulatedPx = 0f },
                ) { _, dragAmount ->
                    dragAccumulatedPx += dragAmount
                    if (dragAccumulatedPx <= -SWIPE_THRESHOLD_PX) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNext()
                        dragAccumulatedPx = 0f
                    } else if (dragAccumulatedPx >= SWIPE_THRESHOLD_PX) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPrevious()
                        dragAccumulatedPx = 0f
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassArtworkThumbnail(artworkUri = artworkUri)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = GlassTokens.spaceSm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Transport cluster: previous / play-pause / next. Flat
            // PlainIconButton for previous/next (matching the minimal,
            // no-background inline convention already established for
            // secondary controls elsewhere, e.g. per-row 3-dot menus) so
            // the filled play/pause button remains the single visual
            // emphasis point in this compact row. Explicit spacedBy since
            // this is now 3 adjacent interactive controls in a Row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
            ) {
                PlainIconButton(
                    contentDescription = "Previous",
                    onClick = onPrevious,
                    size = 40.dp,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = WhiplashColors.textPrimary,
                    )
                }

                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = WhiplashColors.accent,
                    )
                } else {
                    GlassIconButton(
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = {
                            // Section 57: subtle haptic on play/pause.
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlayPause()
                        },
                        size = 40.dp,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = WhiplashColors.textPrimary,
                        )
                    }
                }

                PlainIconButton(
                    contentDescription = "Next",
                    onClick = onNext,
                    size = 40.dp,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = WhiplashColors.textPrimary,
                    )
                }
            }
        }

        // Progress indicator (section 47). Inset with horizontal margin and
        // fully rounded ends rather than a flush, square-cornered 2dp strip
        // along the exact bottom edge — the previous version read as a
        // stray border line rather than an intentional progress bar,
        // especially since its track color (glassBorder, ~12% alpha) is
        // barely distinguishable from a hairline border at that thickness.
        // A slightly thicker rounded pill with a clearly visible track
        // color reads unambiguously as "this is a progress indicator."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.spaceMd)
                .padding(bottom = GlassTokens.spaceSm)
                .height(3.dp)
                .clip(RoundedCornerShape(WhiplashRadius.pill))
                .background(WhiplashColors.surfaceElevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(WhiplashColors.accent),
            )
        }
    }
}

/** Minimum horizontal drag distance (px) before a mini-player swipe counts as a skip gesture. */
private const val SWIPE_THRESHOLD_PX = 120f
