package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 *
 * Deliberately a plain, flat surface — NOT a GlassSurface: a solid-color
 * rounded Box with no border, no blur, no translucency, matching how
 * most mainstream apps' toasts actually look (per explicit user
 * feedback — a "lighter" glass treatment still read as glass, and any
 * blur at all made the transition feel heavy).
 *
 * Appears/disappears instantly with no enter/exit animation at all
 * (no AnimatedVisibility, no fade, no scale) — also per explicit user
 * feedback: even a plain fade-only transition still read as the toast
 * "getting stuck" while dismissing. A stock system Toast has no custom
 * animation either; matching that exactly (appear, hold, disappear,
 * nothing in between) is the most reliably smooth option since there is
 * no animation left to look janky.
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
        if (currentMessage != null) {
            Box(
                modifier = Modifier
                    .widthIn(max = MAX_WIDTH)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(WhiplashColors.surfaceElevated),
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

/**
 * Caps how wide the toast pill can grow (section: fixing an oversized
 * toast). Most toast messages in this app are short, fixed phrases
 * ("Added to favorites", "Queue cleared"), so the pill never needed an
 * explicit width cap before — but a per-track message that interpolates
 * a real (sometimes very long) YouTube video title, like "Download
 * failed: <title>", could otherwise stretch the pill almost the full
 * screen width and make it look like an oversized banner rather than a
 * normal toast. Capping the width forces long text to wrap within
 * [maxLines] and ellipsize instead, matching how a stock system Toast
 * or Snackbar caps its own width.
 */
private val MAX_WIDTH = 320.dp
