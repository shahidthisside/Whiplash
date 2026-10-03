// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a LazyColumn, written against [LazyListState] only.
 *
 * Rows move in a local order while the finger is down; the real queue is
 * changed once, on drop, so the controller never sees a move per frame.
 * The dragged row's on-screen position is always recomputed as
 * "where it started + how far the finger went − where its slot is now",
 * so section headers appearing or the list auto-scrolling never make it
 * jump away from the finger.
 */
internal class QueueReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    /** Keys that may be swapped with each other (the upcoming rows). */
    private val movableKeys: () -> List<String>,
    /** Called when the dragged row passes another: move key [from] to [to]'s place. */
    private val onSwap: (fromKey: String, toKey: String) -> Unit,
) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    private var startOffset = 0
    private var dragged by mutableFloatStateOf(0f)

    fun start(key: String) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        startOffset = info.offset
        dragged = 0f
        draggingKey = key
    }

    fun dragBy(dy: Float) {
        if (draggingKey == null) return
        dragged += dy
        swapIfNeeded()
    }

    fun stop() {
        draggingKey = null
        dragged = 0f
    }

    /** Where the dragged row's top should be drawn, in viewport pixels. */
    private val visualTop: Float get() = startOffset + dragged

    /** Translation to apply to row [key] (0 unless it's the one being dragged). */
    fun translationFor(key: String): Float {
        if (key != draggingKey) return 0f
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return visualTop - info.offset
    }

    private fun swapIfNeeded() {
        val key = draggingKey ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val me = infos.firstOrNull { it.key == key } ?: return
        val center = visualTop + me.size / 2f
        val movable = movableKeys()
        val target = infos.firstOrNull {
            it.key != key && it.key in movable && center >= it.offset && center <= it.offset + it.size
        } ?: return
        // LazyColumn keeps its scroll anchored to the first visible item's
        // key; if that item is one of the two swapping, pin the index instead
        // or the whole list jumps.
        val anchor = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        onSwap(key, target.key as String)
        if (me.index == anchor || target.index == anchor) {
            scope.launch { listState.scrollToItem(anchor, anchorOffset) }
        }
    }

    /** Scrolls the list while the dragged row is held near the top or bottom edge. Run while dragging. */
    suspend fun autoScroll(edgePx: Float, maxStepPx: Float) {
        while (draggingKey != null) {
            val layout = listState.layoutInfo
            val me = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey }
            if (me != null) {
                val top = visualTop
                val bottom = top + me.size
                val start = layout.viewportStartOffset + edgePx
                val end = layout.viewportEndOffset - edgePx
                val step = when {
                    top < start -> -((start - top) / edgePx).coerceIn(0f, 1f) * maxStepPx
                    bottom > end -> ((bottom - end) / edgePx).coerceIn(0f, 1f) * maxStepPx
                    else -> 0f
                }
                if (step != 0f && listState.scrollBy(step) != 0f) swapIfNeeded()
            }
            withFrameNanos { }
        }
    }
}
