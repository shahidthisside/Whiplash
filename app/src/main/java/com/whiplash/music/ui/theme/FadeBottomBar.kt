package com.whiplash.music.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Edge-to-edge bottom navigation in the style of the big streaming apps:
 * no pill or card, just the page background running into the bar, with
 * the open tab shown by a filled, bright icon and label while the rest
 * stay outlined and dimmed. Pair it with [BottomBarFade] at the bottom of
 * the content so the page appears to scroll away underneath the bar.
 *
 * [icon] gets whether its tab is selected so it can switch to a filled
 * variant; the two are crossfaded.
 */
@Composable
fun <T> FadeBottomBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    icon: @Composable (item: T, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Liquid Glass: a floating glass capsule over the page instead of a flat bar.
    val floating = LocalGlassBackdrop.current != null
    val capsule = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    Row(
        modifier = if (floating) {
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .androidxShadow(capsule)
                .liquidGlass(shape = capsule, fallback = WhiplashColors.surfaceElevated)
                .padding(vertical = 2.dp, horizontal = 4.dp)
        } else {
            modifier
                .fillMaxWidth()
                .background(WhiplashColors.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 6.dp, bottom = 4.dp)
        },
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            FadeBottomBarItem(
                isSelected = isSelected,
                label = label(item),
                onClick = { onSelect(item) },
                icon = { sel -> icon(item, sel) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A short gradient from transparent to the bar's colour, drawn over the
 * bottom edge of the content (under the mini player) so rows fade out as
 * they reach the bar instead of stopping at a hard line. Touches pass
 * straight through it.
 */
@Composable
fun BottomBarFade(modifier: Modifier = Modifier) {
    val bg = WhiplashColors.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to bg.copy(alpha = 0.7f),
                    1f to bg,
                ),
            ),
    )
}

@Composable
private fun FadeBottomBarItem(
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A small, springy press-in instead of a ripple.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "fadeBarPressScale",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> WhiplashColors.textPrimary
            LocalGlassBackdrop.current != null -> WhiplashColors.textPrimary.copy(alpha = 0.82f)
            else -> WhiplashColors.textSecondary
        },
        animationSpec = tween(GlassTokens.animRegular),
        label = "fadeBarContentColor",
    )

    val onGlass = LocalGlassBackdrop.current != null
    val pillAlpha by animateFloatAsState(
        targetValue = if (onGlass && isSelected) 1f else 0f,
        animationSpec = tween(GlassTokens.animSlow),
        label = "glassTabPill",
    )
    val pillColor = WhiplashColors.textPrimary
    Column(
        modifier = modifier
            .padding(vertical = if (onGlass) 4.dp else 0.dp)
            .then(
                if (onGlass) {
                    // Selected tab: a lighter capsule inside the glass, like iOS.
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = pillColor.copy(alpha = 0.14f * pillAlpha),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.Tab
                this.selected = isSelected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = isSelected, animationSpec = tween(GlassTokens.animRegular), label = "fadeBarIcon") { sel ->
                    icon(sel)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    // On glass, a soft shadow keeps labels readable over bright art.
                    shadow = if (LocalGlassBackdrop.current != null) {
                        androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 6f)
                    } else null,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Soft drop shadow under the floating glass bar, lifting it off the page. */
private fun Modifier.androidxShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.shadow(elevation = 18.dp, shape = shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
