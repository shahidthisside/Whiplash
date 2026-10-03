// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.localmusic

import com.whiplash.music.ui.theme.glassFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.LocalAlbum
import com.whiplash.music.domain.model.LocalArtist
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/** The Library's sections, each opened as its own page from the start page. */
internal enum class LibrarySection(val label: String, val icon: ImageVector, val tint: Color) {
    DOWNLOADS("Downloads", Icons.Filled.DownloadForOffline, Color(0xFF4DB88A)),
    SONGS("Songs", Icons.Filled.MusicNote, Color(0xFF5B8DEF)),
    ALBUMS("Albums", Icons.Filled.Album, Color(0xFFB36BD8)),
    ARTISTS("Artists", Icons.Filled.Person, Color(0xFFE39B4B)),
}

private val HISTORY_TINT = Color(0xFF3FA7D6)

/**
 * Modern Library start page: one search field, then four colour cards
 * (Downloads, Songs, Albums, Artists) each showing its count and a tilted
 * piece of its own artwork, a Listening history card, and a shelf of the
 * latest downloads. Typing searches downloads and the phone's music together.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun ModernLibraryHome(
    viewModel: LocalLibraryViewModel,
    hasMediaPermission: Boolean,
    onOpenSection: (LibrarySection) -> Unit,
    onOpenHistory: (() -> Unit)?,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val inFlight by viewModel.inFlightTracks.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val localResults by viewModel.searchResults.collectAsState()
    val downloadResults by viewModel.downloadSearchResults.collectAsState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {
        GlassSearchField(
            query = query,
            onQueryChange = viewModel::onSearchQueryChanged,
            placeholder = "Search your library",
            modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTokens.spaceMd),
        )

        if (query.isNotBlank()) {
            val results: List<PlayableItem> = downloadResults + (if (hasMediaPermission) localResults else emptyList())
            if (results.isEmpty()) {
                com.whiplash.music.ui.theme.CollectionEmptyState(
                    icon = Icons.Filled.MusicNote,
                    title = "Nothing found",
                    message = if (hasMediaPermission) "No downloads or songs on this phone match \"${query.trim()}\"."
                    else "No downloads match \"${query.trim()}\". Allow music access in Songs to search this phone too.",
                    tint = LibrarySection.SONGS.tint,
                )
            } else {
                com.whiplash.music.ui.player.PlayableItemsList(
                    items = results,
                    onPlayQueue = { _, index ->
                        keyboard?.hide()
                        focus.clearFocus()
                        onPlayQueue(results, index)
                    },
                    modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd).padding(top = GlassTokens.spaceSm),
                )
            }
            return@Column
        }

        val summaries = mapOf(
            LibrarySection.DOWNLOADS to when {
                downloads.isEmpty() && inFlight.isEmpty() -> "Save songs for offline"
                else -> listOfNotNull(
                    if (downloads.isNotEmpty()) com.whiplash.music.ui.theme.songCountLabel(downloads.size) else null,
                    if (inFlight.isNotEmpty()) "${inFlight.size} downloading" else null,
                ).joinToString(" · ")
            },
            LibrarySection.SONGS to if (!hasMediaPermission) "Allow music access" else countLabel(songs.size, "song"),
            LibrarySection.ALBUMS to if (!hasMediaPermission) "On this phone" else countLabel(albums.size, "album"),
            LibrarySection.ARTISTS to if (!hasMediaPermission) "On this phone" else countLabel(artists.size, "artist"),
        )
        // Real covers where there are some: downloads' own artwork, the phone's songs, one per album.
        val covers = mapOf(
            LibrarySection.DOWNLOADS to downloads.mapNotNull { it.artworkUri }.distinct().take(4),
            LibrarySection.SONGS to songs.mapNotNull { it.artworkUri }.distinct().take(4),
            LibrarySection.ALBUMS to albumArtworks(songs).values.distinct().take(4),
            LibrarySection.ARTISTS to emptyList(),
        )
        val artistFace = artistArtworks(songs).values.firstOrNull()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
            contentPadding = PaddingValues(top = GlassTokens.spaceMd, bottom = GlassTokens.miniPlayerReservedHeight),
        ) {
            // Two columns of colour cards: Downloads | Songs, Albums | Artists.
            LibrarySection.entries.chunked(2).forEach { pair ->
                item(key = "row:" + pair.first().name) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTokens.spaceMd),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { section ->
                            val cover = if (section == LibrarySection.ARTISTS) artistFace
                            else if (section == LibrarySection.SONGS && !hasMediaPermission) null
                            else covers.getValue(section).firstOrNull()
                            CategoryCard(
                                section = section,
                                summary = summaries.getValue(section),
                                cover = cover,
                                round = section == LibrarySection.ARTISTS,
                                onClick = { onOpenSection(section) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // Shuffle everything (downloads + this phone's songs) beside Listening history.
            val everything: List<PlayableItem> = downloads + (if (hasMediaPermission) songs else emptyList())
            item(key = "quick") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTokens.spaceMd),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onOpenHistory != null) {
                        QuickCard(
                            icon = Icons.Filled.History,
                            tint = HISTORY_TINT,
                            title = "History",
                            subtitle = "Recently played",
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    QuickCard(
                        icon = Icons.Filled.Shuffle,
                        tint = WhiplashColors.accent,
                        title = "Shuffle all",
                        subtitle = if (everything.isEmpty()) "Nothing to play yet" else com.whiplash.music.ui.theme.songCountLabel(everything.size),
                        onClick = { if (everything.isNotEmpty()) onPlayQueue(everything.shuffled(), 0) },
                        enabled = everything.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (downloads.isNotEmpty()) {
                item(key = "recent") {
                    RecentDownloadsShelf(downloads = downloads, onPlay = { index -> onPlayQueue(downloads, index) })
                }
            }
        }
    }
}

/**
 * A section as a bold colour card: name and count at the top left, and the
 * section's own artwork tilted into the bottom-right corner (an artist
 * photo is round). With no artwork yet, a large faint icon takes its place.
 */
