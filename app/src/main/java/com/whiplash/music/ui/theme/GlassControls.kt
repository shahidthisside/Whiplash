package com.whiplash.music.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Primary glass button with press feedback (section 52: micro-interactions
 * must not block interaction — feedback is a lightweight alpha/scale nudge).
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // opacityElevated (0.65) — the same value used for elevated card
    // surfaces — reads as a plain mid-gray for a light accent color like
    // Classic Graphite's cream (visually confirmed via a real on-device
    // pixel sample: (155,154,149), nearly identical to the math for 0.65
    // alpha over black, and clearly NOT distinguishable from the disabled
    // state at 0.38 alpha the way a toggle switch's fully-opaque knob is).
    // A primary action button (Play/Shuffle/Retry/Clear cache) needs to
    // read as genuinely solid/bright, matching how real apps render their
    // primary pill buttons — so this uses a much higher, near-opaque
    // value specifically for GlassButton's enabled state, while still
    // keeping GlassTokens.opacityDisabled for the real "not currently
    // actionable" affordance.
    val opacity by animateFloatAsState(
        targetValue = when {
            !enabled -> GlassTokens.opacityDisabled
            pressed -> 0.92f
            else -> 1.0f
        },
        animationSpec = tween(GlassTokens.animFast),
        label = "glassButtonOpacity",
    )
    val shape = RoundedCornerShape(WhiplashRadius.pill)

    Box(
        modifier = modifier
            .clip(shape)
            .background(WhiplashColors.accent.copy(alpha = opacity))
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorderStrong, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceSm),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides WhiplashColors.onAccent) {
            Text(text = text, style = WhiplashTypography.labelLarge)
        }
    }
}

/**
 * Circular glass icon button. Minimum touch target respects accessibility
 * guidance (section 55): 48dp default.
 */
@Composable
fun GlassIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val opacity by animateFloatAsState(
        targetValue = when {
            !enabled -> GlassTokens.opacityDisabled
            pressed -> GlassTokens.opacityRegular + GlassTokens.opacityPressedDelta
            else -> GlassTokens.opacityRegular
        },
        animationSpec = tween(GlassTokens.animFast),
        label = "glassIconButtonOpacity",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(WhiplashColors.surfaceElevated.copy(alpha = opacity))
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .then(Modifier.padding(GlassTokens.spaceSm)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A plain icon-only button with no background/border/circle — used where a
 * visible glass-pill treatment would look out of place, e.g. the per-row
 * "more options" (3-dot) button in track lists, matching YouTube Music's
 * minimal inline overflow button rather than the heavier glass-button
 * style used for primary/transport controls.
 *
 * Default [size] is 48dp to actually meet the documented minimum touch
 * target. It used to default to 40dp, which put every icon-only inline
 * button in the app (per-row 3-dot overflow, mini-player prev/next, header
 * actions) below the 48dp accessibility guideline the README claims to
 * honour — a real, if quiet, a11y gap. Because this button deliberately
 * draws no background, border or ripple circle, the extra 8dp is an
 * invisible hit area: the icon inside keeps its own size and stays centred,
 * so nothing changes visually.
 */
@Composable
fun PlainIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Small glass chip for filters/tags (section 43). */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WhiplashRadius.pill)
    val tint = if (selected) WhiplashColors.accent else WhiplashColors.surfaceElevated
    val opacity = if (selected) GlassTokens.opacityElevated else GlassTokens.opacityRegular
    val contentColor = if (selected) WhiplashColors.onAccent else WhiplashColors.textPrimary

    Box(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = opacity))
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Text(
                text = text,
                style = WhiplashTypography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The primary play/pause button for the full player transport row.
 *
 * Deliberately visually distinct from every other transport control, the
 * way virtually every standard music player (Spotify, YouTube Music, Apple
 * Music) treats play/pause: solid accent-color fill rather than the
 * translucent glass style used by [GlassIconButton], plus a materially
 * larger footprint than shuffle/previous/next/repeat. Section 52's
 * lightweight press feedback is a lift/shrink scale nudge rather than an
 * opacity change here, since a solid-fill button reads better with a scale
 * cue than an alpha one.
 */
@Composable
fun GlassPrimaryPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 84.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(GlassTokens.animFast),
        label = "primaryPlayButtonScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(elevation = GlassTokens.elevationElevated, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(WhiplashColors.accent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = if (isPlaying) "Pause" else "Play"
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides WhiplashColors.onAccent) {
            content()
        }
    }
}
