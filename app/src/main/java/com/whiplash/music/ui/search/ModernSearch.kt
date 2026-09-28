package com.whiplash.music.ui.search

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import com.whiplash.music.ui.theme.glassMaterial
import com.whiplash.music.ui.theme.glassShadow
import com.whiplash.music.ui.theme.glassFill
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.ui.common.artworkAtSize
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.player.rememberArtworkPalette
import com.whiplash.music.ui.theme.CollectionPillButton
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.tintForName
import kotlinx.coroutines.launch

/*
 * The Search results look.
 * Nothing here adds work to a search itself: results arrive exactly as
 * before, and the only extra fetches are larger cover images for the top
 * result card and the grids, loaded in the background like any artwork.
 * Album/playlist tracks are only loaded when a menu action needs them.
 */

/** Cover size requested for grid tiles and the top result card (~180dp at 3x). */
private const val LARGE_ART_PX = 544

/** Artist photos in the round grid (~110dp at 3x). */
private const val ARTIST_ART_PX = 360

private val tileShape = RoundedCornerShape(16.dp)


@Composable
private fun tonal(amount: Float = 0.08f): Color = WhiplashColors.tone(amount)

private fun tabIcon(tab: SearchResultTab): ImageVector = when (tab) {
    SearchResultTab.SONGS -> Icons.Filled.MusicNote
    SearchResultTab.ALBUMS -> Icons.Filled.Album
    SearchResultTab.ARTISTS -> Icons.Filled.Person
    SearchResultTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
}

// ---------------------------------------------------------------- filters

/**
 * Songs / Albums / Artists / Playlists as one segmented bar: four equal
 * segments (icon over label) with an accent pill that glides to the
 * selected one. Always fits the width, so no category hides off-screen.
 * [count] feeds each segment's accessibility label.
 */
