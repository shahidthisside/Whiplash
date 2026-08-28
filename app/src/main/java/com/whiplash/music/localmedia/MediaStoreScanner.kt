package com.whiplash.music.localmedia

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.whiplash.music.data.local.entity.LocalAlbumEntity
import com.whiplash.music.data.local.entity.LocalArtistEntity
import com.whiplash.music.data.local.entity.LocalSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans on-device audio via [MediaStore] (section 24-25).
 *
 * Uses only the modern MediaStore audio collection — no raw filesystem
 * traversal. Song rows carry MediaStore content:// URIs, never file paths,
 * so no files are copied or duplicated (section 25).
 */
class MediaStoreScanner(private val context: Context) {

    /**
     * Queries [MediaStore.Audio.Media] for all music tracks and derives
     * album/artist rollups from the same result set (avoids 3 separate
     * content-provider round trips for what is ultimately the same data).
     */
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<LocalSongEntity>()
        val albums = LinkedHashMap<Long, AlbumAccumulator>()
        val artists = LinkedHashMap<Long, ArtistAccumulator>()

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val genreCol = cursor.columnIndexOrNull(MediaStore.Audio.Media.GENRE)
            val albumArtistCol = cursor.columnIndexOrNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.ALBUM_ARTIST else null
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: continue
                val artist = cursor.getString(artistCol) ?: UNKNOWN_ARTIST
                val artistId = cursor.getLong(artistIdCol)
                val album = cursor.getString(albumCol)
                val albumId = if (cursor.isNull(albumIdCol)) null else cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val track = if (cursor.isNull(trackCol)) null else cursor.getInt(trackCol)
                val year = if (cursor.isNull(yearCol) || cursor.getInt(yearCol) == 0) null else cursor.getInt(yearCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val dateModified = cursor.getLong(dateModifiedCol)
                val mimeType = cursor.getString(mimeTypeCol)
                val genre = genreCol?.let { if (cursor.isNull(it)) null else cursor.getString(it) }
                val albumArtist = albumArtistCol?.let { if (cursor.isNull(it)) null else cursor.getString(it) }

                val uri = ContentUris.withAppendedId(collection, id)

                songs += LocalSongEntity(
                    mediaStoreId = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    durationMs = duration,
                    uri = uri.toString(),
                    trackNumber = track?.let { it % 1000 }, // MediaStore encodes disc*1000+track
                    year = year,
                    genre = genre,
                    albumId = albumId,
                    artistId = artistId,
                    sizeBytes = size,
                    dateAddedEpochSec = dateAdded,
                    dateModifiedEpochSec = dateModified,
                    mimeType = mimeType,
                )

                if (albumId != null) {
                    val acc = albums.getOrPut(albumId) { AlbumAccumulator(title = album ?: UNKNOWN_ALBUM, artist = artist, year = year) }
                    acc.songCount++
                }
                artists.getOrPut(artistId) { ArtistAccumulator(name = artist) }.also { acc ->
                    acc.trackCount++
                    if (albumId != null) acc.albumIds += albumId
                }
            }
        }

        val albumEntities = albums.map { (id, acc) ->
            LocalAlbumEntity(albumId = id, title = acc.title, artist = acc.artist, songCount = acc.songCount, year = acc.year)
        }
        val artistEntities = artists.map { (id, acc) ->
            LocalArtistEntity(artistId = id, name = acc.name, trackCount = acc.trackCount, albumCount = acc.albumIds.size)
        }

        ScanResult(songs = songs, albums = albumEntities, artists = artistEntities)
    }

    private fun Cursor.columnIndexOrNull(column: String?): Int? {
        if (column == null) return null
        val idx = getColumnIndex(column)
        return if (idx >= 0) idx else null
    }

    private class AlbumAccumulator(val title: String, val artist: String, val year: Int?) {
        var songCount = 0
    }

    private class ArtistAccumulator(val name: String) {
        var trackCount = 0
        val albumIds = mutableSetOf<Long>()
    }

    data class ScanResult(
        val songs: List<LocalSongEntity>,
        val albums: List<LocalAlbumEntity>,
        val artists: List<LocalArtistEntity>,
    )

    companion object {
        private const val UNKNOWN_ARTIST = "Unknown Artist"
        private const val UNKNOWN_ALBUM = "Unknown Album"
    }
}
