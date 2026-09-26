package com.whiplash.music.ui.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 2.11: shared state for dismissing the full player, either by dragging it
 * down or by the system predictive-back gesture. Both drive the same
 * [progress] (0 = fully open, 1 = gone), so the player previews its exit the
 * same way whichever gesture the user makes.
 */
@Stable
class PlayerDismissState internal constructor() {
    internal val progressAnim = Animatable(0f)

    /** 0..1, read in the draw layer only. */
    val progress: Float get() = progressAnim.value

    internal suspend fun settleBack() {
        progressAnim.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    /** Called when the player is opened again, so it never reappears half-dragged. */
    internal suspend fun reset() = progressAnim.snapTo(0f)
}

@Composable
fun rememberPlayerDismissState(): PlayerDismissState = remember { PlayerDismissState() }

/** Drag distance, as a fraction of the height, past which releasing dismisses. */
private const val DISMISS_FRACTION = 0.25f
/** Downward fling speed (px/s) that dismisses regardless of distance. */
private const val DISMISS_VELOCITY = 1800f
/** How far the player shrinks while a predictive back is in progress. */
private const val BACK_PREVIEW_SHRINK = 0.1f

/**
 * Vertical drag-down-to-dismiss. Only downward movement moves the player;
 * horizontal gestures (artwork skip, seek bar) are unaffected because this
 * listens on the vertical axis only.
 */
fun Modifier.playerDragToDismiss(
    state: PlayerDismissState,
    heightPx: () -> Float,
    enabled: Boolean,
    onDismiss: () -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val dragState = rememberDraggableState { delta ->
        val h = heightPx().coerceAtLeast(1f)
        scope.launch {
            state.progressAnim.snapTo((state.progress + delta / h).coerceIn(0f, 1f))
        }
    }
    draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        enabled = enabled,
        onDragStopped = { velocity ->
            if (state.progress >= DISMISS_FRACTION || velocity >= DISMISS_VELOCITY) {
                onDismiss()
            } else {
                state.settleBack()
            }
        },
    )
}

/**
 * Draws the player following the dismiss progress: a drag slides it down
 * 1:1 with the finger; a predictive back (which reports progress from the
 * same state) also shrinks it slightly so it reads as "going back".
 */
fun Modifier.playerDismissTransform(
    state: PlayerDismissState,
    heightPx: () -> Float,
    isBackGesture: () -> Boolean,
): Modifier = this.graphicsLayer {
    val p = state.progress
    if (isBackGesture()) {
        val scale = 1f - p * BACK_PREVIEW_SHRINK
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        translationY = p * 48.dp.toPx()
        shape = androidx.compose.foundation.shape.RoundedCornerShape((p * 32).dp)
        clip = p > 0f
    } else {
        translationY = p * heightPx()
    }
}

/**
 * Predictive back for the full player. While the system back gesture is in
 * progress the player previews its exit; committing collapses it, cancelling
 * springs it back. On devices without predictive back this still receives a
 * plain back press and collapses immediately.
 */
@Composable
fun PlayerPredictiveBack(
    enabled: Boolean,
    state: PlayerDismissState,
    onBackGestureActive: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        onBackGestureActive(true)
        try {
            events.collect { event -> state.progressAnim.snapTo(event.progress) }
            onDismiss()
        } catch (e: CancellationException) {
            // Gesture cancelled: this coroutine is already cancelled, so the
            // spring back has to run in a non-cancellable context.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { state.settleBack() }
            throw e
        } finally {
            onBackGestureActive(false)
        }
    }
}
