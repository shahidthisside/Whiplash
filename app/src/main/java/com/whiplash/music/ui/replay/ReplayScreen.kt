package com.whiplash.music.ui.replay

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.ReplayArtistStat
import com.whiplash.music.domain.model.ReplaySummary
import com.whiplash.music.domain.model.ReplayTrackStat
import com.whiplash.music.domain.model.replayMonthLabel
import com.whiplash.music.domain.model.replayMonthName
import com.whiplash.music.ui.common.isReducedMotionEnabled
import com.whiplash.music.ui.player.PlayerColors
import com.whiplash.music.ui.player.PlayerMeshBackdrop
import com.whiplash.music.ui.player.animatedPlayerColors
import com.whiplash.music.ui.player.rememberArtworkPalette
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import kotlin.math.absoluteValue

/** How long each story page stays up before moving on by itself. */
private const val STORY_PAGE_MS = 7_000

/** Poster size in dp; the shared image is this times the screen density. */
private val POSTER_WIDTH = 360.dp
private val POSTER_HEIGHT = 640.dp

private enum class ReplayPage { INTRO, MINUTES, TOP_ARTIST, TOP_SONGS, TOP_ARTISTS, POSTER }

private fun pagesFor(summary: ReplaySummary?): List<ReplayPage> = when {
    summary == null || summary.isEmpty -> emptyList()
    summary.topArtists.isEmpty() -> listOf(ReplayPage.INTRO, ReplayPage.MINUTES, ReplayPage.TOP_SONGS, ReplayPage.POSTER)
    else -> ReplayPage.entries
}

