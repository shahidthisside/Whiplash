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
 * Real Room migrations for [WhiplashDatabase], v1 through v4 — added as
 * part of a UAT audit finding: [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * was previously used unconditionally, silently wiping every user's
 * playlists/favorites/history/downloads/settings on the next version
 * bump with no warning. That was defensible while the app had no real
 * users yet, but the project's own README now advertises a signed
 * release APK on a public Releases page — real installs exist, so a
 * destructive fallback is a real data-loss risk going forward.
 *
 * Each migration below was derived directly from Room's own exported
 * schema JSON files (app/schemas/.../{1,2,3,4}.json, already present
 * in this repo from `exportSchema = true`), not guessed: diffing every
 * version's `createSql` confirmed each version bump ONLY ever added one
 * new table (v1->v2: pinned_speed_dial, v2->v3: search_history,
 * v3->v4: downloads) — every pre-existing table's own CREATE TABLE SQL
 * is byte-identical across all 4 versions, so no column was ever added,
 * renamed, or removed on an existing table. Each migration's SQL below
 * is the literal `createSql` Room generated for that table at the
 * version it was introduced (with the `${TABLE_NAME}` placeholder
 * resolved to the real name), so this is provably correct against the
 * schema Room itself already recorded, not a hand-written guess.
 */
private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pinned_speed_dial` (`trackId` TEXT NOT NULL, `source` TEXT NOT NULL, `pinnedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`trackId`, `source`))"
        )
    }
}

private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `searchedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`query`))"
        )
    }
}

private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `artworkPath` TEXT, `durationMs` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `downloadedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

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
                    // Real migrations now cover every version bump this
                    // database has ever had (see MIGRATION_1_2/2_3/3_4's
                    // own doc above for how these were derived directly
                    // from Room's exported schema JSON, not guessed).
                    // fallbackToDestructiveMigration() is kept ONLY as a
                    // safety net for a version jump with no matching
                    // Migration object (which would otherwise crash the
                    // app outright on open) — every version this app has
                    // actually shipped is now migrated losslessly, so
                    // this fallback is expected to never actually fire
                    // for a real user's upgrade. It remains a real
                    // data-loss risk if it ever does — the honest fix
                    // going forward is to keep adding a new Migration_
                    // object here every time the schema changes again,
                    // never relying on this fallback for a real release.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
