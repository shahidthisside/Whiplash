package com.whiplash.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Floating pill-style bottom navigation bar (section 43: `GlassBottomBar`),
 * matching the modern footer-navigation pattern used by mainstream apps
 * (YouTube Music, Spotify): a fully rounded, opaque, shadow-lifted bar with
 * margin from the screen edges, rather than an edge-to-edge rectangle with
 * a visible border stroke. Each item shows an animated pill highlight
 * behind its icon when selected instead of a full-row color change.
 */
@Composable
fun <T> GlassBottomBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    icon: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = GlassTokens.elevationSheet,
                    shape = RoundedCornerShape(WhiplashRadius.pill),
                    clip = false,
                )
                .clip(RoundedCornerShape(WhiplashRadius.pill))
                .background(WhiplashColors.surfaceSheet)
                .padding(vertical = GlassTokens.spaceSm, horizontal = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                BottomBarItem(
                    isSelected = isSelected,
                    label = label(item),
                    onClick = { onSelect(item) },
                    icon = { icon(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) WhiplashColors.surfaceElevated else WhiplashColors.surfaceSheet,
        animationSpec = tween(GlassTokens.animRegular),
        label = "bottomBarPillColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) WhiplashColors.accent else WhiplashColors.textSecondary,
        animationSpec = tween(GlassTokens.animRegular),
        label = "bottomBarContentColor",
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(WhiplashRadius.large))
            .background(pillColor)
            .padding(horizontal = 4.dp, vertical = GlassTokens.spaceSm)
            .semantics {
                role = Role.Tab
                this.selected = isSelected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            // Fixed-height icon slot so every item's icon sits on the same
            // baseline regardless of whether its label wraps to one or two
            // lines below it — previously a longer label (e.g. "Local
            // Music") wrapping to two lines pushed that item's icon out of
            // alignment with its single-line neighbors, which is exactly
            // the "icon and name are here and there" misalignment reported.
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