@Composable
internal fun ModernSearchFilters(
    selected: SearchResultTab,
    count: (SearchResultTab) -> Int,
    onSelect: (SearchResultTab) -> Unit,
) {
    val tabs = SearchResultTab.entries
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .glassFill(RoundedCornerShape(20.dp), tonal(0.07f))
            .padding(4.dp),
    ) {
        val segment = maxWidth / tabs.size
        val offset by androidx.compose.animation.core.animateDpAsState(
            segment * tabs.indexOf(selected),
            tween(GlassTokens.animSlow, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "filterIndicator",
        )
        // The selected segment: in Liquid Glass a raised 3D glass puck (rim,
        // bevel, gloss and a soft lift); otherwise a solid accent pill. It's
        // one element that slides, and the tabs have no press ripple, so
        // nothing else flashes at the destination while it moves.
        val glassPuck = WhiplashColors.isGlass
        val puckShape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .matchParentSize()
                .wrapContentWidth(Alignment.Start),
        ) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .then(
                        if (glassPuck) {
                            Modifier
                                .glassShadow(puckShape, elevation = 6.dp, strength = if (WhiplashColors.isLight) 0.16f else 0.28f)
                                .clip(puckShape)
                                .background(
                                    if (WhiplashColors.isLight) Color.White.copy(alpha = 0.55f)
                                    else WhiplashColors.textPrimary.copy(alpha = 0.14f),
                                )
                                .glassMaterial(puckShape)
                        } else {
                            Modifier.clip(puckShape).background(WhiplashColors.accent)
                        },
                    ),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val content by animateColorAsState(
                    when {
                        isSelected && glassPuck -> WhiplashColors.textPrimary
                        isSelected -> WhiplashColors.onAccent
                        else -> WhiplashColors.textSecondary
                    },
                    tween(GlassTokens.animSlow),
                    label = "filterContent",
                )
                val n = count(tab)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .semantics { contentDescription = if (n > 0) "${tab.label}, $n results" else tab.label }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tabIcon(tab), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = content,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- songs

/**
 * Songs tab: the first result as a large "Top result" card tinted from its
 * own cover, then the rest as the usual song rows (same long-press and ⋮
 * menus, download badges and infinite scroll as before).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ModernSongsTab(
    results: List<PlayableItem.YoutubeTrack>,
    isRefreshing: Boolean,
    onPlayTrack: (PlayableItem.YoutubeTrack) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    if (results.isEmpty()) {
        ModernEmptyTab(Icons.Filled.MusicNote, "No songs found")
        return
    }
    val top = results.first()
    val rest = results.drop(1)
    Column(Modifier.fillMaxSize()) {
        if (isRefreshing) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(GlassTokens.spaceSm).size(20.dp),
                    color = WhiplashColors.accent,
                    strokeWidth = 2.dp,
                )
            }
        }
        PlayableItemsList(
            items = rest,
            onPlayQueue = { _, index -> rest.getOrNull(index)?.let(onPlayTrack) },
            modifier = Modifier.fillMaxSize(),
            onLoadMore = onLoadMore,
            isLoadingMore = isLoadingMore,
            headerWithActions = { openActions ->
                Column {
                    TopResultCard(top, onPlay = { onPlayTrack(top) }, onMore = { openActions(top) })
                    if (rest.isNotEmpty()) SectionLabel("Songs")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(track: PlayableItem.YoutubeTrack, onPlay: () -> Unit, onMore: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val art = artworkAtSize(track.artworkUri, LARGE_ART_PX)
    // Colours from the cover itself (same sampler as the player, tiny
    // decode from the disk cache), falling back to a tint from the title.
    val palette by rememberArtworkPalette(art, enabled = true)
    // Always a deep, cover-coloured card with white text (the mesh tones are
    // dark too), so it reads the same in light and dark themes.
    val fallback = lerp(Color(0xFF141418), tintForName(track.title), 0.35f)
    val mesh = palette?.mesh
    val c1 by animateColorAsState(mesh?.getOrNull(0)?.let { Color(it) } ?: fallback, tween(GlassTokens.animSlow), label = "top1")
    val c2 by animateColorAsState(mesh?.getOrNull(3)?.let { Color(it) } ?: Color(0xFF1B1B20), tween(GlassTokens.animSlow), label = "top2")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GlassTokens.spaceSm)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .combinedClickable(
                onClickLabel = "Play ${track.title}",
                onLongClickLabel = "More options",
                onClick = onPlay,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore()
                },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(art, Modifier.size(112.dp), RoundedCornerShape(16.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = "TOP RESULT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f),
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = listOfNotNull("Song", track.artist.takeIf { it.isNotBlank() }, durationLabel(track.durationMs)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CollectionPillButton(text = "Play", icon = Icons.Filled.PlayArrow, onClick = onPlay, primary = true)
                Spacer(Modifier.weight(1f))
                PlainIconButton(contentDescription = "More options for ${track.title}", onClick = onMore, size = 44.dp) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

private fun durationLabel(ms: Long): String? {
    if (ms <= 0) return null
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = WhiplashColors.textPrimary,
        modifier = Modifier.padding(top = GlassTokens.spaceMd, bottom = GlassTokens.spaceXs),
    )
}

// ---------------------------------------------------------------- albums & playlists

/**
 * Albums or playlists as a two-column cover grid. Tap opens; long-press or
 * ⋮ opens a menu with Open, Play, Shuffle, Play next, Add to queue, Save to
 * Playlists, Download and Share.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModernCollectionGrid(
    items: List<YoutubePlaylistResult>,
    isAlbum: Boolean,
    onOpen: (YoutubePlaylistResult) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    if (items.isEmpty()) {
        ModernEmptyTab(
            if (isAlbum) Icons.Filled.Album else Icons.AutoMirrored.Filled.QueueMusic,
            if (isAlbum) "No albums found" else "No playlists found",
        )
        return
    }
    val haptic = LocalHapticFeedback.current
    var menuFor by remember { mutableStateOf<YoutubePlaylistResult?>(null) }
    val state = rememberLazyGridState()
    LoadMoreWhenNearEnd(state, items.size, isLoadingMore, onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = GlassTokens.spaceXs, bottom = GlassTokens.miniPlayerReservedHeight),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.url }) { result ->
            // Only the cover is clipped: clipping the whole tile would shave
            // the first letter of the title under it.
            Column(
                Modifier.combinedClickable(
                    onClickLabel = "Open ${result.title}",
                    onLongClickLabel = "More options",
                    onClick = { onOpen(result) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuFor = result
                    },
                ),
            ) {
                Cover(artworkAtSize(result.artworkUrl, LARGE_ART_PX), Modifier.fillMaxWidth().aspectRatio(1f), tileShape)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = WhiplashColors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = collectionSubtitle(result)
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = WhiplashColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    PlainIconButton(
                        contentDescription = "More options for ${result.title}",
                        onClick = { menuFor = result },
                        size = 36.dp,
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (isLoadingMore) {
            item(key = "__load_more__", span = { GridItemSpan(maxLineSpan) }) { LoadingFooter() }
        }
    }
    CollectionMenuSheet(menuFor, onDismiss = { menuFor = null }, onOpen = onOpen)
}

private fun collectionSubtitle(r: YoutubePlaylistResult): String {
    val kind = if (r.isAlbum) "Album" else "Playlist"
    val count = r.trackCount?.takeIf { it > 0 }?.let { if (it == 1L) "1 song" else "$it songs" }
    return listOfNotNull(kind, r.uploaderName?.takeIf { it.isNotBlank() }, count).joinToString(" · ")
}

/** Songs of albums/playlists whose menu was opened recently, so actions start instantly. */
private val collectionTrackCache = object : LinkedHashMap<String, List<PlayableItem.YoutubeTrack>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlayableItem.YoutubeTrack>>?) = size > 20
}

