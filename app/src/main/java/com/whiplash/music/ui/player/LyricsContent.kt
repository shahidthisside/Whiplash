package com.whiplash.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricsResult
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Lyrics sheet (CLAUDE.md section 20), styled to match the premium synced-
 * lyrics experience of YouTube Music/Spotify/Apple Music: the active line
 * is large, bold and full-opacity; inactive lines are smaller and dimmed;
 * the transition between them is an animated scale+color crossfade, not an
 * instant swap. Never fabricates content — synced/plain/unavailable are all
 * real outcomes from the lyrics provider.
 */
@Composable
fun LyricsContent(
    result: LyricsResult?,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(top = GlassTokens.spaceMd),
    ) {
        Text(
            text = "Lyrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(horizontal = GlassTokens.spaceMd),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))

        when (result) {
            null -> LoadingState()
            is LyricsResult.Synced -> SyncedLyricsView(result.lines, positionMs, isPlaying, onSeekTo)
            is LyricsResult.Plain -> PlainLyricsView(result.text)
            is LyricsResult.Error -> MessageState(
                icon = Icons.Filled.MusicOff,
                title = "Couldn't load lyrics",
                subtitle = result.message,
            )
            LyricsResult.Unavailable -> MessageState(
                icon = Icons.Filled.MusicOff,
                title = "Lyrics unavailable",
                subtitle = "No lyrics were found for this song.",
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = WhiplashColors.accent)
    }
}

@Composable
private fun MessageState(icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = WhiplashColors.textSecondary)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
        }
    }
}

@Composable
private fun PlainLyricsView(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(horizontal = GlassTokens.spaceMd)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = WhiplashColors.textPrimary,
            lineHeight = 34.sp,
        )
    }
}

/**
 * Continuously advances a "smoothed" position between the underlying
 * player's coarse ~500ms position ticks, using wall-clock elapsed time —
 * the same technique premium players use so the highlighted line tracks
 * the audio in real time instead of visibly jumping/lagging every half
 * second. Resyncs to the real [positionMs] on every tick to prevent drift.
 */
@Composable
private fun rememberSmoothedPositionMs(positionMs: Long, isPlaying: Boolean): Long {
    var smoothedMs by remember { mutableLongStateOf(positionMs) }
    var lastTickWallClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastTickPositionMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(positionMs) {
        lastTickWallClock = System.currentTimeMillis()
        lastTickPositionMs = positionMs
        smoothedMs = positionMs
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameTimeMs ->
                val elapsed = System.currentTimeMillis() - lastTickWallClock
                smoothedMs = lastTickPositionMs + elapsed
            }
        }
    }

    return smoothedMs
}

/**
 * Auto-scrolls to and highlights the line whose timestamp has most
 * recently passed, tracking a locally interpolated position for
 * frame-accurate sync (see [rememberSmoothedPositionMs]) rather than the
 * underlying ~500ms-granularity player position directly. Matches the
 * premium lyrics convention (YouTube Music/Spotify/Apple Music): the
 * active line is large, bold, full-opacity; others are smaller and dimmed,
 * with an animated scale+color transition between states. A manual scroll
 * suspends auto-scroll briefly so the user can read ahead/back freely.
 */
@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    val smoothedMs = rememberSmoothedPositionMs(positionMs, isPlaying)
    val listState = rememberLazyListState()
    var userScrollSuspendUntilMs by remember { mutableLongStateOf(0L) }

    // -1 (no active line yet) is a real, distinct state from "line 0 is
    // active" — e.g. during a song's instrumental intro before the first
    // lyric timestamp. Coercing it up to 0 would wrongly highlight the
    // first line before playback has actually reached it.
    val activeIndex = remember(lines, smoothedMs) {
        lines.indexOfLast { it.timestampMs <= smoothedMs }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrollSuspendUntilMs = System.currentTimeMillis() + MANUAL_SCROLL_SUSPEND_MS
        }
    }

    LaunchedEffect(activeIndex) {
        val suspended = System.currentTimeMillis() < userScrollSuspendUntilMs
        if (!suspended) {
            listState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = GlassTokens.spaceMd,
            vertical = GlassTokens.spaceXl,
        ),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timestampMs}" }) { index, line ->
            LyricLineRow(
                text = line.text,
                isActive = index == activeIndex,
                onClick = { onSeekTo(line.timestampMs) },
            )
        }
    }
}

@Composable
private fun LyricLineRow(text: String, isActive: Boolean, onClick: () -> Unit) {
    // Premium lyrics convention (YouTube Music/Spotify/Apple Music): the
    // active line is visually dominant — larger, bold, fully opaque —
    // while every other line recedes (smaller, dimmed). The transition
    // between states is an animated scale + color crossfade rather than
    // an instant style swap, which is what makes the highlight feel like
    // it's genuinely "following" the vocals instead of just ticking a
    // label on and off.
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.94f,
        animationSpec = tween(GlassTokens.animRegular),
        label = "lyricLineScale",
    )
    val color by animateColorAsState(
        targetValue = if (isActive) WhiplashColors.textPrimary else WhiplashColors.textSecondary.copy(alpha = 0.55f),
        animationSpec = tween(GlassTokens.animRegular),
        label = "lyricLineColor",
    )
    val interactionSource = remember { MutableInteractionSource() }

    if (text.isBlank()) {
        // An empty LRC line marks an instrumental gap — a small breathing
        // space rather than an empty, confusing row.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
        return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
        color = color,
        lineHeight = 32.sp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

private const val MANUAL_SCROLL_SUSPEND_MS = 4_000L
