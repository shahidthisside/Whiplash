package com.whiplash.music.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.MediaSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for a real bug: the advertised "up to 200 recently played
 * tracks" cap was only ever a read-side `LIMIT 200` in the History screen's
 * query. [com.whiplash.music.data.local.dao.HistoryDao.pruneOlderThan] existed
 * but was called from nowhere in the entire app (verified by grep), so the
 * `history` table itself grew by one row on every single play, forever — an
 * unbounded, progressively slower table behind a cap that was purely a display
 * illusion.
 *
 * These tests exercise the real SQL against a real Room database, so they
 * would fail if the trim query were removed, mis-ordered, or off by one.
 */
@RunWith(AndroidJUnit4::class)
class HistoryTrimInstrumentedTest {

    private lateinit var database: WhiplashDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WhiplashDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertPlays(count: Int, startAtEpochMs: Long = 1_000L) {
        repeat(count) { index ->
            database.historyDao().insert(
                HistoryEntity(
                    trackId = "track$index",
                    source = MediaSource.YOUTUBE,
                    playedAtEpochMs = startAtEpochMs + index,
                ),
            )
        }
    }

    @Test
    fun trimToMostRecent_capsTheTableAtExactlyTheKeepCount() = runTest {
        insertPlays(250)
        assertEquals(250, database.historyDao().observeRecentlyPlayed(1000).first().size)

        database.historyDao().trimToMostRecent(200)

        val remaining = database.historyDao().observeRecentlyPlayed(1000).first()
        assertEquals(200, remaining.size)
    }

    @Test
    fun trimToMostRecent_keepsTheNewestAndDropsTheOldest() = runTest {
        insertPlays(250)

        database.historyDao().trimToMostRecent(200)

        val remaining = database.historyDao().observeRecentlyPlayed(1000).first()
        val keptIds = remaining.map { it.trackId }.toSet()
        // track249 is the most recent play, track0 the oldest.
        assertTrue("newest play must survive the trim", "track249" in keptIds)
        assertTrue("oldest play must be trimmed away", "track0" !in keptIds)
        // Exactly the newest 200 (track50..track249) should remain.
        assertTrue("track50 is the 200th-newest and must survive", "track50" in keptIds)
        assertTrue("track49 falls outside the cap and must be gone", "track49" !in keptIds)
    }

    @Test
    fun trimToMostRecent_isANoOpWhenUnderTheCap() = runTest {
        insertPlays(10)

        database.historyDao().trimToMostRecent(200)

        assertEquals(10, database.historyDao().observeRecentlyPlayed(1000).first().size)
    }

    @Test
    fun trimToMostRecent_isIdempotent() = runTest {
        insertPlays(250)

        database.historyDao().trimToMostRecent(200)
        database.historyDao().trimToMostRecent(200)
        database.historyDao().trimToMostRecent(200)

        assertEquals(200, database.historyDao().observeRecentlyPlayed(1000).first().size)
    }
}
