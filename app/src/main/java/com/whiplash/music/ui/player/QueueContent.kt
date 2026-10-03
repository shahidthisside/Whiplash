// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.common.isReducedMotionEnabled
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import kotlinx.coroutines.delay

/** One queue row with a key that survives reordering (same song twice gets #2). */
internal data class QueueEntry(val key: String, val item: PlayableItem, val queueIndex: Int, val fromAutoplay: Boolean)

/** What was last removed, for Undo. */
private data class RemovedEntry(val index: Int, val item: PlayableItem, val fromAutoplay: Boolean)

private enum class RowKind { PLAYED, UPCOMING }

/** Callbacks for a row's drag handle. */
private class RowDrag(val onStart: () -> Unit, val onDrag: (Float) -> Unit, val onEnd: () -> Unit)

/**
 * Queue sheet: songs already played (collapsed), the one playing now, then
 * what's up next, split into what you queued and what autoplay added.
 *
 * Drag a row's handle to reorder, swipe it left to remove (with Undo), or
 * long-press for Play next / Move / Remove. The same actions are offered to
 * TalkBack as custom actions, so nothing here needs a gesture.
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
    isPlaying: Boolean = false,
    autoplayIds: Set<String> = emptySet(),
    /** Artwork colour of the playing song, or null to use the theme accent. */
    tint: Color? = null,
    onRestore: (index: Int, item: PlayableItem, fromAutoplay: Boolean) -> Unit = { _, _, _ -> },
    onShuffleUpcoming: () -> Unit = {},
    /** A drag has started; the sheet uses it to go full height so there's room to move. */
    onReorderStart: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduceMotion = isReducedMotionEnabled()
    val accent = tint ?: WhiplashColors.accent

    val entries = remember(queue, autoplayIds) { buildQueueEntries(queue, autoplayIds) }
    val playing = entries.getOrNull(currentIndex)
    val played = if (currentIndex > 0) entries.subList(0, currentIndex) else emptyList()
    val upcomingFromQueue = if (currentIndex >= 0) entries.drop(currentIndex + 1) else entries

    // Local order while a drag is in progress (and until the queue catches up).
    var localOrder by remember { mutableStateOf<List<QueueEntry>?>(null) }
    val upcoming = localOrder ?: upcomingFromQueue
    val latestEntries by rememberUpdatedState(entries)
    val latestCurrent by rememberUpdatedState(currentIndex)
    val latestUpcoming by rememberUpdatedState(upcoming)

    val listState = rememberLazyListState()
    val reorder = remember(listState) {
        QueueReorderState(
            listState = listState,
            scope = scope,
            movableKeys = { latestUpcoming.map { it.key } },
            onSwap = { fromKey, toKey ->
                val list = latestUpcoming.toMutableList()
                val from = list.indexOfFirst { it.key == fromKey }
                val to = list.indexOfFirst { it.key == toKey }
                if (from >= 0 && to >= 0) {
                    list.add(to, list.removeAt(from))
                    localOrder = list
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
        )
    }
    LaunchedEffect(queue) { if (reorder.draggingKey == null) localOrder = null }
    val density = LocalDensity.current
    LaunchedEffect(reorder.draggingKey) {
        if (reorder.draggingKey != null) {
            reorder.autoScroll(edgePx = with(density) { 64.dp.toPx() }, maxStepPx = with(density) { 14.dp.toPx() })
        }
    }

    fun indexOfKey(key: String): Int? = latestEntries.firstOrNull { it.key == key }?.queueIndex

    fun startDrag(key: String) {
        localOrder = latestUpcoming
        reorder.start(key)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onReorderStart()
    }

    fun endDrag() {
        val key = reorder.draggingKey ?: return
        reorder.stop()
        val from = indexOfKey(key)
        val position = latestUpcoming.indexOfFirst { it.key == key }
        val to = latestCurrent + 1 + position
        if (from == null || position < 0 || from == to) localOrder = null else onMove(from, to)
    }

    var removed by remember { mutableStateOf<RemovedEntry?>(null) }
    LaunchedEffect(removed) {
        if (removed != null) {
            delay(UNDO_WINDOW_MS)
            removed = null
        }
    }
    fun remove(entry: QueueEntry) {
        val index = indexOfKey(entry.key) ?: return
        removed = RemovedEntry(index, entry.item, entry.fromAutoplay)
        onRemove(index)
    }
    fun moveTo(entry: QueueEntry, to: Int) {
        val from = indexOfKey(entry.key) ?: return
        val target = to.coerceIn(0, latestEntries.lastIndex)
        if (from != target) onMove(from, target)
    }

    var showPlayed by remember { mutableStateOf(false) }

    // Always as tall as the screen allows, so the sheet's "full" position
    // doesn't move when a song is removed or restored (it used to drop
    // back to half height).
    Box(modifier = Modifier.fillMaxHeight()) {
        Column {
            QueueHeader(
                upcoming = upcoming,
                canShuffle = upcoming.size > 1,
                onShuffleUpcoming = onShuffleUpcoming,
                canClear = queue.size > 1,
                onClear = onClear,
            )

            Box {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.spaceXl),
                ) {
                    if (played.isNotEmpty()) {
                        item(key = "h:played", contentType = "played-toggle") {
                            PlayedToggle(
                                count = played.size,
                                expanded = showPlayed,
                                onToggle = { showPlayed = !showPlayed },
                                modifier = Modifier.rowAnim(this, reduceMotion),
                            )
                        }
                        if (showPlayed) {
                            items(played, key = { it.key }, contentType = { "row" }) { entry ->
                                QueueRow(
                                    entry = entry,
                                    kind = RowKind.PLAYED,
                                    reduceMotion = reduceMotion,
                                    onClick = { indexOfKey(entry.key)?.let(onPlayIndex) },
                                    onRemove = { remove(entry) },
                                    menu = listOf(
                                        "Play" to { indexOfKey(entry.key)?.let(onPlayIndex); Unit },
                                        "Remove from queue" to { remove(entry) },
                                    ),
                                )
                            }
                        }
                    }

                    if (playing != null) {
                        item(key = "h:now", contentType = "label") {
                            SectionLabel("Now playing", modifier = Modifier.rowAnim(this, reduceMotion))
                        }
                        item(key = playing.key, contentType = "now") {
                            NowPlayingCard(
                                item = playing.item,
                                isPlaying = isPlaying,
                                accent = accent,
                                modifier = Modifier.rowAnim(this, reduceMotion),
                            )
                        }
                    }

                    item(key = "autoplay", contentType = "autoplay") {
                        AutoplayRow(
                            enabled = autoplayEnabled,
                            onToggle = { onToggleAutoplay(!autoplayEnabled) },
                            modifier = Modifier.rowAnim(this, reduceMotion),
                        )
                    }

                    if (upcoming.isEmpty()) {
                        item(key = "h:empty", contentType = "empty") {
                            EmptyUpNext(autoplayEnabled, Modifier.rowAnim(this, reduceMotion))
                        }
                    }

                    // A heading wherever the list switches between songs you
                    // queued and songs autoplay added, in play order.
                    var run = 0
                    upcoming.forEachIndexed { i, entry ->
                        if (i == 0 || entry.fromAutoplay != upcoming[i - 1].fromAutoplay) {
                            val auto = entry.fromAutoplay
                            val runId = run++
                            item(key = "h:run:$runId:$auto", contentType = "label") {
                                SectionLabel(
                                    text = if (auto) "From autoplay" else "Up next",
                                    modifier = Modifier.rowAnim(this, reduceMotion),
                                )
                            }
                        }
                        item(key = entry.key, contentType = "row") {
                            val dragging = reorder.draggingKey == entry.key
                            val position = latestUpcoming.indexOfFirst { it.key == entry.key }
                            QueueRow(
                                entry = entry,
                                kind = RowKind.UPCOMING,
                                reduceMotion = reduceMotion,
                                dragging = dragging,
                                drag = RowDrag(
                                    onStart = { startDrag(entry.key) },
                                    onDrag = reorder::dragBy,
                                    onEnd = { endDrag() },
                                ),
                                translationY = { reorder.translationFor(entry.key) },
                                onClick = { indexOfKey(entry.key)?.let(onPlayIndex) },
                                onRemove = { remove(entry) },
                                menu = buildList {
                                    if (position > 0) add("Play next" to { moveTo(entry, latestCurrent + 1) })
                                    if (position > 0) add("Move up" to { indexOfKey(entry.key)?.let { moveTo(entry, it - 1) }; Unit })
                                    if (position in 0 until latestUpcoming.lastIndex) {
                                        add("Move down" to { indexOfKey(entry.key)?.let { moveTo(entry, it + 1) }; Unit })
                                        add("Move to end" to { moveTo(entry, latestEntries.lastIndex) })
                                    }
                                    add("Remove from queue" to { remove(entry) })
                                },
                            )
                        }
                    }
                }

                UndoBar(
                    removed = removed,
                    onUndo = {
                        removed?.let { onRestore(it.index, it.item, it.fromAutoplay) }
                        removed = null
                    },
                    // Top, not bottom: at half height the bottom of the list is off screen.
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** Items animate in, out and into place, unless reduced motion is on. */
private fun Modifier.rowAnim(scope: LazyItemScope, reduceMotion: Boolean): Modifier =
    if (reduceMotion) this else with(scope) { this@rowAnim.animateItem() }

@Composable
private fun QueueHeader(
    upcoming: List<QueueEntry>,
    canShuffle: Boolean,
    onShuffleUpcoming: () -> Unit,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceXs, bottom = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge,
                color = WhiplashColors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = upNextSummary(upcoming),
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
            )
        }
        // A labelled chip, not a bare icon: the circular-arrows icon alone
        // read as Repeat.
        if (canShuffle) {
            PlainIconButton(contentDescription = "Shuffle up next", onClick = onShuffleUpcoming, size = 48.dp) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = WhiplashColors.textSecondary)
            }
        }
        if (canClear) {
            PlainIconButton(contentDescription = "Clear queue", onClick = onClear, size = 48.dp) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = WhiplashColors.textSecondary)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceMd, bottom = GlassTokens.spaceXs)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = TextUnit(1.2f, TextUnitType.Sp),
            ),
            color = WhiplashColors.textSecondary,
        )
    }
}

