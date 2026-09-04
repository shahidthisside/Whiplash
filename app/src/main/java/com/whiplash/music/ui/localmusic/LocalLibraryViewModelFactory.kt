package com.whiplash.music.ui.localmusic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LocalLibraryRepository
import com.whiplash.music.localmedia.MediaStoreScanner

/**
 * Minimal manual factory (no DI framework introduced yet — kept simple
 * until enough cross-cutting dependencies exist to justify one).
 */
class LocalLibraryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val appContext = context.applicationContext
        val database = com.whiplash.music.data.local.WhiplashDatabase.getInstance(appContext)
        val repository = LocalLibraryRepository(
            scanner = MediaStoreScanner(appContext),
            localSongDao = database.localSongDao(),
            localAlbumDao = database.localAlbumDao(),
            localArtistDao = database.localArtistDao(),
            database = database,
        )
        val app = appContext as? com.whiplash.music.WhiplashApplication
        @Suppress("UNCHECKED_CAST")
        return LocalLibraryViewModel(repository, appContext, app?.libraryRepository, app?.downloadManager) as T
    }
}
