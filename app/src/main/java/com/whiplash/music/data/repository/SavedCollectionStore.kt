package com.whiplash.music.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remembers which YouTube album or playlist (by URL) was copied into the
 * user's Playlists with "Save to Playlists", so its menu can offer "Remove
 * from Playlists" instead. The playlist's name is stored too: a mapping
 * only counts while a playlist with that id still has that name, so a
 * deleted, renamed or restored-over playlist is never removed by mistake.
 */
class SavedCollectionStore private constructor(context: Context) {
    data class Saved(val playlistId: Long, val name: String)

    private val prefs = context.getSharedPreferences("saved_collections", Context.MODE_PRIVATE)
    private val _saved = MutableStateFlow(load())
    val saved: StateFlow<Map<String, Saved>> = _saved

    private fun load(): Map<String, Saved> = prefs.all.mapNotNull { (url, value) ->
        val raw = value as? String ?: return@mapNotNull null
        val id = raw.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
        url to Saved(id, raw.substringAfter('\t'))
    }.toMap()

    fun put(url: String, playlistId: Long, name: String) {
        prefs.edit().putString(url, "$playlistId\t$name").apply()
        _saved.value = _saved.value + (url to Saved(playlistId, name))
    }

    fun remove(url: String) {
        prefs.edit().remove(url).apply()
        _saved.value = _saved.value - url
    }

    companion object {
        @Volatile private var instance: SavedCollectionStore? = null

        fun get(context: Context): SavedCollectionStore =
            instance ?: synchronized(this) {
                instance ?: SavedCollectionStore(context.applicationContext).also { instance = it }
            }
    }
}
