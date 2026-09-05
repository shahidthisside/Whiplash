package com.whiplash.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Queue bottom sheet (section 21/50): current + upcoming tracks, tap to
 * jump, remove, clear. Drag-reorder is intentionally deferred — Compose's
 * reorderable-LazyColumn pattern needs a dedicated gesture/state library or
 * a hand-rolled pointer-input implementation to feel genuinely "immediate"
 * per section 50, and a half-working drag would be worse than none; move
 * up/down icon buttons deliver the same reorder capability accessibly in
 * the meantime without pretending to a a polish level not yet built.
 */
@Composable
fun QueueContent(
    queue: List<PlayableItem>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    onToggleAutoplay: (Boolean) -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge,
                color = WhiplashColors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shortcut for the same Autoplay setting in Settings
                // (section 22) — surfaced here too since queue-exhaustion
                // behavior is exactly what a user checking the queue is
                // often thinking about in the moment, without having to
                // leave the sheet and navigate to a different tab. Setting
                // remains fully present and controllable from Settings as
                // well; this is an additional shortcut, not a replacement.
                // A single tappable icon (filled+accent when on, outlined+
                // dim when off) rather than a Material Switch: a full-size
                // Switch reads as bulky/heavy squeezed into this compact
                // icon-button row, out of place next to the plain 40dp
                // icon buttons beside it (confirmed visually, see also the
                // Favorite heart's identical filled/outlined convention).
                PlainIconButton(
                    contentDescription = if (autoplayEnabled) "Autoplay on. Tap to turn off." else "Autoplay off. Tap to turn on.",
                    onClick = { onToggleAutoplay(!autoplayEnabled) },
                    size = 48.dp,
                ) {
                    Icon(
                        imageVector = if (autoplayEnabled) Icons.Filled.Autorenew else Icons.Outlined.Autorenew,
                        contentDescription = null,
                        tint = if (autoplayEnabled) WhiplashColors.accent else WhiplashColors.textTertiary,
                    )
                }
                if (queue.size > 1) {
                    PlainIconButton(contentDescription = "Clear queue", onClick = onClear, size = 48.dp) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = WhiplashColors.textSecondary)
                    }
                }
            }
        }

        // Open the queue positioned on the track that's actually playing.
        //
        // The list previously always started at the top, so with a queue that
        // autoplay had extended — which can run to hundreds of entries — the
        // current song was far below the fold and had to be hunted for by
        // scrolling, even though the sheet already knew which row it was.
        //
        // Keyed on Unit rather than currentIndex deliberately: this runs once
        // per opening of the sheet (the caller composes QueueContent only while
        // it's open, so re-opening re-runs it), and never again while the sheet
        // stays open. Keying on currentIndex instead would yank the list out
        // from under the user the moment a track advanced mid-browse.
        //
        // scrollToItem, not animateScrollToItem: the correct row should already
        // be there when the sheet settles, rather than the user watching it
        // travel past several hundred entries.
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) {
            if (queue.isNotEmpty() && currentIndex > 0) {
                listState.scrollToItem(currentIndex.coerceAtMost(queue.lastIndex))
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(queue, key = { index, item -> "$index:${item.id}" }) { index, item ->
                QueueRow(
                    item = item,
                    isCurrent = index == currentIndex,
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.lastIndex,
                    onClick = { onPlayIndex(index) },
                    onRemove = { onRemove(index) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: PlayableItem,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .background(if (isCurrent) WhiplashColors.surfaceElevated else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = GlassTokens.spaceSm, vertical = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassArtworkThumbnail(artworkUri = item.artworkUri, size = 40.dp)

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = GlassTokens.spaceSm),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) WhiplashColors.accent else WhiplashColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Section 57: subtle haptic feedback on queue reorder — the
        // reorder itself (move up/down) is the "meaningful interaction"
        // here, not the underlying icon tap, so it fires on every
        // successful move rather than being tied to a longer gesture.
        if (canMoveUp) {
            PlainIconButton(
                contentDescription = "Move up",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMoveUp()
                },
                size = 48.dp,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = WhiplashColors.textTertiary)
            }
        }
        if (canMoveDown) {
            PlainIconButton(
                contentDescription = "Move down",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMoveDown()
                },
                size = 48.dp,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = WhiplashColors.textTertiary)
            }
        }
        PlainIconButton(contentDescription = "Remove from queue", onClick = onRemove, size = 48.dp) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = WhiplashColors.textTertiary)
        }
    }
}
