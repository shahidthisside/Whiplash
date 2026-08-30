package com.whiplash.music.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A single, app-wide toast surface (section: brief popup feedback for
 * favoriting, pinning, playlist/queue changes, etc. — matching how
 * Spotify/YouTube Music briefly confirm this class of silent action
 * instead of leaving the user guessing whether it worked).
 *
 * Mounted exactly once, at the very top of the composable tree (see
 * MainActivity's WhiplashApp — drawn after even the full player, so a
 * toast triggered by an action taken inside the expanded player is still
 * visible). Screens never render their own toast UI; they just call
 * [ToastController.show] and this single host reacts.
 *
 * One message on screen at a time, each shown for [durationMs] and then
 * auto-dismissed; a new message arriving while one is showing replaces it
 * immediately rather than queuing behind it — for this class of frequent,
 * low-stakes confirmation ("Added to favorites", "Added to Coldplay
 * playlist"), showing the *latest* action's result is more useful than
 * making the user wait through a backlog of older ones.
 */
@Composable
fun GlassToastHost(modifier: Modifier = Modifier) {
    var currentMessage by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ToastController.events.collect { message ->
            currentMessage = message
            val myGeneration = ++generation
            scope.launch {
                delay(DURATION_MS)
                // Only clear if no newer message has arrived in the
                // meantime — otherwise this stale dismissal would cut off
                // a message that replaced this one after it was shown.
                if (generation == myGeneration) currentMessage = null
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = currentMessage != null,
            enter = fadeIn(tween(GlassTokens.animRegular)) + slideInVertically(tween(GlassTokens.animRegular)) { it / 2 },
            // Dismissal previously used animFast (120ms) — too short to
            // smoothly interpolate a frosted/blurred surface (GlassSurface
            // applies a real backdrop blur, which is a comparatively
            // expensive draw-time effect), so it visibly stuttered/snapped
            // right at the end instead of gently fading away — a real,
            // reported UI issue, not just a perception one. Matching the
            // enter animation's duration (220ms) fixes this: the same
            // motion that reads as smooth going in now reads as smooth
            // going out too.
            exit = fadeOut(tween(GlassTokens.animRegular)) + slideOutVertically(tween(GlassTokens.animRegular)) { it / 2 },
        ) {
            GlassSurface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(WhiplashRadius.pill),
                tint = WhiplashColors.surfaceSheet,
                opacity = GlassTokens.opacitySheet,
                elevation = GlassTokens.elevationSheet,
            ) {
                Text(
                    text = currentMessage.orEmpty(),
                    style = WhiplashTypography.bodyMedium,
                    color = WhiplashColors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
                )
            }
        }
    }
}

private const val DURATION_MS = 2600L
