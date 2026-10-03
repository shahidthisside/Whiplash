// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import com.whiplash.music.domain.model.wordHighlights
import com.whiplash.music.domain.model.lineEmphasis
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricsResult
import com.whiplash.music.domain.model.LYRIC_OFFSET_STEP_MS
import com.whiplash.music.domain.model.formatLyricOffset
import com.whiplash.music.domain.model.lyricPositionWithOffset
import com.whiplash.music.domain.model.seekTargetForLyricLine
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * The lyrics themselves, filling whatever space [LyricsScreen] gives them:
 * synced lines with the sung one large, bright and held a third of the way
 * down; plain text as a scrollable page; or an honest loading / not-found
 * state. Never fabricates content.
 */
@Composable
fun LyricsBody(
    result: LyricsResult?,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    offsetMs: Long = 0L,
    blurUnfocused: Boolean = false,
) {
    // Keep the screen on only while lyrics are on screen and the song is
    // playing: the display otherwise times out mid-verse while someone is
    // reading along. Closing the lyrics (or pausing) hands it back at once.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        when (result) {
            null -> LoadingState()
            is LyricsResult.Synced -> SyncedLyricsView(result.lines, positionMs, isPlaying, offsetMs, onSeekTo, blurUnfocused)
            is LyricsResult.Plain -> PlainLyricsView(result.text)
            is LyricsResult.Error -> MessageState(
                icon = Icons.Filled.MusicOff,
                title = "Couldn't load lyrics",
                // The raw error (host names, IP addresses) means nothing to a
                // listener; the lookup is tried again next time.
                subtitle = "Check your connection and try again.",
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
        modifier = Modifier.fillMaxSize().padding(horizontal = LYRICS_SIDE_PADDING),
        contentAlignment = Alignment.TopCenter,
    ) {
        com.whiplash.music.ui.theme.LyricsSkeleton(Modifier.padding(top = 48.dp))
    }
}

