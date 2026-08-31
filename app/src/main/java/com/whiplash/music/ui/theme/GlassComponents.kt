package com.whiplash.music.ui.theme

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Base frosted-glass surface.
 *
 * All Glass* components in the design system should be built from this
 * primitive (section 43: "do not manually create different glass effects
 * on every screen"). It applies:
 *
 * - a tonal translucent background,
 * - a subtle blur (gracefully cheap; real backdrop blur of content behind
 *   this surface requires API 31+ RenderEffect, which [Modifier.blur] uses
 *   automatically when available and no-ops to a plain tint otherwise),
 * - a soft hairline border that catches light,
 * - restrained elevation/shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WhiplashRadius.medium),
    tint: Color = WhiplashColors.surfaceGlass,
    opacity: Float = GlassTokens.opacityRegular,
    borderColor: Color = WhiplashColors.glassBorder,
    elevation: Dp = GlassTokens.elevationRegular,
    blurRadius: Dp = GlassTokens.blurRegular,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
    ) {
        // Background layer only: blur must never affect the content slot,
        // otherwise text/icons drawn on top become illegible (blur is a
        // draw-layer effect that also blurs children in the same node).
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = blurRadius)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = opacity),
                            tint.copy(alpha = opacity * 0.92f),
                        )
                    )
                )
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = GlassTokens.borderWidth, color = borderColor, shape = shape)
        )
        content()
    }
}

/**
 * A [GlassSurface] preconfigured as a content card with standard padding.
 * The default building block for list items, album/artist tiles, and
 * generic content containers throughout the app.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(WhiplashRadius.medium),
    tint: Color = WhiplashColors.surfaceElevated,
    opacity: Float = GlassTokens.opacityElevated,
    contentPadding: Dp = GlassTokens.spaceMd,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        tint = tint,
        opacity = opacity,
        elevation = GlassTokens.elevationElevated,
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** Convenience accessor mirroring MaterialTheme.colorScheme-style usage. */
object GlassDefaults {
    val cardShape @Composable get() = MaterialTheme.shapes.medium
    val sheetShape @Composable get() = RoundedCornerShape(
        topStart = WhiplashRadius.extraLarge,
        topEnd = WhiplashRadius.extraLarge,
    )
}

/**
 * A gently pulsing alpha (section 44: motion quality, kept subtle) for
 * skeleton loading placeholders anywhere in the app — shared by the
 * Search screen's result skeletons and Home's Speed dial/Quick Picks
 * skeletons, so every skeleton in the app pulses identically rather than
 * each screen re-implementing its own slightly different shimmer timing.
 * Section 55: this is a purely decorative, nonessential animation, so
 * when the system's reduced-motion preference is on, it freezes at a
 * static mid-value instead of continuously pulsing — the skeleton shape
 * itself (still a real loading indicator) remains, only the shimmer
 * motion is removed.
 */
@Composable
fun rememberShimmerAlpha(): androidx.compose.runtime.State<Float> {
    if (com.whiplash.music.ui.common.isReducedMotionEnabled()) {
        return androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    }
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(GlassTokens.animSlow * 2),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
}

/** A single shimmering skeleton box — the shared primitive both [ShimmerSkeletonRow] and Home's skeletons build on. */
@Composable
fun ShimmerBox(modifier: Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    val shimmerAlpha by rememberShimmerAlpha()
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(WhiplashColors.surfaceElevated.copy(alpha = shimmerAlpha)),
    )
}

/**
 * Shared skeleton row shaped like [GlassListItem] (artwork + title +
 * subtitle) — used by both the Search screen's loading state and Home's
 * Quick Picks loading state, so a list-row skeleton looks identical
 * everywhere it appears in the app.
 */
@Composable
fun ShimmerSkeletonRow(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        ShimmerBox(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(WhiplashRadius.small))
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(start = GlassTokens.spaceSm).weight(1f),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceXs))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp))
        }
    }
}
