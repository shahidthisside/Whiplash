// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Horizontal fling-to-skip on the full player's artwork (2.3).
 *
 * The raw finger distance is kept in [dragPx]; the artwork only moves by
 * [FOLLOW_FRACTION] of it, so it feels attached but heavy rather than
 * sliding off like a pager. Release past [SKIP_DISTANCE_FRACTION] of the
 * artwork width, or with a fast flick, skips; anything else springs back.
 * Previous uses the normal Previous behaviour, so a swipe more than 3s
 * into a song restarts it, same as the button.
 *
 * State lives outside the track-change AnimatedContent, so the spring back
 * keeps running smoothly while the new track's artwork fades in.
 */
@Stable
class ArtworkSwipeState(private val scope: CoroutineScope) {
    internal val dragPx = Animatable(0f)
    internal var widthPx = 1f

    /** Artwork translation in px (already damped). */
    val translationPx: Float get() = dragPx.value * FOLLOW_FRACTION

    /** -1..1: how far toward a previous (−) or next (+) skip the drag is. */
    val progress: Float
        get() = (-dragPx.value / (widthPx * SKIP_DISTANCE_FRACTION)).coerceIn(-1f, 1f)

    internal fun snapBy(delta: Float) = scope.launch { dragPx.snapTo(dragPx.value + delta) }
    internal fun settle() = scope.launch {
        dragPx.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
    }
}

@Composable
fun rememberArtworkSwipeState(): ArtworkSwipeState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ArtworkSwipeState(scope) }
}

/**
 * Detects the swipe and moves the artwork. Vertical drags are left alone
 * (detectHorizontalDragGestures only claims horizontal movement), so this
 * does not fight a future drag-to-dismiss.
 */
fun Modifier.artworkSwipeToSkip(
    state: ArtworkSwipeState,
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipFeedback: () -> Unit,
): Modifier = this
    .graphicsLayer { translationX = state.translationPx }
    .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        state.widthPx = size.width.toFloat().coerceAtLeast(1f)
        val velocity = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { velocity.resetTracking() },
            onHorizontalDrag = { change, delta ->
                velocity.addPosition(change.uptimeMillis, change.position)
                change.consume()
                state.snapBy(delta)
            },
            onDragEnd = {
                val vx = velocity.calculateVelocity().x
                val distance = state.dragPx.value
                val far = abs(distance) > state.widthPx * SKIP_DISTANCE_FRACTION
                val fast = abs(vx) > FLING_VELOCITY_PX_S && abs(distance) > state.widthPx * 0.08f
                if (far || fast) {
                    // Direction of the gesture, not of the velocity alone: a
                    // flick back toward the start shouldn't skip the other way.
                    val towardNext = if (far) distance < 0 else vx < 0
                    onSkipFeedback()
                    if (towardNext) onNext() else onPrevious()
                }
                state.settle()
            },
            onDragCancel = { state.settle() },
        )
    }

/** Fast-forward / rewind glyph that fades in on the side the artwork is heading toward. */
@Composable
fun BoxScope.ArtworkSwipeHint(state: ArtworkSwipeState) {
    val p = state.progress
    if (p == 0f) return
    Box(
        modifier = Modifier
            .align(if (p > 0f) Alignment.CenterEnd else Alignment.CenterStart)
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                alpha = abs(p)
                val s = 0.8f + 0.2f * abs(p)
                scaleX = s; scaleY = s
            }
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (p > 0f) Icons.Filled.FastForward else Icons.Filled.FastRewind,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Haptic used when a swipe actually skips. */
@Composable
fun rememberSkipHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

private const val FOLLOW_FRACTION = 0.35f
private const val SKIP_DISTANCE_FRACTION = 0.30f
private const val FLING_VELOCITY_PX_S = 1200f