private suspend fun loadCollectionTracks(app: WhiplashApplication, url: String): List<PlayableItem.YoutubeTrack>? {
    synchronized(collectionTrackCache) { collectionTrackCache[url] }?.let { return it }
    val tracks = runCatching { app.youtubeDetailProvider.getPlaylistDetail(url).tracks }.getOrNull()
    if (!tracks.isNullOrEmpty()) synchronized(collectionTrackCache) { collectionTrackCache[url] = tracks }
    return tracks
}

/**
 * Long-press / ⋮ menu for an album or playlist (Search results, Explore
 * and genre pages).
 *
 * Its songs start loading when the menu opens (only then — never during a
 * search), so Play and the rest start at once and the Download row can
 * show the real state. Rows follow that state live:
 * - Save to Playlists → Remove from Playlists once saved.
 * - Download → Cancel download while any song is downloading → Remove
 *   download once every song is downloaded.
 * Those two keep the menu open so the change is visible; playback actions
 * close it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionMenuSheet(
    collection: YoutubePlaylistResult?,
    onDismiss: () -> Unit,
    onOpen: (YoutubePlaylistResult) -> Unit,
) {
    if (collection == null) return
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val kind = if (collection.isAlbum) "album" else "playlist"
    val url = collection.url

    val tracks by androidx.compose.runtime.produceState<List<PlayableItem.YoutubeTrack>?>(
        initialValue = synchronized(collectionTrackCache) { collectionTrackCache[url] },
        url,
    ) {
        if (value == null) value = loadCollectionTracks(app, url)
    }

    // Saved-to-Playlists state: the stored mapping, and only while that
    // playlist still exists under the same name.
    val store = remember { com.whiplash.music.data.repository.SavedCollectionStore.get(context) }
    val savedMap by store.saved.collectAsState()
    val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = null)
    val savedEntry = savedMap[url]?.takeIf { s -> playlists?.any { it.id == s.playlistId && it.name == s.name } == true }
    var confirmRemoveSaved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val download = com.whiplash.music.ui.common.rememberBatchDownloadController(collection.title, tracks.orEmpty())
    var confirmRemoveDownloads by remember { mutableStateOf(false) }
    val allFavorited = com.whiplash.music.ui.common.rememberAllFavorited(tracks)
    var favoriteDialog by remember { mutableStateOf<Boolean?>(null) } // true = add, false = remove

    fun withTracks(close: Boolean = true, action: suspend (List<PlayableItem.YoutubeTrack>) -> Unit) {
        if (close) onDismiss()
        val ready = tracks
        if (ready == null) ToastController.show("Loading ${collection.title}…")
        com.whiplash.music.ui.common.UiActionScope.scope.launch {
            val list = ready ?: loadCollectionTracks(app, url)
            if (list.isNullOrEmpty()) {
                ToastController.show("Couldn't load ${collection.title}")
            } else {
                runCatching { action(list) }.onFailure { ToastController.show("Something went wrong with ${collection.title}") }
            }
        }
    }

    GlassSheet(onDismissRequest = onDismiss) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = GlassTokens.spaceSm)) {
                Cover(artworkAtSize(collection.artworkUrl, LARGE_ART_PX), Modifier.size(56.dp), RoundedCornerShape(12.dp))
                Column(Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
                    Text(collection.title, style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val count = tracks?.size?.let { if (it == 1) "1 song" else "$it songs" }
                    Text(
                        listOfNotNull(collectionSubtitle(collection).takeIf { it.isNotBlank() }, count.takeIf { collection.trackCount == null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open $kind") { onDismiss(); onOpen(collection) }
            SheetAction(Icons.Filled.PlayArrow, "Play") {
                withTracks { app.playbackController.playQueue(it, 0) }
            }
            SheetAction(Icons.Filled.Shuffle, "Shuffle") {
                withTracks { app.playbackController.playQueue(it.shuffled(), 0) }
            }
            SheetAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") {
                withTracks { list ->
                    // Each insert goes straight after the current song, so
                    // adding in reverse keeps the album's own order.
                    list.asReversed().forEach { app.playbackController.playNext(it) }
                    ToastController.show("${list.size} songs will play next")
                }
            }
            SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") {
                withTracks { list ->
                    list.forEach { app.playbackController.addToQueue(it) }
                    ToastController.show("Added ${list.size} songs to queue")
                }
            }

            if (allFavorited == true) {
                SheetAction(Icons.Filled.Favorite, "Remove all from Favorites", tint = WhiplashColors.accent) { favoriteDialog = false }
            } else {
                SheetAction(Icons.Filled.FavoriteBorder, "Add all to Favorites", enabled = tracks != null) { favoriteDialog = true }
            }

            if (savedEntry != null) {
                SheetAction(Icons.Filled.LibraryAddCheck, "Remove from Playlists", tint = WhiplashColors.accent) { confirmRemoveSaved = true }
            } else {
                SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, if (saving) "Saving to Playlists…" else "Save to Playlists", enabled = !saving) {
                    saving = true
                    withTracks(close = false) { list ->
                        try {
                            val name = collection.title.ifBlank { "Saved $kind" }
                            val id = app.libraryRepository.createPlaylist(name)
                            list.forEach { app.libraryRepository.addToPlaylist(id, it) }
                            store.put(url, id, name)
                            ToastController.show("Saved \"$name\" to Playlists (${list.size} songs)")
                        } finally {
                            saving = false
                        }
                    }
                }
            }

            when {
                download == null -> SheetAction(Icons.Filled.Download, "Download", enabled = false, trailing = {
                    CircularProgressIndicator(color = WhiplashColors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }) {}
                download.state == com.whiplash.music.ui.common.BatchDownloadState.DOWNLOADING -> {
                    val done = download.downloadedInBatch.size
                    val total = done + download.notDownloaded.size
                    SheetAction(Icons.Filled.Close, "Cancel download", subtitle = "Downloading · $done of $total done", trailing = {
                        CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }) {
                        download.inFlightInBatch.forEach { app.downloadManager.cancelDownload(it.id) }
                        ToastController.show("Download cancelled")
                    }
                }
                download.state == com.whiplash.music.ui.common.BatchDownloadState.DOWNLOADED ->
                    SheetAction(Icons.Filled.DownloadDone, "Remove download", tint = WhiplashColors.accent) { confirmRemoveDownloads = true }
                else -> {
                    val partial = download.downloadedInBatch.isNotEmpty()
                    SheetAction(
                        Icons.Filled.Download,
                        if (partial) "Download the rest" else "Download",
                        subtitle = if (partial) "${download.downloadedInBatch.size} already downloaded" else null,
                    ) {
                        if (download.notDownloaded.isEmpty()) {
                            ToastController.show("Nothing left to download")
                        } else {
                            app.downloadManager.downloadAll(download.notDownloaded)
                        }
                    }
                }
            }

            SheetAction(Icons.Filled.Share, "Share") {
                onDismiss()
                shareLink(context, collection.title, url)
            }
        }
    }

    val favTracks = tracks
    if (favoriteDialog != null && favTracks != null) {
        com.whiplash.music.ui.common.FavoriteAllDialogs(
            name = collection.title,
            tracks = favTracks,
            confirmAdd = favoriteDialog == true,
            confirmRemove = favoriteDialog == false,
            onDismiss = { favoriteDialog = null; onDismiss() },
        )
    }

    if (confirmRemoveSaved && savedEntry != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Remove from Playlists?",
            message = "The playlist \"${savedEntry.name}\" you saved from this $kind will be deleted. Downloads aren't affected.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemoveSaved = false
                com.whiplash.music.ui.common.UiActionScope.scope.launch {
                    app.libraryRepository.deletePlaylist(savedEntry.playlistId)
                    store.remove(url)
                    ToastController.show("Removed \"${savedEntry.name}\" from Playlists")
                }
            },
            onDismiss = { confirmRemoveSaved = false },
        )
    }
    if (download != null) {
        com.whiplash.music.ui.common.BatchDownloadDialogs(
            controller = download,
            showDownloadConfirm = false,
            showCancelConfirm = false,
            showRemoveConfirm = confirmRemoveDownloads,
            onDismissDownload = {},
            onDismissCancel = {},
            onDismissRemove = { confirmRemoveDownloads = false },
        )
    }
}

/**
 * Round Save-to-Playlists toggle for an album/playlist page's header: an
 * outlined "add" icon until saved, then a filled accent tick. Tapping the
 * tick asks before deleting the saved copy. Same saved state as the
 * long-press menu ([CollectionMenuSheet]), so both always agree.
 */
