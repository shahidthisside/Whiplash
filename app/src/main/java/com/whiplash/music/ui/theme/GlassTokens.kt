package com.whiplash.music.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized Liquid Glass design tokens.
 *
 * Section 43 requires opacity, blur, border, radius, elevation, padding,
 * pressed/disabled state and animation to be centralized rather than
 * reinvented per screen. This object is the single source of truth that
 * every Glass* component pulls from.
 */
object GlassTokens {

    // Blur radii. Kept modest per section 56 (avoid expensive blur / overdraw).
    val blurRegular: Dp = 20.dp
    val blurStrong: Dp = 32.dp

    // Surface opacity levels applied on top of the tonal base color.
    const val opacityRegular: Float = 0.55f
    const val opacityElevated: Float = 0.65f
    const val opacitySheet: Float = 0.85f
    const val opacityPressedDelta: Float = 0.08f
    const val opacityDisabled: Float = 0.38f

    // Border stroke width for the subtle hairline edge that catches light.
    val borderWidth: Dp = 1.dp

    // Standard spacing scale.
    val spaceXs: Dp = 4.dp
    val spaceSm: Dp = 8.dp
    val spaceMd: Dp = 16.dp
    val spaceLg: Dp = 24.dp
    val spaceXl: Dp = 32.dp

    // Elevation shadow (restrained per section 41 — "restrained shadows").
    val elevationRegular: Dp = 2.dp
    val elevationElevated: Dp = 6.dp
    val elevationSheet: Dp = 12.dp

    // Space to reserve at the bottom of any scrollable list that can render
    // underneath the persistent mini-player (which is an absolutely
    // positioned overlay, not part of the scroll layout — see
    // GlassMiniPlayer/MainActivity). Without this, a list's last item sits
    // directly behind the mini-player with no way to tap it. Sized generously
    // above the mini-player's actual measured height (~75dp content +
    // 16dp/spaceMd outer margin) so it clears comfortably on every density.
    val miniPlayerReservedHeight: Dp = 100.dp

    // Animation durations (ms). Motion quality over quantity (section 44).
    const val animFast: Int = 120
    const val animRegular: Int = 220
    const val animSlow: Int = 360
}
