package com.whiplash.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Search field used across the app (Search, Library, Settings).
 *
 * A solid, borderless-looking pill in a tone just above the page — the
 * same family as the Settings cards and the mini player — with a leading
 * magnifier, and a round clear button that appears as soon as there's text.
 * Clearing keeps the keyboard up so you can type again straight away.
 * When focused the edge lights up slightly so it's clear where typing goes.
 *
 * Fully opaque on purpose: it sits over scrolling content, and see-through
 * here reads as a bug rather than as glass.
 */
@Composable
fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearchAction: () -> Unit = {},
) {
    val shape = RoundedCornerShape(WhiplashRadius.pill)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current

    val fill = lerp(WhiplashColors.background, Color.White, 0.08f)
    val edge by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (focused) 0.18f else 0.06f),
        animationSpec = tween(GlassTokens.animRegular),
        label = "searchFieldEdge",
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
        animationSpec = tween(GlassTokens.animRegular),
        label = "searchFieldIcon",
    )

    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(fill)
            .border(0.75.dp, edge, shape)
            .padding(start = GlassTokens.spaceMd, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(GlassTokens.spaceSm + 4.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = WhiplashTypography.bodyLarge,
                    color = WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = LocalTextStyle.current.copy(
                    color = WhiplashColors.textPrimary,
                    fontSize = WhiplashTypography.bodyLarge.fontSize,
                ),
                singleLine = true,
                cursorBrush = SolidColor(WhiplashColors.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = GlassTokens.spaceMd),
            )
        }
        // Always reserves its 44dp so text never shifts when it appears.
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(GlassTokens.animRegular)) + scaleIn(tween(GlassTokens.animRegular), initialScale = 0.6f),
                exit = fadeOut(tween(GlassTokens.animFast)) + scaleOut(tween(GlassTokens.animFast), targetScale = 0.6f),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Clear search") {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onQueryChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = WhiplashColors.textPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}