/** Autoplay on/off, as a plain row with a switch (same look as the switches in Settings). */
@Composable
private fun AutoplayRow(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .padding(top = GlassTokens.spaceSm)
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .toggleable(value = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceSm, bottom = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Autoplay", style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textPrimary)
            Text(
                "Add similar songs when the queue runs out",
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WhiplashColors.onAccent,
                checkedTrackColor = WhiplashColors.accent,
                uncheckedThumbColor = WhiplashColors.textSecondary,
                uncheckedTrackColor = WhiplashColors.surfaceGlass,
                uncheckedBorderColor = WhiplashColors.glassBorderStrong,
            ),
        )
    }
}

/** "PLAYED · 2" heading that shows or hides the songs already played. */
@Composable
private fun PlayedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(GlassTokens.animRegular), label = "playedChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .clickable(onClickLabel = if (expanded) "Hide played songs" else "Show played songs", onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (expanded) "Shown" else "Hidden"
            }
            .padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceSm, bottom = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PLAYED",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = TextUnit(1.2f, TextUnitType.Sp),
            ),
            color = WhiplashColors.textSecondary,
        )
        Text(
            text = "  ·  $count",
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textTertiary,
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = WhiplashColors.textTertiary,
            modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(rotation),
        )
    }
}

@Composable
private fun NowPlayingCard(item: PlayableItem, isPlaying: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(WhiplashRadius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .semantics(mergeDescendants = true) {
                stateDescription = if (isPlaying) "Playing" else "Paused"
            }
            .padding(GlassTokens.spaceSm + GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassArtworkThumbnail(artworkUri = item.artworkUri, size = 60.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = GlassTokens.spaceMd)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayingBars(playing = isPlaying, color = accent, modifier = Modifier.padding(end = GlassTokens.spaceXs))
    }
}

/** Three little level bars: bouncing while playing, still when paused or with reduced motion. */
@Composable
private fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val still = floatArrayOf(0.45f, 0.8f, 0.6f)
    val heights = if (playing && !isReducedMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "playingBars")
        List(3) { i ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 380 + i * 140, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            ).value
        }
    } else {
        still.toList()
    }
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width / 5f
        heights.forEachIndexed { i, fraction ->
            val h = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2 * w, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.QueueRow(
    entry: QueueEntry,
    kind: RowKind,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    dragging: Boolean = false,
    drag: RowDrag? = null,
    translationY: () -> Float = { 0f },
) {
    val item = entry.item
    var menuOpen by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    val latestRemove by rememberUpdatedState(onRemove)
    // Plain remember, not the saveable rememberSwipeToDismissBoxState: the
    // saved "dismissed" value came back with a row restored by Undo (same
    // key) and removed it again straight away.
    val density = LocalDensity.current
    val swipe = remember {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            positionalThreshold = { distance -> distance * 0.4f },
            confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !gone) {
                gone = true
                latestRemove()
                true
            } else {
                false
            }
        },
        )
    }
    val elevation by animateFloatAsState(if (dragging) 1f else 0f, tween(GlassTokens.animFast), label = "dragLift")
    val shape = RoundedCornerShape(WhiplashRadius.small)
    val itemModifier = when {
        reduceMotion -> Modifier
        dragging -> Modifier.animateItem(placementSpec = null)
        else -> Modifier.animateItem()
    }

    Box(
        modifier = itemModifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                this.translationY = translationY()
                val s = 1f + 0.02f * elevation
                scaleX = s
                scaleY = s
            }
            .semantics {
                customActions = menu.map { (label, action) -> CustomAccessibilityAction(label) { action(); true } }
            },
    ) {
        SwipeToDismissBox(
            state = swipe,
            enableDismissFromStartToEnd = false,
            gesturesEnabled = !dragging,
            backgroundContent = {
                // Only while a swipe is under way; the row itself is see-through.
                if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) SwipeRemoveBackground(shape)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow((8 * elevation).dp, shape, clip = false)
                    .clip(shape)
                    .background(
                        // Opaque while dragging or swiping so the row doesn't
                        // show what's underneath it.
                        if (dragging || swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                            WhiplashColors.surfaceElevated
                        } else {
                            WhiplashColors.surfaceSheet.copy(alpha = 0f)
                        },
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = "More actions",
                    )
                    .alpha(if (kind == RowKind.PLAYED) 0.55f else 1f)
                    .padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceXs, bottom = GlassTokens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassArtworkThumbnail(artworkUri = item.artworkUri, size = 48.dp)
                Column(modifier = Modifier.weight(1f).padding(horizontal = GlassTokens.spaceMd)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(item.artist.takeIf { it.isNotBlank() }, formatTrackTime(item.durationMs)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (kind == RowKind.UPCOMING) {
                    // The handle itself is hidden from TalkBack; reordering is
                    // offered there through the row's custom actions instead.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (drag == null) Modifier else Modifier.pointerInput(entry.key) {
                                    detectVerticalDragGestures(
                                        onDragStart = { drag.onStart() },
                                        onDragEnd = { drag.onEnd() },
                                        onDragCancel = { drag.onEnd() },
                                        onVerticalDrag = { change, dy ->
                                            change.consume()
                                            drag.onDrag(dy)
                                        },
                                    )
                                },
                            )
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = if (dragging) WhiplashColors.accent else WhiplashColors.textTertiary,
                        )
                    }
                } else {
                    Spacer(Modifier.width(GlassTokens.spaceSm))
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = WhiplashColors.surfaceElevated,
            shape = RoundedCornerShape(WhiplashRadius.medium),
        ) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, color = WhiplashColors.textPrimary) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeRemoveBackground(shape: RoundedCornerShape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(WhiplashColors.error.copy(alpha = 0.85f))
            .padding(end = GlassTokens.spaceLg),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun EmptyUpNext(autoplayEnabled: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceLg, horizontal = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = null,
            tint = WhiplashColors.textTertiary,
            modifier = Modifier.padding(end = GlassTokens.spaceMd),
        )
        Column {
            Text("Nothing up next", style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textPrimary)
            Text(
                text = if (autoplayEnabled) "Autoplay will add similar songs before this one ends." else "Turn on Autoplay to keep the music going.",
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
            )
        }
    }
}

