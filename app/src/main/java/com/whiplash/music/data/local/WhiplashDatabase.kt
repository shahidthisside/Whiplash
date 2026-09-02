package com.whiplash.music.data.local
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.whiplash.music.data.local.dao.AlbumDao
import com.whiplash.music.data.local.dao.ArtistDao
import com.whiplash.music.data.local.dao.DownloadDao
import com.whiplash.music.data.local.dao.FavoriteDao
import com.whiplash.music.data.local.dao.HistoryDao
import com.whiplash.music.data.local.dao.LocalAlbumDao
import com.whiplash.music.data.local.dao.LocalArtistDao
import com.whiplash.music.data.local.dao.LocalSongDao
import com.whiplash.music.data.local.dao.PinnedDao
import com.whiplash.music.data.local.dao.PlaylistDao
import com.whiplash.music.data.local.dao.ProviderHealthDao
import com.whiplash.music.data.local.dao.SearchCacheDao
import com.whiplash.music.data.local.dao.SearchHistoryDao
import com.whiplash.music.data.local.dao.SongDao
import com.whiplash.music.data.local.entity.AlbumEntity
import com.whiplash.music.data.local.entity.ArtistEntity
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.LocalAlbumEntity
import com.whiplash.music.data.local.entity.LocalArtistEntity
import com.whiplash.music.data.local.entity.LocalSongEntity
import com.whiplash.music.data.local.entity.PinnedEntity
import com.whiplash.music.data.local.entity.PlaylistEntity
import com.whiplash.music.data.local.entity.PlaylistTrackEntity
import com.whiplash.music.data.local.entity.ProviderHealthEntity
import com.whiplash.music.data.local.entity.SearchCacheEntity
import com.whiplash.music.data.local.entity.SearchHistoryEntity
import com.whiplash.music.data.local.entity.SongEntity

/**
 * Whiplash's local-first Room database (section 35, section 63).
 *
 * Holds device-local library data, cached online metadata, playlists,
 * favorites, history, search cache, and provider health — never
 * credentials, cookies, tokens, or stream URLs (section 35/36).
 */
@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        SearchCacheEntity::class,
        SearchHistoryEntity::class,
        LocalSongEntity::class,
        LocalAlbumEntity::class,
        LocalArtistEntity::class,
        ProviderHealthEntity::class,
        PinnedEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WhiplashDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun localSongDao(): LocalSongDao
    abstract fun localAlbumDao(): LocalAlbumDao
    abstract fun localArtistDao(): LocalArtistDao
    abstract fun providerHealthDao(): ProviderHealthDao
    abstract fun pinnedDao(): PinnedDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        private const val DATABASE_NAME = "whiplash.db"

        @Volatile
        private var instance: WhiplashDatabase? = null

        fun getInstance(context: Context): WhiplashDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    WhiplashDatabase::class.java,
                    DATABASE_NAME,
                )
                    // This app has no shipped release yet (still in active
                    // development, per CLAUDE.md's phased build process) —
                    // there is no real user data to preserve across this
                    // schema bump, so a destructive fallback is the correct
                    // choice here rather than writing a real Migration for
                    // data that doesn't exist in the wild. This must be
                    // revisited before any real release.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