@Composable
private fun CategoryCard(
    section: LibrarySection,
    summary: String,
    cover: String?,
    round: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(WhiplashRadius.large)
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(lerp(section.tint, Color.White, 0.05f), lerp(section.tint, Color.Black, 0.5f)),
                ),
            )
            .clickable(role = Role.Button, onClickLabel = "Open ${section.label}") {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {},
    ) {
        val art = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 14.dp, y = 10.dp)
            .size(72.dp)
            .graphicsLayer { rotationZ = 18f }
        if (cover != null) {
            Box(
                art.shadow(10.dp, if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp))
                    .clip(if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)),
            ) {
                LocalArtwork(uri = cover, name = section.label, fallbackIcon = section.icon, modifier = Modifier.fillMaxSize())
            }
        } else {
            Icon(section.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.22f), modifier = art)
        }
        Column(modifier = Modifier.padding(14.dp).padding(end = 36.dp)) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Slim flat card with a tinted icon tile: History and Shuffle all. */
@Composable
private fun QuickCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .glassFill(RoundedCornerShape(WhiplashRadius.large), com.whiplash.music.ui.theme.collectionCardColor())
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = com.whiplash.music.ui.theme.readableTint(tint), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// id 5A17A226 / wl-sa26
/** A row of the latest downloads' covers; tapping one plays downloads from there. */
@Composable
private fun RecentDownloadsShelf(downloads: List<PlayableItem.DownloadedTrack>, onPlay: (Int) -> Unit) {
    Column {
        Text(
            text = "Recently downloaded",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(start = GlassTokens.spaceMd, top = GlassTokens.spaceSm, bottom = GlassTokens.spaceSm).semantics { heading() },
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = GlassTokens.spaceMd),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(downloads.take(12).size, key = { downloads[it].id }) { i ->
                val track = downloads[i]
                Column(
                    // Only the cover is clipped: clipping the whole column also shaved
                    // the first letter of titles like "The Weeknd".
                    modifier = Modifier
                        .width(124.dp)
                        .clickable(onClickLabel = "Play ${track.title}") { onPlay(i) }
                        .semantics(mergeDescendants = true) {},
                ) {
                    LocalArtwork(
                        uri = track.artworkUri,
                        name = track.title,
                        fallbackIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.size(124.dp).clip(RoundedCornerShape(WhiplashRadius.medium)),
                    )
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "8.2 MB", "1.3 GB". */
private fun formatStorage(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

private fun countLabel(n: Int, noun: String): String = when (n) {
    0 -> "No ${noun}s yet"
    1 -> "1 $noun"
    else -> "$n ${noun}s"
}

/** Back row naming the page, as on Settings' section pages. */
@Composable
private fun SectionBackRow(title: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainIconButton(contentDescription = "Back to Library", onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = WhiplashColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = GlassTokens.spaceXs).semantics { heading() },
            )
        }
    }
}

/**
 * One Library section's page. Downloads and Songs are song collections
 * (header, Play/Shuffle, filter, sort); Albums is a cover grid and Artists a
 * list, each opening its own page.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun LibrarySectionPage(
    section: LibrarySection,
    viewModel: LocalLibraryViewModel,
    hasMediaPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val inFlight by viewModel.inFlightTracks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    // "Save all" asks first: it says how many songs and exactly where they go.
    var confirmSaveAll by remember { mutableStateOf(false) }
    val saveToDevice = com.whiplash.music.ui.common.rememberSaveToDevice()
    // Space used, refreshed whenever the set of downloads changes.
    val downloadBytes by androidx.compose.runtime.produceState(0L, downloads.size) { value = viewModel.downloadsBytes() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        val needsPermission = section != LibrarySection.DOWNLOADS && !hasMediaPermission
        val localEmpty = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
        // Downloads and Songs carry their name in the header card, so the back row stays bare.
        val heroPage = !needsPermission && !localEmpty && (section == LibrarySection.DOWNLOADS || section == LibrarySection.SONGS)
        SectionBackRow(if (heroPage || section == LibrarySection.DOWNLOADS) null else section.label, onBack)
        when {
            needsPermission -> com.whiplash.music.ui.theme.CollectionEmptyState(
                icon = section.icon,
                title = "Allow music access",
                message = "Whiplash needs access to the audio files on this phone to show your ${section.label.lowercase()}. Downloads work without it.",
                tint = section.tint,
            ) {
                com.whiplash.music.ui.theme.CollectionPillButton(
                    text = if (permissionPermanentlyDenied) "Open settings" else "Allow access",
                    icon = if (permissionPermanentlyDenied) Icons.Filled.Settings else Icons.Filled.LockOpen,
                    onClick = if (permissionPermanentlyDenied) onOpenSettings else onRequestPermission,
                    primary = true,
                )
            }
            // Scanning the phone for music: shaped like what's coming (song rows, or a cover grid).
            section != LibrarySection.DOWNLOADS && isScanning && localEmpty -> when (section) {
                LibrarySection.SONGS -> com.whiplash.music.ui.theme.CollectionPageSkeleton(rows = 7)
                LibrarySection.ARTISTS -> com.whiplash.music.ui.theme.CoverGridSkeleton(round = true)
                else -> com.whiplash.music.ui.theme.CoverGridSkeleton()
            }
            section != LibrarySection.DOWNLOADS && localEmpty -> EmptyLibraryState(onRescan = viewModel::rescan)
            section == LibrarySection.DOWNLOADS -> {
                val rows: List<PlayableItem> = inFlight.values.toList() + downloads
                com.whiplash.music.ui.common.TrackCollectionPage(
                    items = rows,
                    eyebrow = "Offline",
                    title = "Downloads",
                    tint = section.tint,
                    subtitlePrefix = listOfNotNull(
                        if (inFlight.isNotEmpty()) "${inFlight.size} downloading" else null,
                        downloadBytes.takeIf { it > 0L }?.let(::formatStorage),
                    ).joinToString(" · ").ifBlank { null },
                    cover = { m -> com.whiplash.music.ui.theme.GradientIconCover(section.icon, section.tint, m) },
                    onPlayQueue = onPlayQueue,
                    sortKey = "library_downloads",
                    defaultSortLabel = "Recently downloaded",
                    heroActions = {
                        if (downloads.isNotEmpty()) {
                            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.whiplash.music.WhiplashApplication
                            val saving = app?.deviceExporter?.isSaving?.collectAsState()?.value ?: false
                            PlainIconButton(
                                contentDescription = if (saving) "Saving downloads to device" else "Save all downloads to device",
                                onClick = { if (!saving) confirmSaveAll = true },
                                size = 48.dp,
                            ) {
                                if (saving) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = WhiplashColors.textSecondary,
                                    )
                                } else {
                                    Icon(Icons.Filled.SaveAlt, contentDescription = null, tint = WhiplashColors.textSecondary)
                                }
                            }
                        }
                        if (rows.isNotEmpty()) {
                            PlainIconButton(contentDescription = "Clear all downloads", onClick = { confirmClear = true }, size = 48.dp) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = WhiplashColors.textSecondary)
                            }
                        }
                    },
                    emptyContent = {
                        com.whiplash.music.ui.theme.CollectionEmptyState(
                            icon = section.icon,
                            title = "No downloads yet",
                            message = "Download a song, album or playlist from its \u22ee menu to listen with no connection.",
                            tint = section.tint,
                        )
                    },
                )
            }
            section == LibrarySection.SONGS -> com.whiplash.music.ui.common.TrackCollectionPage(
                items = songs,
                eyebrow = "On this phone",
                title = "All songs",
                tint = section.tint,
                cover = { m -> com.whiplash.music.ui.theme.GradientIconCover(section.icon, section.tint, m) },
                onPlayQueue = onPlayQueue,
                sortKey = "library_songs",
                defaultSortLabel = "Library order",
                emptyContent = { EmptyLibraryState(onRescan = viewModel::rescan) },
            )
            section == LibrarySection.ALBUMS -> ModernAlbumGrid(albums, songs, onAlbumClick)
            else -> ModernArtistList(artists, songs, onArtistClick)
        }
    }

    if (confirmSaveAll) {
        val count = downloads.size
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Save to device?",
            message = "${if (count == 1) "1 song" else "$count songs"} will be saved to Download/Whiplash.",
            confirmLabel = "Save",
            destructive = false,
            onConfirm = {
                confirmSaveAll = false
                saveToDevice(downloads.map { it.id })
            },
            onDismiss = { confirmSaveAll = false },
        )
    }

    if (confirmClear) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Clear all downloads?",
            message = "This will cancel any in-progress downloads and permanently delete every downloaded song from this device. This can't be undone.",
            confirmLabel = "Clear all",
            onConfirm = {
                viewModel.clearAllDownloads()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}