@Composable
private fun MessageState(icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = LYRICS_SIDE_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = WhiplashColors.textSecondary, modifier = Modifier.size(40.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = WhiplashColors.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = WhiplashColors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlainLyricsView(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LYRICS_SIDE_PADDING, vertical = 48.dp),
    ) {
        Text(
            text = text,
            style = LYRIC_TEXT_STYLE,
            fontWeight = FontWeight.Bold,
            color = WhiplashColors.textPrimary,
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
    // Between player ticks the position moves at the playback speed, not
    // wall-clock speed; at 1.5x the lyrics otherwise lag and jump forward.
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.whiplash.music.WhiplashApplication
    val speed by app.settingsRepository.playbackSpeed.collectAsState(initial = 1f)

    LaunchedEffect(positionMs) {
        lastTickWallClock = System.currentTimeMillis()
        lastTickPositionMs = positionMs
        smoothedMs = positionMs
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                val elapsed = System.currentTimeMillis() - lastTickWallClock
                smoothedMs = interpolatedPositionMs(lastTickPositionMs, elapsed, speed)
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
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    blurUnfocused: Boolean,
) {
    // The per-track offset shifts the position the lines are matched
    // against, not the lines themselves, so the list keeps stable keys and
    // does not recompose every row when the listener nudges the timing.
    val smoothedMs = lyricPositionWithOffset(rememberSmoothedPositionMs(positionMs, isPlaying), offsetMs)
    // Rows read the live position through this holder, so only the active
    // row (the one drawing a word highlight) redraws every frame.
    val positionHolder = androidx.compose.runtime.rememberUpdatedState(smoothedMs)
    val listState = rememberLazyListState()
    var userScrollSuspendUntilMs by remember { mutableLongStateOf(0L) }
    // True while the listener is scrolling through the lyrics themselves:
    // blur is lifted so every line is readable.
    var userBrowsing by remember { mutableStateOf(false) }
    val reduceMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()

    // -1 (no active line yet) is a real, distinct state from "line 0 is
    // active" — e.g. during a song's instrumental intro before the first
    // lyric timestamp. Coercing it up to 0 would wrongly highlight the
    // first line before playback has actually reached it.
    val activeIndex = remember(lines, smoothedMs) {
        lines.indexOfLast { it.timestampMs <= smoothedMs }
    }

    // Only a real finger drag counts as the listener browsing. (Watching
    // isScrollInProgress also caught this view's own auto-scroll, so every
    // automatic jump paused auto-scroll for the next few seconds and lifted
    // the blur.)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) {
            userBrowsing = true
            userScrollSuspendUntilMs = Long.MAX_VALUE
        } else if (userBrowsing) {
            // Keep the listener's place for a few seconds after they let go.
            userScrollSuspendUntilMs = System.currentTimeMillis() + MANUAL_SCROLL_SUSPEND_MS
            kotlinx.coroutines.delay(MANUAL_SCROLL_SUSPEND_MS)
            userBrowsing = false
        }
    }

    // Also re-runs when browsing ends, so the view returns to the sung line.
    // The sung line is held about a third of the way down, so what's coming
    // next is always in view.
    LaunchedEffect(activeIndex, userBrowsing) {
        if (userBrowsing) return@LaunchedEffect
        val suspended = System.currentTimeMillis() < userScrollSuspendUntilMs
        if (!suspended) {
            val target = activeIndex.coerceAtLeast(0)
            // Measure where the line actually is and scroll by the difference;
            // bring it into view first if it's off screen.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) listState.scrollToItem(target)
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            // Offsets are measured from inside the top padding, which is
            // exactly the reading position, so the line belongs at offset 0.
            val delta = info.offset.toFloat()
            if (reduceMotion) {
                listState.scrollBy(delta)
            } else {
                listState.animateScrollBy(delta, androidx.compose.animation.core.tween(ACTIVE_LINE_SCROLL_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val topRoom = maxHeight * ACTIVE_LINE_POSITION
    val bottomRoom = maxHeight * (1f - ACTIVE_LINE_POSITION)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room above the first line and below the last, so either can be
        // brought to the reading position.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = LYRICS_SIDE_PADDING,
            end = LYRICS_SIDE_PADDING,
            top = topRoom,
            bottom = bottomRoom,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timestampMs}" }) { index, line ->
            // During the intro (no active line) everything counts as upcoming.
            val distance = if (activeIndex < 0) index + 1 else index - activeIndex
            LyricLineRow(
                line = line,
                distance = distance,
                positionMs = positionHolder,
                blur = blurUnfocused && !userBrowsing,
                reduceMotion = reduceMotion,
                onClick = { onSeekTo(seekTargetForLyricLine(line.timestampMs, offsetMs)) },
            )
        }
    }
    }
}

/**
 * One lyric line.
 *
 * - Kinetic transition: the line springs up to full size as it becomes
 *   active (a slight overshoot), and its neighbours fade by distance.
 * - Word highlight: on word-synced lyrics the active line fills word by
 *   word; a held word glows ("bloom") while it is sung.
 * - Optional blur on lines away from the active one (Android 12+).
 * Reduce animations turns every transition into an instant change.
 */
@Composable
private fun LyricLineRow(
    line: LyricLine,
    distance: Int,
    positionMs: androidx.compose.runtime.State<Long>,
    blur: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val isActive = distance == 0
    val emphasis = lineEmphasis(distance)
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "lyricLineScale",
    )
    val alpha by animateFloatAsState(
        targetValue = emphasis.alpha,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(GlassTokens.animRegular),
        label = "lyricLineAlpha",
    )
    val blurDp by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (blur) (emphasis.blurSteps * BLUR_STEP_DP).dp else 0.dp,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(GlassTokens.animRegular),
        label = "lyricLineBlur",
    )
    val interactionSource = remember { MutableInteractionSource() }

    if (line.text.isBlank()) {
        // An empty LRC line marks an instrumental gap — a small breathing
        // space rather than an empty, confusing row.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 12.dp))
        return
    }

    val baseColor = WhiplashColors.textPrimary
    val dimColor = WhiplashColors.textSecondary
    val wordSynced = isActive && line.words.isNotEmpty()
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            // Scale from the start edge so wrapped lines don't drift sideways.
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                if (isRtl) 1f else 0f, 0.5f,
            )
        }
        .then(
            if (blurDp > 0.dp) Modifier.blur(blurDp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
            else Modifier,
        )
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

    if (wordSynced) {
        val text = wordHighlightText(line, positionMs.value, baseColor, dimColor.copy(alpha = 0.7f))
        Text(
            text = text,
            style = LYRIC_TEXT_STYLE,
            fontWeight = FontWeight.ExtraBold,
            modifier = modifier,
        )
    } else {
        Text(
            text = line.text,
            style = LYRIC_TEXT_STYLE,
            fontWeight = FontWeight.ExtraBold,
            // One colour; the distance-based alpha does the dimming, so
            // lines read as white-on-colour rather than grey.
            color = baseColor,
            modifier = modifier,
        )
    }
}