@Composable
internal fun SaveCollectionIconButton(
    url: String,
    title: String,
    isAlbum: Boolean,
    tracks: List<PlayableItem.YoutubeTrack>,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val store = remember { com.whiplash.music.data.repository.SavedCollectionStore.get(context) }
    val savedMap by store.saved.collectAsState()
    val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = null)
    val saved = savedMap[url]?.takeIf { s -> playlists?.any { it.id == s.playlistId && it.name == s.name } == true }
    var confirmRemove by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val kind = if (isAlbum) "album" else "playlist"
    val tint by animateColorAsState(
        if (saved != null) WhiplashColors.accent else WhiplashColors.textPrimary,
        tween(GlassTokens.animSlow),
        label = "saveTint",
    )
    PlainIconButton(
        contentDescription = if (saved != null) "Remove $title from Playlists" else "Save $title to Playlists",
        onClick = {
            if (saved != null) {
                confirmRemove = true
            } else if (!saving && tracks.isNotEmpty()) {
                saving = true
                com.whiplash.music.ui.common.UiActionScope.scope.launch {
                    try {
                        val name = title.ifBlank { "Saved $kind" }
                        val id = app.libraryRepository.createPlaylist(name)
                        tracks.forEach { app.libraryRepository.addToPlaylist(id, it) }
                        store.put(url, id, name)
                        ToastController.show("Saved \"$name\" to Playlists (${tracks.size} songs)")
                    } finally {
                        saving = false
                    }
                }
            }
        },
        size = 46.dp,
        enabled = tracks.isNotEmpty(),
    ) {
        com.whiplash.music.ui.theme.RoundActionSurface {
            if (saving) {
                CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    if (saved != null) Icons.Filled.LibraryAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
    if (confirmRemove && saved != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Remove from Playlists?",
            message = "The playlist \"${saved.name}\" you saved from this $kind will be deleted. Downloads aren't affected.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                com.whiplash.music.ui.common.UiActionScope.scope.launch {
                    app.libraryRepository.deletePlaylist(saved.playlistId)
                    store.remove(url)
                    ToastController.show("Removed \"${saved.name}\" from Playlists")
                }
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

// ---------------------------------------------------------------- artists

/** Artists as a grid of round photos. Tap opens; long-press or ⋮ offers Open and Share. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ModernArtistGrid(
    items: List<YoutubeArtistResult>,
    onOpen: (YoutubeArtistResult) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    if (items.isEmpty()) {
        ModernEmptyTab(Icons.Filled.Person, "No artists found")
        return
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menuFor by remember { mutableStateOf<YoutubeArtistResult?>(null) }
    val state = rememberLazyGridState()
    LoadMoreWhenNearEnd(state, items.size, isLoadingMore, onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = GlassTokens.spaceSm, bottom = GlassTokens.miniPlayerReservedHeight),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.channelUrl }) { artist ->
            Column(
                modifier = Modifier.combinedClickable(
                    onClickLabel = "Open ${artist.name}",
                    onLongClickLabel = "More options",
                    onClick = { onOpen(artist) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuFor = artist
                    },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                            .background(lerp(WhiplashColors.background, tintForName(artist.name), 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            artist.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = com.whiplash.music.ui.theme.inkOn(lerp(WhiplashColors.background, tintForName(artist.name), 0.45f)),
                        )
                        if (artist.artworkUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(artworkAtSize(artist.artworkUrl, ARTIST_ART_PX)).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                artist.subscriberCount?.let { count ->
                    Text(
                        text = "${com.whiplash.music.ui.common.formatCompactCount(count)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (isLoadingMore) {
            item(key = "__load_more__", span = { GridItemSpan(maxLineSpan) }) { LoadingFooter() }
        }
    }
    val artist = menuFor
    if (artist != null) {
        GlassSheet(onDismissRequest = { menuFor = null }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = GlassTokens.spaceSm)) {
                    Cover(artworkAtSize(artist.artworkUrl, ARTIST_ART_PX), Modifier.size(56.dp), CircleShape)
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = WhiplashColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
                SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open artist") { menuFor = null; onOpen(artist) }
                SheetAction(Icons.Filled.Share, "Share") {
                    menuFor = null
                    shareLink(context, artist.name, artist.channelUrl)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- suggestions

/**
 * Live suggestions while typing. The part already typed stays soft and the
 * rest is bold, so the difference between suggestions is easy to see. The
 * arrow on the right copies a suggestion into the box to keep typing,
 * without searching.
 */
@Composable
internal fun ModernSuggestions(
    query: String,
    suggestions: List<String>,
    onSuggestionTap: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val typed = query.trim()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Search $suggestion") { onSuggestionTap(suggestion) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(tonal()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = WhiplashColors.textSecondary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = buildAnnotatedString {
                        if (typed.isNotEmpty() && suggestion.startsWith(typed, ignoreCase = true)) {
                            withStyle(SpanStyle(color = WhiplashColors.textSecondary)) { append(suggestion.take(typed.length)) }
                            withStyle(SpanStyle(color = WhiplashColors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(suggestion.drop(typed.length)) }
                        } else {
                            withStyle(SpanStyle(color = WhiplashColors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(suggestion) }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f),
                )
                PlainIconButton(contentDescription = "Fill in $suggestion", onClick = { onFill(suggestion) }, size = 44.dp) {
                    Icon(Icons.Filled.NorthWest, contentDescription = null, tint = WhiplashColors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(GlassTokens.miniPlayerReservedHeight))
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun Cover(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(modifier.clip(shape).background(tonal(0.06f)), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    text: String,
    tint: Color = WhiplashColors.textPrimary,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else WhiplashColors.textTertiary)
        Column(Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) WhiplashColors.textPrimary else WhiplashColors.textTertiary,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
            }
        }
        trailing?.invoke()
    }
}

private fun shareLink(context: android.content.Context, title: String, url: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share $title")) }
        .onFailure { ToastController.show("Couldn't open share") }
}

@Composable
private fun ModernEmptyTab(icon: ImageVector, message: String) {
    Column(
        Modifier.fillMaxSize().padding(bottom = GlassTokens.miniPlayerReservedHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tonal()), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = WhiplashColors.textSecondary, modifier = Modifier.size(28.dp))
        }
        Text(message, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textSecondary, modifier = Modifier.padding(top = GlassTokens.spaceMd))
    }
}

@Composable
private fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(GlassTokens.spaceMd), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
}

/** Grid version of the lists' infinite scroll: asks for more a few items before the end. */
@Composable
private fun LoadMoreWhenNearEnd(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    itemCount: Int,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(state, isLoadingMore, itemCount) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !isLoadingMore && last >= itemCount - 1 - GRID_LOAD_MORE_THRESHOLD) onLoadMore()
            }
    }
}

private const val GRID_LOAD_MORE_THRESHOLD = 6
