package com.whiplash.music.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Persistent app header — shows the bold "Whiplash" brand wordmark on the
 * Home tab, and smoothly swaps to the current section's name on every
 * other tab (Search, Library, Favorites, Playlists, Settings) so the user
 * always has a clear, friendly label for where they are, matching the
 * "swap the header like a real app, don't just always show the brand
 * name" request. The swap is an up/down slide + fade (not an abrupt
 * label replace) so it reads as one continuous, premium transition rather
 * than a jarring text change.
 */
@Composable
fun WhiplashAppHeader(title: String = "Whiplash", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Horizontal inset must match the screen content below it. Every
            // screen — Home, Search, Library, Favorites, Playlists, Settings —
            // insets its content by spaceMd, so the header using spaceLg left
            // the title sitting 8dp further right than the first section label
            // beneath it ("Whiplash" against "Quick Picks", "Settings" against
            // "Playback"), which read as a misalignment rather than a
            // deliberate indent.
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(GlassTokens.animRegular)) { it / 2 } + fadeIn(animationSpec = tween(GlassTokens.animRegular)))
                    .togetherWith(slideOutVertically(animationSpec = tween(GlassTokens.animFast)) { -it / 2 } + fadeOut(animationSpec = tween(GlassTokens.animFast)))
            },
            label = "appHeaderTitle",
        ) { currentTitle ->
            Text(
                text = currentTitle,
                color = WhiplashColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-0.3).sp,
            )
        }
    }
}