/**
 * 4.7 Monthly Replay: a full-screen, swipeable story of one month's
 * listening, ending in a poster you can share as an image.
 *
 * Pages move on by themselves (tap the right side to skip ahead, the left
 * to go back, hold to pause). They don't auto-advance while TalkBack is on,
 * so nothing moves away mid-read. The background takes its colours from the
 * artwork each page is about.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(
    viewModel: ReplayViewModel,
    onClose: () -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val summary by viewModel.summary.collectAsState()
    val months by viewModel.months.collectAsState()
    val currentMonth by viewModel.currentMonthKey.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val reduceMotion = isReducedMotionEnabled()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val touchExploration = remember {
        (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true
    }

    val pages = pagesFor(summary)
    // Month switches rebuild the pager, so it starts again from page one.
    val pagerState = androidx.compose.runtime.key(summary?.monthKey) {
        rememberPagerState { pages.size.coerceAtLeast(1) }
    }
    var showMonthPicker by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val progress = remember(summary?.monthKey) { Animatable(0f) }
    var progressPage by remember(summary?.monthKey) { mutableStateOf(0) }

    val playTracks: (Int) -> Unit = { index ->
        val tracks = summary?.topTracks.orEmpty()
        scope.launch {
            val items = viewModel.playable(tracks)
            if (items.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayQueue(items, index.coerceIn(0, items.lastIndex))
            }
        }
    }

    // Auto-advance, paused while held, while dragging, while the month
    // picker is open, and on the last page.
    // Timing follows the settled page: currentPage flips halfway through a
    // turn, and restarting the timer there used to cancel the turn itself,
    // leaving two pages half on screen.
    val page = pagerState.settledPage
    // Only a finger dragging pauses it — not the pager's own animated turn,
    // which would cancel itself mid-scroll.
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val paused = held || dragging || showMonthPicker || touchExploration
    LaunchedEffect(page, paused, pages.size) {
        if (progressPage != page) {
            progressPage = page
            progress.snapTo(0f)
        }
        if (pages.isEmpty()) return@LaunchedEffect
        if (page >= pages.lastIndex) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * STORY_PAGE_MS).toInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // Outside this effect's scope, so the turn always finishes.
        scope.launch { pagerState.animateScrollToPage(page + 1, animationSpec = tween(520, easing = FastOutSlowInEasing)) }
    }

    // Colours follow what the page is about.
    val s = summary
    val focusArt = when (pages.getOrNull(page)) {
        ReplayPage.TOP_ARTIST, ReplayPage.TOP_ARTISTS -> s?.topArtists?.firstOrNull()?.artworkUrl
        else -> s?.topTracks?.firstOrNull()?.artworkUrl
    }
    val palette by rememberArtworkPalette(focusArt, enabled = true)
    val colors = animatedPlayerColors(palette)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiplashColors.background)
            // Swallows touches so nothing underneath reacts.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        PlayerMeshBackdrop(colors = colors, animate = !reduceMotion)
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (pages.isNotEmpty()) {
                StoryProgress(count = pages.size, current = page, progress = { progress.value })
            }
            ReplayTopBar(
                month = selectedMonth,
                canPickMonth = months.size > 1,
                onPickMonth = { showMonthPicker = true },
                onClose = onClose,
            )
            if (s == null || pages.isEmpty()) {
                ReplayEmpty(modifier = Modifier.weight(1f))
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(pages.size) {
                            detectTapGestures(
                                onPress = {
                                    held = true
                                    tryAwaitRelease()
                                    held = false
                                },
                                onTap = { offset ->
                                    // Read live: this lambda outlives the composition it was made in.
                                    val from = pagerState.currentPage
                                    val target = if (offset.x < size.width * 0.3f) from - 1 else from + 1
                                    if (target in pages.indices) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(target, animationSpec = tween(420, easing = FastOutSlowInEasing))
                                        }
                                    }
                                },
                            )
                        },
                ) { index ->
                    val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Soft depth: the page leaving shrinks and fades a little.
                                val f = offset.absoluteValue.coerceIn(0f, 1f)
                                alpha = 1f - f * 0.6f
                                val scale = 1f - f * 0.08f
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        val active = index == pagerState.currentPage
                        when (pages[index]) {
                            ReplayPage.INTRO -> IntroPage(s, isCurrentMonth = s.monthKey == currentMonth)
                            ReplayPage.MINUTES -> MinutesPage(s, active = active, animate = !reduceMotion)
                            ReplayPage.TOP_ARTIST -> TopArtistPage(s.topArtists.first(), colors)
                            ReplayPage.TOP_SONGS -> TopSongsPage(s.topTracks, colors, onPlay = playTracks)
                            ReplayPage.TOP_ARTISTS -> TopArtistsPage(s.topArtists)
                            ReplayPage.POSTER -> PosterPage(s, colors, onPlay = { playTracks(0) })
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        GlassSheet(onDismissRequest = { showMonthPicker = false }) {
            Text(
                text = "Choose a month",
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
                modifier = Modifier.padding(bottom = GlassTokens.spaceSm).semantics { heading() },
            )
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                months.forEach { month ->
                    val selected = month == selectedMonth
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(WhiplashRadius.small))
                            .clickable(role = Role.RadioButton) {
                                showMonthPicker = false
                                viewModel.selectMonth(month)
                            }
                            .padding(horizontal = GlassTokens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = replayMonthLabel(month) + if (month == currentMonth) " (so far)" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = WhiplashColors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryProgress(count: Int, current: Int, progress: () -> Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.spaceMd)
            .padding(top = GlassTokens.spaceSm)
            .semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Read in the draw phase so the bar fills without recomposing.
                            val fill = when {
                                i < current -> 1f
                                i == current -> progress()
                                else -> 0f
                            }
                            scaleX = fill
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun ReplayTopBar(month: String?, canPickMonth: Boolean, onPickMonth: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GlassTokens.spaceMd, end = GlassTokens.spaceXs, top = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Insights, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(GlassTokens.spaceSm))
        Text("Replay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(GlassTokens.spaceSm))
        if (month != null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (canPickMonth) {
                            Modifier
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable(onClickLabel = "Choose a month", onClick = onPickMonth)
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = replayMonthLabel(month),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
                if (canPickMonth) Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        PlainIconButton(contentDescription = "Close Replay", onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }
    }
}

// ── Pages ────────────────────────────────────────────────────────────────

/** Centred page body that scrolls if a short screen or large font needs it. */
@Composable
private fun PageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun Kicker(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 2.sp,
        color = Color.White.copy(alpha = 0.75f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun IntroPage(s: ReplaySummary, isCurrentMonth: Boolean) {
    PageColumn {
        CoverFan(s.topTracks.take(3).map { it.artworkUrl })
        Spacer(Modifier.height(GlassTokens.spaceXl))
        Kicker("Your Replay")
        Spacer(Modifier.height(GlassTokens.spaceSm))
        Text(
            text = replayMonthName(s.monthKey),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(GlassTokens.spaceMd))
        Text(
            text = if (isCurrentMonth) {
                "Your month in music so far. It keeps counting until the month ends."
            } else {
                "Your month in music: the songs and artists you played the most."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(GlassTokens.spaceXl))
        Text(
            text = "Tap to continue",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

/** Up to three covers fanned out like a hand of cards. */
@Composable
private fun CoverFan(arts: List<String?>) {
    Box(modifier = Modifier.width(260.dp).height(190.dp), contentAlignment = Alignment.Center) {
        val slots = listOf(Triple(-62.dp, -9f, 1), Triple(62.dp, 9f, 2), Triple(0.dp, 0f, 0))
        slots.forEach { (dx, angle, index) ->
            if (index < arts.size) {
                Artwork(
                    url = arts[index],
                    size = if (index == 0) 170.dp else 140.dp,
                    shape = RoundedCornerShape(WhiplashRadius.medium),
                    modifier = Modifier.offset(x = dx).rotate(angle).shadow(18.dp, RoundedCornerShape(WhiplashRadius.medium)),
                )
            }
        }
    }
}

@Composable
private fun MinutesPage(s: ReplaySummary, active: Boolean, animate: Boolean) {
    // Less than a minute of real listening time (e.g. only plays carried over
    // from before Replay existed that happen to be short) reads better as plays.
    val showMinutes = s.listenedMinutes >= 1
    val target = if (showMinutes) s.listenedMinutes else s.totalPlays.toLong()
    val count = remember(s.monthKey) { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(active, target) {
        if (active && animate) {
            count.snapTo(0f)
            count.animateTo(target.toFloat(), tween(1_600, easing = FastOutSlowInEasing))
        } else if (!animate) {
            count.snapTo(target.toFloat())
        }
    }
    val numbers = NumberFormat.getIntegerInstance()
    PageColumn {
        Kicker(if (showMinutes) "You listened for" else "You pressed play")
        Spacer(Modifier.height(GlassTokens.spaceMd))
        Text(
            text = numbers.format(count.value.toLong()),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 88.sp),
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = numbers.format(target) + if (showMinutes) " minutes" else " plays" },
        )
        Text(
            text = if (showMinutes) "minutes" else if (target == 1L) "time" else "times",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(GlassTokens.spaceXl))
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
            if (showMinutes) StatPill(numbers.format(s.totalPlays), if (s.totalPlays == 1) "play" else "plays")
            StatPill(numbers.format(s.songCount), if (s.songCount == 1) "song" else "songs")
            if (s.artistCount > 0) StatPill(numbers.format(s.artistCount), if (s.artistCount == 1) "artist" else "artists")
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(WhiplashRadius.medium))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun TopArtistPage(artist: ReplayArtistStat, colors: PlayerColors) {
    PageColumn {
        Kicker("Your top artist")
        Spacer(Modifier.height(GlassTokens.spaceLg))
        Box(
            modifier = Modifier
                .size(232.dp)
                .border(3.dp, colors.accent.copy(alpha = 0.9f), CircleShape)
                .padding(8.dp),
        ) {
            Artwork(url = artist.artworkUrl, size = 216.dp, shape = CircleShape, modifier = Modifier.shadow(20.dp, CircleShape))
        }
        Spacer(Modifier.height(GlassTokens.spaceLg))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(GlassTokens.spaceSm))
        Text(
            text = artistLine(artist),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun artistLine(a: ReplayArtistStat): String {
    val parts = mutableListOf(plural(a.plays, "play"), plural(a.songCount, "song"))
    val minutes = a.listenedMs / 60_000L
    if (minutes >= 1) parts += "$minutes min"
    return parts.joinToString(" · ")
}

private fun plural(n: Int, word: String) = "${NumberFormat.getIntegerInstance().format(n)} $word${if (n == 1) "" else "s"}"

@Composable
private fun TopSongsPage(tracks: List<ReplayTrackStat>, colors: PlayerColors, onPlay: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        PageColumn {
            Kicker("Your top songs")
            Spacer(Modifier.height(GlassTokens.spaceMd))
            val first = tracks.first()
            Artwork(
                url = first.artworkUrl,
                size = if (compact) 92.dp else 176.dp,
                shape = RoundedCornerShape(WhiplashRadius.medium),
                modifier = Modifier.shadow(18.dp, RoundedCornerShape(WhiplashRadius.medium)),
            )
            Spacer(Modifier.height(GlassTokens.spaceSm))
            Text(
                text = first.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${first.artist} · ${plural(first.plays, "play")}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(GlassTokens.spaceMd))
            tracks.drop(1).forEachIndexed { i, t ->
                RankRow(
                    rank = i + 2,
                    title = t.title,
                    subtitle = t.artist,
                    trailing = plural(t.plays, "play"),
                    art = t.artworkUrl,
                    circle = false,
                    onClick = { onPlay(i + 1) },
                )
            }
            Spacer(Modifier.height(GlassTokens.spaceMd))
            ReplayButton(text = "Play these songs", icon = Icons.Filled.PlayArrow, colors = colors, onClick = { onPlay(0) })
        }
    }
}

@Composable
private fun TopArtistsPage(artists: List<ReplayArtistStat>) {
    PageColumn {
        Kicker("Your top artists")
        Spacer(Modifier.height(GlassTokens.spaceLg))
        artists.forEachIndexed { i, a ->
            RankRow(
                rank = i + 1,
                title = a.name,
                subtitle = plural(a.songCount, "song"),
                trailing = plural(a.plays, "play"),
                art = a.artworkUrl,
                circle = true,
                big = i == 0,
            )
        }
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    trailing: String,
    art: String?,
    circle: Boolean,
    big: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(WhiplashRadius.small)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Play", onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp, horizontal = GlassTokens.spaceXs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.width(32.dp),
        )
        Artwork(url = art, size = if (big) 64.dp else 48.dp, shape = shape)
        Column(modifier = Modifier.weight(1f).padding(horizontal = GlassTokens.spaceSm)) {
            Text(
                text = title,
                style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun PosterPage(s: ReplaySummary, colors: PlayerColors, onPlay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceLg, vertical = GlassTokens.spaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val scale = minOf(maxWidth / POSTER_WIDTH, maxHeight / POSTER_HEIGHT, 1.2f)
            // The poster is always laid out at one fixed size (so the shared
            // image looks the same on every phone) and only scaled on screen.
            Box(modifier = Modifier.size(POSTER_WIDTH * scale, POSTER_HEIGHT * scale), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .requiredSize(POSTER_WIDTH, POSTER_HEIGHT)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = 24.dp.toPx()
                            shape = RoundedCornerShape(WhiplashRadius.large)
                            clip = true
                        }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        },
                ) {
                    ReplayPoster(s, colors)
                }
            }
        }
        Spacer(Modifier.height(GlassTokens.spaceMd))
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
            ReplayButton(
                text = if (sharing) "Preparing…" else "Share",
                icon = Icons.Filled.Share,
                colors = colors,
                onClick = {
                    if (sharing) return@ReplayButton
                    sharing = true
                    scope.launch {
                        runCatching { sharePoster(context, layer.toImageBitmap(), s.monthKey) }
                            .onFailure {
                                com.whiplash.music.ui.common.ToastController.show("Couldn't create the image")
                            }
                        sharing = false
                    }
                },
            )
            ReplayButton(text = "Play top songs", icon = Icons.Filled.PlayArrow, colors = colors, filled = false, onClick = onPlay)
        }
    }
}

/** Writes the poster to the app's cache and opens the system share sheet. */
private suspend fun sharePoster(context: android.content.Context, image: ImageBitmap, monthKey: String) {
    val file = withContext(Dispatchers.IO) {
        var bitmap = image.asAndroidBitmap()
        // A hardware bitmap can't be compressed directly.
        if (android.os.Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val dir = File(context.cacheDir, "replay").apply { mkdirs() }
        // One file per month, overwritten each time.
        File(dir, "whiplash-replay-$monthKey.png").also { out ->
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My ${replayMonthLabel(monthKey)} Replay on Whiplash")
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your Replay").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The shareable poster. Laid out at [POSTER_WIDTH]×[POSTER_HEIGHT] with the
 * font scale pinned to 1, since it's an image with a fixed layout (the rest
 * of Replay follows the system font size).
 */
@Composable
private fun ReplayPoster(s: ReplaySummary, colors: PlayerColors) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        Box(Modifier.fillMaxSize()) {
            PlayerMeshBackdrop(colors = colors, animate = false)
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Insights, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WHIPLASH REPLAY", fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                }
                Text(
                    text = replayMonthLabel(s.monthKey),
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(18.dp))
                val top = s.topTracks.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(url = top.artworkUrl, size = 164.dp, shape = RoundedCornerShape(16.dp), software = true)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("TOP SONG", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        Text(top.title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(top.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth()) {
                    PosterList("Top songs", s.topTracks.map { it.title }, Modifier.weight(1f))
                    if (s.topArtists.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        PosterList("Top artists", s.topArtists.map { it.name }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.weight(1f))
                val numbers = NumberFormat.getIntegerInstance()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(vertical = 12.dp),
                ) {
                    if (s.listenedMinutes >= 1) PosterStat(numbers.format(s.listenedMinutes), "minutes")
                    PosterStat(numbers.format(s.totalPlays), "plays")
                    PosterStat(numbers.format(s.songCount), "songs")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Made with Whiplash",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun PosterList(title: String, entries: List<String>, modifier: Modifier) {
    Column(modifier) {
        Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        entries.forEachIndexed { i, e ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.width(18.dp))
                Text(e, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RowScope.PosterStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun ReplayButton(text: String, icon: ImageVector, colors: PlayerColors, onClick: () -> Unit, filled: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(colors.accent)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (filled) colors.onAccent else Color.White
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
    }
}

@Composable
private fun ReplayEmpty(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(GlassTokens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(GlassTokens.spaceMd))
        Text("Nothing to replay yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(GlassTokens.spaceSm))
        Text(
            "Play some music and your monthly recap will build up here.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Cover image. [software] keeps the bitmap out of GPU-only memory, which
 * the poster needs so it can be turned into a shareable image on every
 * Android version.
 */
@Composable
private fun Artwork(url: String?, size: Dp, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier, software: Boolean = false) {
    Box(
        modifier = modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(!software)
                    .allowHardware(!software)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(size / 3))
        }
    }
}

/**
 * Home's entry point: a compact card with the month's top covers, shown
 * after Quick Picks while Replay is on and there's something to recap.
 */
@Composable
fun ReplayHomeCard(summary: ReplaySummary, isCurrentMonth: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette by rememberArtworkPalette(summary.topTracks.firstOrNull()?.artworkUrl, enabled = true)
    val colors = animatedPlayerColors(palette)
    val month = replayMonthName(summary.monthKey)
    val title = if (isCurrentMonth) "Your $month so far" else "Your $month Replay"
    val detail = buildList {
        if (summary.listenedMinutes >= 1) add("${NumberFormat.getIntegerInstance().format(summary.listenedMinutes)} min")
        add(plural(summary.songCount, "song"))
        summary.topArtists.firstOrNull()?.let { add("top: ${it.name}") }
    }.joinToString(" · ")
    val shape = RoundedCornerShape(WhiplashRadius.large)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WhiplashColors.surfaceElevated)
            .clickable(onClickLabel = "Open Replay", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "Replay. $title. $detail" },
    ) {
        PlayerMeshBackdrop(colors = colors, animate = false, modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(92.dp).height(64.dp)) {
                summary.topTracks.take(3).reversed().forEachIndexed { i, t ->
                    val slot = summary.topTracks.take(3).size - 1 - i
                    Artwork(
                        url = t.artworkUrl,
                        size = 64.dp - (slot * 6).dp,
                        shape = RoundedCornerShape(WhiplashRadius.small),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (slot * 16).dp)
                            .shadow(6.dp, RoundedCornerShape(WhiplashRadius.small)),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = GlassTokens.spaceMd)) {
                Text("REPLAY", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.75f))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
