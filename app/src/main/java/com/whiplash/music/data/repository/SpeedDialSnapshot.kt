package com.whiplash.music.data.repository

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.QueueSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last Speed dial, kept on disk so Home shows it the moment it opens,
 * the way Quick Picks does, instead of a placeholder while the database
 * starts up. The live list from history replaces it as soon as it's read,
 * so a pin, unpin or new play is never hidden by it.
 */
class SpeedDialSnapshot(private val file: File) {

    // Read once at app start (see warm), so Home's first frame already has it.
    @Volatile private var warmed: List<PlayableItem>? = null
    @Volatile private var warmedListView: Boolean? = null

    /**
     * Starts reading the saved dial in the background, and loading its
     * covers into the image cache, so Home's first frame shows the real
     * tiles. [peek] has the list once read.
     */
    fun warm(scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context? = null) {
        scope.launch(Dispatchers.IO) {
            if (warmed != null) return@launch
            val items = read()
            warmedListView = readListView()
            warmed = items
            if (context != null) {
                val loader = coil.Coil.imageLoader(context)
                items.take(WARM_ARTWORK).mapNotNull { it.artworkUri }.forEach { url ->
                    loader.enqueue(coil.request.ImageRequest.Builder(context).data(url).build())
                }
            }
        }
    }

    /** The saved dial if [warm] has finished reading it, else null. */
    fun peek(): List<PlayableItem>? = warmed

    /** Whether the dial was last shown as a list (null if unknown), so the first frame uses the same layout. */
    fun peekListView(): Boolean? = warmedListView

    /** Remembers the layout next to the list. */
    suspend fun writeListView(list: Boolean) = withContext(Dispatchers.IO) {
        warmedListView = list
        runCatching { layoutFile().writeText(if (list) "list" else "grid") }
    }

    private fun readListView(): Boolean? = runCatching {
        when (layoutFile().takeIf { it.exists() }?.readText()?.trim()) {
            "list" -> true
            "grid" -> false
            else -> null
        }
    }.getOrNull()

    private fun layoutFile() = File(file.parentFile, "${file.nameWithoutExtension}_layout.txt")

    suspend fun read(): List<PlayableItem> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { QueueSnapshot.decodeItems(file.readText()) }
            .onFailure { Log.w(TAG, "Couldn't read saved Speed dial", it) }
            .getOrDefault(emptyList())
    }

    suspend fun write(items: List<PlayableItem>) = withContext(Dispatchers.IO) {
        runCatching {
            // Temp file first, so a crash mid-write never leaves half a list.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(QueueSnapshot.encodeItems(items))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "Couldn't save Speed dial", it) }
    }

    /** After history is cleared, the old dial shouldn't flash back. */
    fun clear() {
        warmed = emptyList()
        runCatching { file.delete() }
    }

    private companion object {
        const val TAG = "SpeedDialSnapshot"
        const val WARM_ARTWORK = 12
    }
}
