package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

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