@Composable
private fun UndoBar(removed: RemovedEntry?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    // Keep the last value while animating out.
    var shown by remember { mutableStateOf(removed) }
    if (removed != null) shown = removed
    AnimatedVisibility(
        visible = removed != null,
        enter = fadeIn(tween(GlassTokens.animRegular)) + slideInVertically(tween(GlassTokens.animRegular)) { -it / 2 },
        exit = fadeOut(tween(GlassTokens.animFast)) + slideOutVertically(tween(GlassTokens.animFast)) { -it / 2 },
        modifier = modifier.padding(top = GlassTokens.spaceXs),
    ) {
        val shape = RoundedCornerShape(WhiplashRadius.pill)
        Row(
            modifier = Modifier
                .shadow(GlassTokens.elevationElevated, shape, clip = false)
                .clip(shape)
                .background(WhiplashColors.surfaceElevated)
                .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, shape)
                .padding(start = GlassTokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Removed “${shown?.item?.title.orEmpty()}”",
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clickable(onClick = onUndo, onClickLabel = "Undo remove")
                    .padding(horizontal = GlassTokens.spaceMd),
                contentAlignment = Alignment.Center,
            ) {
                Text("Undo", style = MaterialTheme.typography.labelLarge, color = WhiplashColors.accent)
            }
        }
    }
}

/** Keys that stay with a song through reordering; a second copy of the same song gets "#2". */
internal fun buildQueueEntries(queue: List<PlayableItem>, autoplayIds: Set<String>): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return queue.mapIndexed { i, item ->
        val base = "${item.source}:${item.id}"
        val n = seen.merge(base, 1, Int::plus) ?: 1
        QueueEntry("$base#$n", item, i, item.id in autoplayIds)
    }
}

internal fun upNextSummary(upcoming: List<QueueEntry>): String {
    if (upcoming.isEmpty()) return "Nothing up next"
    val songs = if (upcoming.size == 1) "1 song up next" else "${upcoming.size} songs up next"
    val totalMs = upcoming.sumOf { it.item.durationMs.coerceAtLeast(0L) }
    val minutes = totalMs / 60_000L
    return when {
        minutes >= 60 -> "$songs · ${minutes / 60} h ${minutes % 60} min"
        minutes > 0 -> "$songs · $minutes min"
        else -> songs
    }
}

internal fun formatTrackTime(ms: Long): String? {
    if (ms <= 0) return null
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private const val UNDO_WINDOW_MS = 6_000L