/** Builds the active line with each word coloured by how far it has been sung, plus the held-word glow. */
private fun wordHighlightText(
    line: LyricLine,
    positionMs: Long,
    sung: androidx.compose.ui.graphics.Color,
    unsung: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.text.AnnotatedString {
    val states = wordHighlights(line, positionMs)
    return androidx.compose.ui.text.buildAnnotatedString {
        line.words.forEachIndexed { i, word ->
            val h = states[i]
            val color = androidx.compose.ui.graphics.lerp(unsung, sung, h.fill)
            val shadow = if (h.bloom > 0f) {
                androidx.compose.ui.graphics.Shadow(
                    color = sung.copy(alpha = 0.75f * h.bloom),
                    blurRadius = 24f * h.bloom,
                )
            } else {
                null
            }
            withStyle(androidx.compose.ui.text.SpanStyle(color = color, shadow = shadow)) {
                append(word.text)
            }
        }
    }
}

/**
 * On/off switch for the player's lyric line, drawn as a toggle icon button:
 * accent and filled when the line shows, dimmed with a strike-through icon
 * when it's hidden. Announces its state to TalkBack as a switch.
 */
@Composable
internal fun LyricStripToggle(on: Boolean, onSet: (Boolean) -> Unit, enabled: Boolean) {
    androidx.compose.material3.IconToggleButton(
        checked = on,
        onCheckedChange = { checked ->
            onSet(checked)
            com.whiplash.music.ui.common.ToastController.show(
                if (checked) "Lyric line on player shown" else "Lyric line on player hidden",
            )
        },
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = if (on) "Lyric line on player: on" else "Lyric line on player: off"
        },
    ) {
        Icon(
            imageVector = if (on) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
            contentDescription = null,
            tint = if (on) WhiplashColors.accent else WhiplashColors.textSecondary,
        )
    }
}

/** Blur per line of distance from the active one, in dp. */
private const val BLUR_STEP_DP = 1.2f

/**
 * Compact "− +0.5s +" timing adjuster in the lyrics header. Each button
 * moves the current track's lyrics by [LYRIC_OFFSET_STEP_MS]; tapping the
 * value resets it. "+" makes lyrics appear earlier, which is the fix for
 * the common case of an LRC file that lags behind the vocals.
 */
@Composable
internal fun LyricOffsetControl(
    offsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    enabled: Boolean = true,
) {
    val label = formatLyricOffset(offsetMs)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAdjust(-LYRIC_OFFSET_STEP_MS) }, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Show lyrics later",
                tint = WhiplashColors.textSecondary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (offsetMs == 0L) WhiplashColors.textSecondary else WhiplashColors.textPrimary,
            modifier = Modifier
                .widthIn(min = 52.dp)
                .clickable(
                    enabled = enabled && offsetMs != 0L,
                    onClickLabel = "Reset lyrics timing",
                    onClick = onReset,
                )
                .semantics { contentDescription = "Lyrics timing $label" }
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onAdjust(LYRIC_OFFSET_STEP_MS) }, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Show lyrics earlier",
                tint = WhiplashColors.textSecondary,
            )
        }
    }
}

private const val MANUAL_SCROLL_SUSPEND_MS = 4_000L

/** Where the sung line is held, as a fraction of the lyrics' height from the top. */
private const val ACTIVE_LINE_POSITION = 0.3f

/** How long the lyrics take to glide to the next line. */
private const val ACTIVE_LINE_SCROLL_MS = 500

/** Side margin of the lyrics, wide enough to read comfortably. */
internal val LYRICS_SIDE_PADDING = 28.dp

/** Every lyric line, plain or synced: big and tight, like the large-type players. */
private val LYRIC_TEXT_STYLE = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp)

// Whiplash internals. Owner: github.com/shahidthisside
/**
 * One-line "current lyric" strip shown above the full player's scrubber
 * (2.5). Only appears for synced lyrics, since plain text has no timing to
 * follow. The line uses the same per-track offset as the lyrics sheet and
 * crossfades as it changes; tapping it opens the full lyrics sheet.
 *
 * Its height is reserved as soon as synced lyrics exist (a "♪" during
 * instrumental parts), so the controls below never jump as lines come
 * and go.
 */
@Composable
internal fun CurrentLyricStrip(
    result: LyricsResult?,
    positionMs: Long,
    offsetMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = (result as? LyricsResult.Synced)?.lines ?: return
    val position = lyricPositionWithOffset(positionMs, offsetMs)
    val text = remember(lines, position) {
        lines.lastOrNull { it.timestampMs <= position }?.text?.takeIf { it.isNotBlank() } ?: "\u266A"
    }
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(tween(GlassTokens.animRegular)) +
                androidx.compose.animation.slideInVertically(tween(GlassTokens.animRegular)) { it / 3 })
                .togetherWith(androidx.compose.animation.fadeOut(tween(GlassTokens.animFast)))
        },
        label = "currentLyricStrip",
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Open lyrics", onClick = onClick)
            .semantics { contentDescription = "Current lyric: $text. Opens lyrics" },
        contentAlignment = Alignment.CenterStart,
    ) { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = WhiplashColors.textPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Where the song is [elapsedMs] of real time after [tickPositionMs], at playback [speed]. */
internal fun interpolatedPositionMs(tickPositionMs: Long, elapsedMs: Long, speed: Float): Long =
    tickPositionMs + (elapsedMs.coerceAtLeast(0L) * speed).toLong()
